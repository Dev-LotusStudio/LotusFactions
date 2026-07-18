package org.degree.factions.http;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.utils.ConfigManager;
import org.degree.factions.utils.FactionCache;
import org.degree.factions.utils.FactionUtils;
import org.degree.factions.utils.OnlinePlayerCache;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class FactionApiClient implements AutoCloseable {
    private static final int JSON_SEGMENT_BYTES = 64 * 1024;
    private static final int MAX_ERROR_BODY_BYTES = 2_000;
    private static final int MAX_PARTIAL_FACTIONS = 500;
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final long RETRY_BASE_DELAY_MS = 500L;
    private static final long MAX_RETRY_AFTER_MS = 60_000L;

    private final Factions plugin;
    private final ConfigManager config;
    private final FactionUtils factionUtils;
    private final FactionSnapshotLoader snapshotLoader;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService ingestExecutor;
    private final AtomicReference<IngestRequest> pendingRequest = new AtomicReference<>();
    private final AtomicReference<HttpURLConnection> activeHttpConnection = new AtomicReference<>();
    private final AtomicBoolean workerScheduled = new AtomicBoolean();
    private final AtomicBoolean acceptingRequests = new AtomicBoolean(true);
    private final AtomicLong retryNotBeforeNanos = new AtomicLong();
    private final AtomicLong retryDelayHintMs = new AtomicLong();

    private final String ingestUrl;
    private final boolean ingestEnabled;
    private final String serverHeader;
    private final String apiKey;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int chartsDays;
    private final Map<String, Object> pluginMeta;

    public FactionApiClient(Factions plugin, ConfigManager config, FactionDatabase factionDatabase, FactionUtils factionUtils) {
        this.plugin = plugin;
        this.config = config;
        this.factionUtils = factionUtils;
        this.snapshotLoader = new FactionSnapshotLoader(factionDatabase);
        this.ingestEnabled = config.isIngestEnabled();
        this.ingestUrl = joinUrl(config.getIngestBaseUrl(), config.getIngestEndpointPath());
        this.serverHeader = config.getIngestServerHeader();
        this.apiKey = config.getIngestApiKey();
        this.connectTimeoutMs = Math.max(0, config.getIngestConnectTimeoutMs());
        this.readTimeoutMs = Math.max(0, config.getIngestReadTimeoutMs());
        this.chartsDays = Math.max(1, config.getIngestChartsDays());
        this.pluginMeta = buildPluginMeta(detectMcVersion());

        String threadName = plugin.getName() + "-Ingest";
        this.ingestExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    public void postFactionFromDatabase(String factionName) {
        if (!canAcceptRequest()) {
            return;
        }
        Instant capturedAt = Instant.now();
        postIngestSnapshotAsync(capturedAt, List.of(factionName), "partial");
    }

    public void postAllFactionsFromDatabase() {
        if (!canAcceptRequest()) {
            return;
        }
        Instant capturedAt = Instant.now();
        postIngestSnapshotAsync(capturedAt, null, "full");
    }

    /**
     * Runs the periodic enqueue on the ingest executor itself. In particular,
     * Folia's global tick never has to copy the online-player state.
     */
    public void startPeriodicFullSnapshots(long initialDelaySeconds, long intervalSeconds) {
        long safeInitialDelay = Math.max(0L, initialDelaySeconds);
        long safeInterval = Math.max(1L, intervalSeconds);
        try {
            ingestExecutor.scheduleWithFixedDelay(
                    this::postAllFactionsFromDatabase,
                    safeInitialDelay,
                    safeInterval,
                    TimeUnit.SECONDS
            );
        } catch (RejectedExecutionException ignored) {
            // Plugin is already stopping.
        }
    }

    /**
     * Adds a snapshot request to a single-flight queue. A queued full export
     * supersedes partial exports; queued partial exports are merged by faction.
     */
    public void postIngestSnapshotAsync(
            Instant capturedAt,
            Collection<String> factionNamesOrNull,
            String syncMode
    ) {
        if (!canAcceptRequest()) {
            return;
        }

        IngestRequest request = new IngestRequest(
                capturedAt,
                factionNamesOrNull,
                System.nanoTime()
        );
        pendingRequest.accumulateAndGet(request, (queued, incoming) ->
                queued == null ? incoming : queued.merge(incoming));
        scheduleWorker(0L);
    }

    private boolean canAcceptRequest() {
        return acceptingRequests.get() && ingestEnabled;
    }

    private void scheduleWorker(long delayMs) {
        if (!acceptingRequests.get() || !workerScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            long remainingNanos = Math.max(0L, retryNotBeforeNanos.get() - System.nanoTime());
            long guardedDelayMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos + 999_999L);
            long actualDelayMs = Math.max(Math.max(0L, delayMs), guardedDelayMs);
            ingestExecutor.schedule(this::drainQueue, actualDelayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            workerScheduled.set(false);
        }
    }

    private void drainQueue() {
        long rescheduleDelayMs = 0L;
        try {
            IngestRequest request;
            while (acceptingRequests.get() && (request = pendingRequest.getAndSet(null)) != null) {
                SendOutcome outcome = sendSnapshot(request);
                if (outcome == SendOutcome.RETRYABLE_FAILURE && acceptingRequests.get()) {
                    if (request.retryAttempt < MAX_RETRY_ATTEMPTS) {
                        IngestRequest retry = request.nextAttempt();
                        requeueFailedRequest(retry);
                        long exponentialDelayMs = RETRY_BASE_DELAY_MS << request.retryAttempt;
                        rescheduleDelayMs = Math.max(exponentialDelayMs, retryDelayHintMs.getAndSet(0L));
                        long retryDeadline = System.nanoTime()
                                + TimeUnit.MILLISECONDS.toNanos(rescheduleDelayMs);
                        retryNotBeforeNanos.accumulateAndGet(retryDeadline, Math::max);
                        break;
                    }
                    plugin.getLogger().warning(
                            "[FactionApiClient] Giving up ingest snapshot after "
                                    + (request.retryAttempt + 1) + " attempts"
                    );
                    retryNotBeforeNanos.set(0L);
                } else {
                    retryNotBeforeNanos.set(0L);
                }
            }
        } finally {
            workerScheduled.set(false);
            if (acceptingRequests.get() && pendingRequest.get() != null) {
                scheduleWorker(rescheduleDelayMs);
            }
        }
    }

    private void requeueFailedRequest(IngestRequest failedRequest) {
        pendingRequest.accumulateAndGet(failedRequest, (queued, failed) ->
                queued == null ? failed : failed.merge(queued));
    }

    private SendOutcome sendSnapshot(IngestRequest request) {
        long startedNanos = System.nanoTime();
        retryDelayHintMs.set(0L);
        try {
            OnlineState onlineState = captureOnlineState();
            long captureFinishedNanos = System.nanoTime();
            FactionSnapshotLoader.LoadResult loadResult = snapshotLoader.load(
                    request.capturedAt,
                    request.factionNames,
                    chartsDays
            );
            List<FactionSnapshotLoader.Snapshot> snapshots = loadResult.snapshots;
            long databaseFinishedNanos = System.nanoTime();
            if (isCancelled()) {
                return SendOutcome.CANCELLED;
            }

            Map<String, Object> body = buildRequestBody(request, snapshots, onlineState);
            long payloadFinishedNanos = System.nanoTime();
            if (isCancelled()) {
                return SendOutcome.CANCELLED;
            }

            HttpResult result = postJson(body);
            long finishedNanos = System.nanoTime();

            long queuedMs = elapsedMs(request.queuedAtNanos, startedNanos);
            long captureMs = elapsedMs(startedNanos, captureFinishedNanos);
            long databaseMs = elapsedMs(captureFinishedNanos, databaseFinishedNanos);
            long payloadMs = elapsedMs(databaseFinishedNanos, payloadFinishedNanos);
            long postMs = elapsedMs(payloadFinishedNanos, finishedNanos);
            long jsonMs = TimeUnit.NANOSECONDS.toMillis(result.jsonEncodingNanos);
            long httpMs = Math.max(0L, postMs - jsonMs);

            if (result.statusCode >= 200 && result.statusCode < 300) {
                plugin.getLogger().info(
                        "[FactionApiClient] Ingest snapshot sent (" + result.statusCode
                                + ", factions=" + snapshots.size()
                                + ", bytes=" + result.requestBytes
                                + ", queue=" + queuedMs + "ms"
                                + ", capture=" + captureMs + "ms"
                                + ", db=" + databaseMs + "ms"
                                + ", dbParts=" + loadResult.timings.formatMillis()
                                + ", build=" + payloadMs + "ms"
                                + ", json=" + jsonMs + "ms"
                                + ", http=" + httpMs + "ms)"
                );
                return SendOutcome.COMPLETED;
            } else if (result.responseBody.isBlank()) {
                plugin.getLogger().warning("[FactionApiClient] Ingest failed: HTTP " + result.statusCode);
            } else {
                plugin.getLogger().warning(
                        "[FactionApiClient] Ingest failed: HTTP " + result.statusCode + " body=" + result.responseBody
                );
            }
            if (isRetryableStatus(result.statusCode)) {
                retryDelayHintMs.set(result.retryAfterMs);
            }
            return isRetryableStatus(result.statusCode)
                    ? SendOutcome.RETRYABLE_FAILURE
                    : SendOutcome.COMPLETED;
        } catch (SQLException e) {
            if (!isCancelled()) {
                plugin.getLogger().warning("[FactionApiClient] SQL error while building ingest snapshot: " + e.getMessage());
                return SendOutcome.RETRYABLE_FAILURE;
            }
            return SendOutcome.CANCELLED;
        } catch (Exception e) {
            if (!isCancelled()) {
                plugin.getLogger().warning("[FactionApiClient] Failed to send ingest snapshot: " + e.getMessage());
                return SendOutcome.RETRYABLE_FAILURE;
            }
            return SendOutcome.CANCELLED;
        }
    }

    private boolean isCancelled() {
        return !acceptingRequests.get() || Thread.currentThread().isInterrupted();
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }

    private Map<String, Object> buildRequestBody(
            IngestRequest request,
            List<FactionSnapshotLoader.Snapshot> snapshots,
            OnlineState onlineState
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("captured_at", request.capturedAt.toString());
        body.put("sync_mode", request.isFull() ? "full" : "partial");

        List<Map<String, Object>> factions = new ArrayList<>(snapshots.size());
        for (FactionSnapshotLoader.Snapshot snapshot : snapshots) {
            factions.add(buildFactionEntry(snapshot, onlineState));
        }
        body.put("factions", factions);
        return body;
    }

    private Map<String, Object> buildFactionEntry(
            FactionSnapshotLoader.Snapshot snapshot,
            OnlineState onlineState
    ) {
        int onlineCount = onlineState.countsByFaction.getOrDefault(snapshot.name, 0);
        Map<String, String> onlineNamesByUuid = onlineState.onlineNamesByUuidByFaction
                .getOrDefault(snapshot.name, Map.of());

        List<Map<String, Object>> players = new ArrayList<>(snapshot.members.size());
        Map<String, Long> byPlayerSeconds = new LinkedHashMap<>();
        Map<String, Object> mostActive = null;
        long bestSeconds = -1L;
        long totalSeconds = 0L;

        for (FactionSnapshotLoader.Member member : snapshot.members) {
            if (member.uuid == null) {
                continue;
            }

            long seconds = snapshot.playtimeSecondsByPlayer.getOrDefault(member.uuid, 0L);
            long safeSeconds = Math.max(0L, seconds);
            byPlayerSeconds.put(member.uuid, safeSeconds);
            totalSeconds += safeSeconds;

            Map<String, Object> player = new LinkedHashMap<>();
            player.put("uuid", member.uuid);
            player.put("name", member.name != null ? member.name : onlineNamesByUuid.get(member.uuid));
            player.put("online", onlineNamesByUuid.containsKey(member.uuid));
            player.put("playtimeSeconds", safeSeconds);
            players.add(player);

            if (safeSeconds > bestSeconds) {
                bestSeconds = safeSeconds;
                mostActive = new LinkedHashMap<>();
                mostActive.put("uuid", member.uuid);
                mostActive.put("name", member.name);
                mostActive.put("playtimeSeconds", safeSeconds);
            }
        }

        Map<String, Object> members = new LinkedHashMap<>();
        members.put("total", snapshot.members.size());
        members.put("online", onlineCount);
        members.put("leader", buildLeader(snapshot.faction));
        members.put("mostActive", mostActive);

        Map<String, Object> playtime = new LinkedHashMap<>();
        playtime.put("totalSeconds", totalSeconds);
        playtime.put("byPlayerSeconds", byPlayerSeconds);

        Map<String, Object> onlineCharts = new LinkedHashMap<>();
        onlineCharts.put("byDay", snapshot.onlineByDay);
        onlineCharts.put("byHour", snapshot.onlineByHour);

        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("brokenTotal", snapshot.brokenTotal);
        blocks.put("placedTotal", snapshot.placedTotal);
        blocks.put("brokenByType", namespaceMaterialKeys(snapshot.brokenByType));
        blocks.put("placedByType", namespaceMaterialKeys(snapshot.placedByType));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plugin", pluginMeta);
        payload.put("members", members);
        payload.put("players", players);
        payload.put("playtime", playtime);
        payload.put("onlineCharts", onlineCharts);
        payload.put("blocks", blocks);

        FactionSnapshotLoader.FactionInfo faction = snapshot.faction;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("source_id", faction != null ? String.valueOf(faction.id) : "");
        entry.put("slug", factionUtils.toSlug(snapshot.name));
        entry.put("name", snapshot.name);
        entry.put("hex", faction != null ? faction.colorHex : null);
        entry.put("discord_role_sync_enabled", faction != null && faction.discordRoleSyncEnabled);
        entry.put("payload", payload);
        return entry;
    }

    private Map<String, Object> buildLeader(FactionSnapshotLoader.FactionInfo faction) {
        if (faction == null) {
            return null;
        }
        Map<String, Object> leader = new LinkedHashMap<>();
        leader.put("uuid", faction.leaderUuid);
        leader.put("name", faction.leaderName);
        return leader;
    }

    private Map<String, Object> buildPluginMeta(String mcVersion) {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", config.getIngestPayloadServerName());
        server.put("platform", config.getIngestPayloadPlatform());
        server.put("mcVersion", mcVersion);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", config.getIngestPayloadPluginName());
        meta.put("version", plugin.getDescription().getVersion());
        meta.put("server", server);
        return meta;
    }

    private OnlineState captureOnlineState() {
        Map<String, Integer> countsByFaction = new HashMap<>();
        Map<String, Map<String, String>> onlineNamesByUuidByFaction = new HashMap<>();
        for (Map.Entry<String, String> player : OnlinePlayerCache.snapshot().entrySet()) {
            String uuid = player.getKey();
            String faction = FactionCache.getFaction(uuid);
            if (faction == null) {
                continue;
            }
            countsByFaction.merge(faction, 1, Integer::sum);
            onlineNamesByUuidByFaction
                    .computeIfAbsent(faction, ignored -> new HashMap<>())
                    .put(uuid, player.getValue());
        }
        return new OnlineState(countsByFaction, onlineNamesByUuidByFaction);
    }

    private Map<String, Long> namespaceMaterialKeys(Map<String, Long> byMaterialName) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : byMaterialName.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String namespaced = key.contains(":")
                    ? key.toLowerCase(Locale.ROOT)
                    : "minecraft:" + key.toLowerCase(Locale.ROOT);
            result.put(namespaced, entry.getValue());
        }
        return result;
    }

    private HttpResult postJson(Map<String, Object> body) throws IOException {
        long encodingStartedNanos = System.nanoTime();
        SegmentedOutputStream requestBody = new SegmentedOutputStream(JSON_SEGMENT_BYTES);
        try (Writer writer = new BufferedWriter(
                new OutputStreamWriter(requestBody, StandardCharsets.UTF_8),
                JSON_SEGMENT_BYTES
        )) {
            gson.toJson(body, writer);
        }
        long encodingNanos = System.nanoTime() - encodingStartedNanos;
        if (isCancelled()) {
            throw new InterruptedIOException("Ingest was cancelled");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(ingestUrl).openConnection();
        activeHttpConnection.set(connection);
        try {
            if (isCancelled()) {
                throw new InterruptedIOException("Ingest was cancelled");
            }
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setFixedLengthStreamingMode(requestBody.size());
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("X-Server", serverHeader);
            connection.setRequestProperty("X-API-Key", apiKey);

            try (OutputStream output = connection.getOutputStream()) {
                requestBody.writeTo(output);
            }

            int statusCode = connection.getResponseCode();
            long retryAfterMs = parseRetryAfterMillis(connection.getHeaderField("Retry-After"));
            String responseBody = statusCode >= 200 && statusCode < 300
                    ? ""
                    : readResponseBody(connection, MAX_ERROR_BODY_BYTES);
            return new HttpResult(statusCode, requestBody.size(), responseBody, encodingNanos, retryAfterMs);
        } finally {
            activeHttpConnection.compareAndSet(connection, null);
            connection.disconnect();
        }
    }

    private static String readResponseBody(HttpURLConnection connection, int maxBytes) {
        InputStream stream = connection.getErrorStream();
        try {
            if (stream == null) {
                stream = connection.getInputStream();
            }
            if (stream == null) {
                return "";
            }

            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream(maxBytes)) {
                byte[] buffer = new byte[512];
                int remaining = maxBytes;
                while (remaining > 0) {
                    int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read < 0) {
                        break;
                    }
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
                return output.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignoredClose) {
                    // Nothing else to do while handling a failed response.
                }
            }
            return "";
        }
    }

    private static String joinUrl(String base, String path) {
        if (base == null) {
            base = "";
        }
        if (path == null) {
            path = "";
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }

    private static String detectMcVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion == null) {
            return "unknown";
        }
        int separator = bukkitVersion.indexOf('-');
        return separator <= 0 ? bukkitVersion : bukkitVersion.substring(0, separator);
    }

    private static long elapsedMs(long startNanos, long endNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, endNanos - startNanos));
    }

    private static long parseRetryAfterMillis(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds <= 0L) {
                return 0L;
            }
            if (seconds >= MAX_RETRY_AFTER_MS / 1_000L) {
                return MAX_RETRY_AFTER_MS;
            }
            return seconds * 1_000L;
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                long delayMs = retryAt.toEpochMilli() - System.currentTimeMillis();
                return Math.min(MAX_RETRY_AFTER_MS, Math.max(0L, delayMs));
            } catch (DateTimeParseException ignoredDate) {
                return 0L;
            }
        }
    }

    @Override
    public void close() {
        if (!acceptingRequests.compareAndSet(true, false)) {
            return;
        }

        pendingRequest.set(null);
        HttpURLConnection connection = activeHttpConnection.getAndSet(null);
        if (connection != null) {
            connection.disconnect();
        }
        ingestExecutor.shutdownNow();
        try {
            if (!ingestExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[FactionApiClient] Ingest worker did not stop within 2 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class IngestRequest {
        private final Instant capturedAt;
        private final List<String> factionNames;
        private final long queuedAtNanos;
        private final int retryAttempt;

        private IngestRequest(
                Instant capturedAt,
                Collection<String> factionNames,
                long queuedAtNanos
        ) {
            this(capturedAt, factionNames, queuedAtNanos, 0);
        }

        private IngestRequest(
                Instant capturedAt,
                Collection<String> factionNames,
                long queuedAtNanos,
                int retryAttempt
        ) {
            this.capturedAt = capturedAt;
            List<String> normalizedNames = factionNames == null ? null : normalizeNames(factionNames);
            this.factionNames = normalizedNames != null && normalizedNames.size() > MAX_PARTIAL_FACTIONS
                    ? null
                    : normalizedNames;
            this.queuedAtNanos = queuedAtNanos;
            this.retryAttempt = retryAttempt;
        }

        private boolean isFull() {
            return factionNames == null;
        }

        private IngestRequest merge(IngestRequest newer) {
            List<String> mergedNames = null;
            if (!isFull() && !newer.isFull()) {
                Set<String> names = new LinkedHashSet<>(factionNames);
                names.addAll(newer.factionNames);
                mergedNames = new ArrayList<>(names);
            }
            return new IngestRequest(
                    newer.capturedAt,
                    mergedNames,
                    Math.min(queuedAtNanos, newer.queuedAtNanos),
                    newer.retryAttempt == 0 ? 0 : Math.max(retryAttempt, newer.retryAttempt)
            );
        }

        private IngestRequest nextAttempt() {
            return new IngestRequest(
                    capturedAt,
                    factionNames,
                    queuedAtNanos,
                    retryAttempt + 1
            );
        }

        private static List<String> normalizeNames(Collection<String> names) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    normalized.add(name);
                }
            }
            return new ArrayList<>(normalized);
        }
    }

    private enum SendOutcome {
        COMPLETED,
        RETRYABLE_FAILURE,
        CANCELLED
    }

    private static final class HttpResult {
        private final int statusCode;
        private final long requestBytes;
        private final String responseBody;
        private final long jsonEncodingNanos;
        private final long retryAfterMs;

        private HttpResult(
                int statusCode,
                long requestBytes,
                String responseBody,
                long jsonEncodingNanos,
                long retryAfterMs
        ) {
            this.statusCode = statusCode;
            this.requestBytes = requestBytes;
            this.responseBody = responseBody;
            this.jsonEncodingNanos = jsonEncodingNanos;
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static final class SegmentedOutputStream extends OutputStream {
        private final List<byte[]> segments = new ArrayList<>();
        private final int segmentSize;
        private byte[] currentSegment;
        private int currentOffset;
        private long size;

        private SegmentedOutputStream(int segmentSize) {
            this.segmentSize = segmentSize;
        }

        @Override
        public void write(int value) {
            ensureCapacity();
            currentSegment[currentOffset++] = (byte) value;
            size++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            if (offset < 0 || length < 0 || offset + length > buffer.length) {
                throw new IndexOutOfBoundsException();
            }
            int remaining = length;
            int sourceOffset = offset;
            while (remaining > 0) {
                ensureCapacity();
                int copyLength = Math.min(remaining, currentSegment.length - currentOffset);
                System.arraycopy(buffer, sourceOffset, currentSegment, currentOffset, copyLength);
                currentOffset += copyLength;
                sourceOffset += copyLength;
                remaining -= copyLength;
                size += copyLength;
            }
        }

        private void ensureCapacity() {
            if (currentSegment != null && currentOffset < currentSegment.length) {
                return;
            }
            currentSegment = new byte[segmentSize];
            currentOffset = 0;
            segments.add(currentSegment);
        }

        private long size() {
            return size;
        }

        private void writeTo(OutputStream output) throws IOException {
            long remaining = size;
            for (byte[] segment : segments) {
                int length = (int) Math.min(segment.length, remaining);
                output.write(segment, 0, length);
                remaining -= length;
                if (remaining == 0) {
                    return;
                }
            }
        }
    }

    private static final class OnlineState {
        private final Map<String, Integer> countsByFaction;
        private final Map<String, Map<String, String>> onlineNamesByUuidByFaction;

        private OnlineState(
                Map<String, Integer> countsByFaction,
                Map<String, Map<String, String>> onlineNamesByUuidByFaction
        ) {
            this.countsByFaction = countsByFaction;
            this.onlineNamesByUuidByFaction = onlineNamesByUuidByFaction;
        }
    }
}
