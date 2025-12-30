package org.degree.factions.http;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.models.Faction;
import org.degree.factions.utils.ConfigManager;
import org.degree.factions.utils.FactionCache;
import org.degree.factions.utils.FactionUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FactionApiClient {
    private final Factions plugin;
    private final ConfigManager config;
    private final FactionDatabase factionDatabase;
    private final FactionUtils factionUtils;
    private final Gson gson = new Gson();

    private final String mcVersion;

    public FactionApiClient(Factions plugin, ConfigManager config, FactionDatabase factionDatabase, FactionUtils factionUtils) {
        this.plugin = plugin;
        this.config = config;
        this.factionDatabase = factionDatabase;
        this.factionUtils = factionUtils;
        this.mcVersion = detectMcVersion();
    }

    public void postFactionFromDatabase(String factionName) {
        Instant capturedAt = Instant.now();
        OnlineState onlineState = captureOnlineState();
        postIngestSnapshotAsync(capturedAt, List.of(factionName), onlineState);
    }

    public void postAllFactionsFromDatabase() {
        Instant capturedAt = Instant.now();
        OnlineState onlineState = captureOnlineState();
        postIngestSnapshotAsync(capturedAt, null, onlineState);
    }

    public void postIngestSnapshotAsync(Instant capturedAt, Collection<String> factionNamesOrNull, OnlineState onlineState) {
        if (!config.isIngestEnabled()) return;

        OnlineState snapshot = onlineState.copy();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<String> factionNames = factionNamesOrNull == null
                        ? factionDatabase.getAllFactionNames()
                        : new ArrayList<>(factionNamesOrNull);

                Map<String, Object> json = new LinkedHashMap<>();
                json.put("captured_at", capturedAt.toString());

                List<Map<String, Object>> factions = new ArrayList<>();
                for (String factionName : factionNames) {
                    if (factionName == null || factionName.isBlank()) continue;
                    int onlineCount = snapshot.countsByFaction.getOrDefault(factionName, 0);
                    Map<String, String> onlineNamesByUuid = snapshot.onlineNamesByUuidByFaction.getOrDefault(factionName, Map.of());
                    factions.add(buildFactionEntry(capturedAt, factionName, onlineCount, onlineNamesByUuid));
                }
                json.put("factions", factions);

                postJson(json);
            } catch (SQLException e) {
                plugin.getLogger().warning("[FactionApiClient] SQL Error: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().warning("[FactionApiClient] Failed to send ingest snapshot: " + e.getMessage());
            }
        });
    }

    private OnlineState captureOnlineState() {
        Map<String, Integer> countsByFaction = new HashMap<>();
        Map<String, Map<String, String>> onlineNamesByUuidByFaction = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String faction = FactionCache.getFaction(player.getUniqueId().toString());
            if (faction == null) continue;
            String uuid = player.getUniqueId().toString();
            countsByFaction.merge(faction, 1, Integer::sum);
            onlineNamesByUuidByFaction.computeIfAbsent(faction, k -> new HashMap<>()).put(uuid, player.getName());
        }
        return new OnlineState(countsByFaction, onlineNamesByUuidByFaction);
    }

    private Map<String, Object> buildFactionEntry(Instant capturedAt, String factionName, int onlineCount, Map<String, String> onlineNamesByUuid) throws SQLException {
        String slug = factionUtils.toSlug(factionName);

        Faction faction = factionDatabase.loadFaction(factionName);
        List<Map<String, String>> membersList = factionDatabase.getMemberNameUuidPairsOfFaction(factionName);

        long nowMs = capturedAt.toEpochMilli();
        Map<String, Long> playtimeSecondsByPlayer = factionDatabase.getPlaytimeSecondsByPlayerInFaction(factionName, nowMs);

        Map<String, Object> members = new LinkedHashMap<>();
        members.put("total", membersList.size());
        members.put("online", onlineCount);
        members.put("leader", buildLeader(faction));
        members.put("mostActive", buildMostActive(membersList, playtimeSecondsByPlayer));

        List<Map<String, Object>> players = new ArrayList<>();
        for (Map<String, String> member : membersList) {
            String uuid = member.get("uuid");
            if (uuid == null) continue;
            String name = member.get("name");
            boolean online = onlineNamesByUuid.containsKey(uuid);
            long playtimeSeconds = playtimeSecondsByPlayer.getOrDefault(uuid, 0L);

            Map<String, Object> playerJson = new LinkedHashMap<>();
            playerJson.put("uuid", uuid);
            playerJson.put("name", name != null ? name : onlineNamesByUuid.get(uuid));
            playerJson.put("online", online);
            playerJson.put("playtimeSeconds", Math.max(0L, playtimeSeconds));
            players.add(playerJson);
        }

        Map<String, Object> playtime = new LinkedHashMap<>();
        long totalSeconds = 0L;
        Map<String, Long> byPlayerSeconds = new LinkedHashMap<>();
        for (Map<String, String> member : membersList) {
            String uuid = member.get("uuid");
            long seconds = uuid == null ? 0L : playtimeSecondsByPlayer.getOrDefault(uuid, 0L);
            if (uuid != null) {
                byPlayerSeconds.put(uuid, seconds);
            }
            totalSeconds += seconds;
        }
        playtime.put("totalSeconds", totalSeconds);
        playtime.put("byPlayerSeconds", byPlayerSeconds);

        Map<String, Object> onlineCharts = new LinkedHashMap<>();
        int daysBack = Math.max(1, config.getIngestChartsDays());
        onlineCharts.put("byDay", factionDatabase.getOnlineChartByDay(factionName, daysBack));
        onlineCharts.put("byHour", factionDatabase.getOnlineChartByHour(factionName, daysBack));

        Map<String, Long> blockTotals = factionDatabase.getBlockTotalsForFaction(factionName);
        Map<String, Long> brokenByType = namespaceMaterialKeys(factionDatabase.getBrokenByTypeForFaction(factionName));
        Map<String, Long> placedByType = namespaceMaterialKeys(factionDatabase.getPlacedByTypeForFaction(factionName));

        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("brokenTotal", blockTotals.getOrDefault("brokenTotal", 0L));
        blocks.put("placedTotal", blockTotals.getOrDefault("placedTotal", 0L));
        blocks.put("brokenByType", brokenByType);
        blocks.put("placedByType", placedByType);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plugin", buildPluginMeta());
        payload.put("members", members);
        payload.put("players", players);
        payload.put("playtime", playtime);
        payload.put("onlineCharts", onlineCharts);
        payload.put("blocks", blocks);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("slug", slug);
        entry.put("name", factionName);
        entry.put("payload", payload);
        return entry;
    }

    private Map<String, Object> buildPluginMeta() {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", config.getIngestPayloadServerName());
        server.put("platform", config.getIngestPayloadPlatform());
        server.put("mcVersion", mcVersion);

        Map<String, Object> pluginMeta = new LinkedHashMap<>();
        pluginMeta.put("name", config.getIngestPayloadPluginName());
        pluginMeta.put("version", plugin.getDescription().getVersion());
        pluginMeta.put("server", server);
        return pluginMeta;
    }

    private Map<String, Object> buildLeader(Faction faction) {
        if (faction == null) return null;
        Map<String, Object> leader = new LinkedHashMap<>();
        leader.put("uuid", faction.getLeaderUuid());
        leader.put("name", faction.getLeaderName());
        return leader;
    }

    private Map<String, Object> buildMostActive(List<Map<String, String>> membersList, Map<String, Long> playtimeSecondsByPlayer) {
        String bestUuid = null;
        String bestName = null;
        long bestSeconds = -1L;

        for (Map<String, String> member : membersList) {
            String uuid = member.get("uuid");
            long seconds = uuid == null ? 0L : playtimeSecondsByPlayer.getOrDefault(uuid, 0L);
            if (seconds > bestSeconds) {
                bestSeconds = seconds;
                bestUuid = uuid;
                bestName = member.get("name");
            }
        }

        if (bestUuid == null) return null;

        Map<String, Object> mostActive = new LinkedHashMap<>();
        mostActive.put("uuid", bestUuid);
        mostActive.put("name", bestName);
        mostActive.put("playtimeSeconds", Math.max(0L, bestSeconds));
        return mostActive;
    }

    private Map<String, Long> namespaceMaterialKeys(Map<String, Long> byMaterialName) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : byMaterialName.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String namespaced = key.contains(":")
                    ? key.toLowerCase(Locale.ROOT)
                    : ("minecraft:" + key.toLowerCase(Locale.ROOT));
            out.put(namespaced, entry.getValue());
        }
        return out;
    }

    private void postJson(Map<String, Object> body) throws Exception {
        String baseUrl = config.getIngestBaseUrl();
        String path = config.getIngestEndpointPath();
        String urlString = joinUrl(baseUrl, path);

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(config.getIngestConnectTimeoutMs());
        conn.setReadTimeout(config.getIngestReadTimeoutMs());
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("X-Server", config.getIngestServerHeader());
        conn.setRequestProperty("X-API-Key", config.getIngestApiKey());

        String jsonStr = gson.toJson(body);
        try (OutputStream stream = conn.getOutputStream()) {
            stream.write(jsonStr.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            plugin.getLogger().info("[FactionApiClient] Ingest snapshot sent (" + code + ")");
        } else {
            String responseBody = readResponseBody(conn);
            if (responseBody.isBlank()) {
                plugin.getLogger().warning("[FactionApiClient] Ingest failed: HTTP " + code);
            } else {
                plugin.getLogger().warning("[FactionApiClient] Ingest failed: HTTP " + code + " body=" + truncate(responseBody, 2000));
            }
        }
        conn.disconnect();
    }

    private static String joinUrl(String base, String path) {
        if (base == null) base = "";
        if (path == null) path = "";
        if (base.endsWith("/") && path.startsWith("/")) return base.substring(0, base.length() - 1) + path;
        if (!base.endsWith("/") && !path.startsWith("/")) return base + "/" + path;
        return base + path;
    }

    private static String readResponseBody(HttpURLConnection conn) {
        InputStream stream = null;
        try {
            stream = conn.getErrorStream();
            if (stream == null) {
                stream = conn.getInputStream();
            }
            if (stream == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, max) + "...";
    }

    private static String detectMcVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion == null) return "unknown";
        int idx = bukkitVersion.indexOf('-');
        return idx <= 0 ? bukkitVersion : bukkitVersion.substring(0, idx);
    }

    private static final class OnlineState {
        private final Map<String, Integer> countsByFaction;
        private final Map<String, Map<String, String>> onlineNamesByUuidByFaction;

        private OnlineState(Map<String, Integer> countsByFaction, Map<String, Map<String, String>> onlineNamesByUuidByFaction) {
            this.countsByFaction = countsByFaction;
            this.onlineNamesByUuidByFaction = onlineNamesByUuidByFaction;
        }

        private OnlineState copy() {
            Map<String, Integer> countsCopy = new HashMap<>(countsByFaction);
            Map<String, Map<String, String>> namesCopy = new HashMap<>();
            for (Map.Entry<String, Map<String, String>> entry : onlineNamesByUuidByFaction.entrySet()) {
                namesCopy.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            return new OnlineState(countsCopy, namesCopy);
        }
    }
}
