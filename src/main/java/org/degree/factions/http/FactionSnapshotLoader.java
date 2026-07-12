package org.degree.factions.http;

import org.degree.factions.database.FactionDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Loads an ingest snapshot using one short-lived read-only SQLite connection.
 *
 * <p>The old exporter issued nine statements per faction on the connection used
 * by gameplay commands. This loader performs six bulk statements regardless of
 * the number of factions and never shares transaction state with a region
 * thread.</p>
 */
final class FactionSnapshotLoader {
    private final FactionDatabase factionDatabase;

    FactionSnapshotLoader(FactionDatabase factionDatabase) {
        this.factionDatabase = factionDatabase;
    }

    List<Snapshot> load(Instant capturedAt, Collection<String> requestedNames, int chartsDays) throws SQLException {
        List<String> names = normalizeNames(requestedNames);
        if (names != null && names.isEmpty()) {
            return List.of();
        }

        Map<String, Snapshot> snapshots = new LinkedHashMap<>();
        try (Connection connection = factionDatabase.openReadOnlyConnection()) {
            connection.setAutoCommit(false);
            try {
                loadFactions(connection, names, snapshots);
                if (!snapshots.isEmpty()) {
                    loadMembers(connection, names, snapshots);
                    loadPlaytime(connection, names, snapshots, capturedAt.toEpochMilli());
                    long sinceMs = capturedAt.toEpochMilli() - Math.max(1, chartsDays) * 86_400_000L;
                    loadDailyCharts(connection, names, snapshots, sinceMs);
                    loadHourlyCharts(connection, names, snapshots, sinceMs);
                    loadBlocks(connection, names, snapshots);
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
        return new ArrayList<>(snapshots.values());
    }

    private void loadFactions(Connection connection, List<String> names, Map<String, Snapshot> snapshots) throws SQLException {
        if (names != null) {
            for (String name : names) {
                snapshots.put(name, new Snapshot(name));
            }
        }

        String sql = "SELECT id, name, leader_uuid, leader_name, color, discord_role_sync_enabled FROM factions"
                + whereIn("name", names)
                + " ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindNames(statement, 1, names);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString("name");
                    Snapshot snapshot = snapshots.computeIfAbsent(name, Snapshot::new);
                    snapshot.faction = new FactionInfo(
                            result.getLong("id"),
                            result.getString("leader_uuid"),
                            result.getString("leader_name"),
                            result.getString("color"),
                            result.getInt("discord_role_sync_enabled") != 0
                    );
                }
            }
        }
    }

    private void loadMembers(Connection connection, List<String> names, Map<String, Snapshot> snapshots) throws SQLException {
        String sql = "SELECT faction_name, member_name, member_uuid FROM faction_members"
                + whereIn("faction_name", names)
                + " ORDER BY faction_name, rowid";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindNames(statement, 1, names);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Snapshot snapshot = snapshots.get(result.getString("faction_name"));
                    if (snapshot != null) {
                        snapshot.members.add(new Member(result.getString("member_uuid"), result.getString("member_name")));
                    }
                }
            }
        }
    }

    private void loadPlaytime(
            Connection connection,
            List<String> names,
            Map<String, Snapshot> snapshots,
            long capturedAtMs
    ) throws SQLException {
        String sql = "SELECT m.faction_name, m.member_uuid AS player_uuid, "
                + "COALESCE(SUM(CASE WHEN s.logout_time IS NOT NULL "
                + "THEN (s.logout_time - s.login_time) / 1000 ELSE 0 END), 0) AS closed_seconds, "
                + "MIN(CASE WHEN s.logout_time IS NULL THEN s.login_time END) AS open_login_time "
                + "FROM (SELECT DISTINCT faction_name, member_uuid FROM faction_members) m "
                + "LEFT JOIN faction_sessions s "
                + "ON s.faction_name = m.faction_name AND s.player_uuid = m.member_uuid"
                + whereIn("m.faction_name", names)
                + " GROUP BY m.faction_name, m.member_uuid";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindNames(statement, 1, names);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Snapshot snapshot = snapshots.get(result.getString("faction_name"));
                    if (snapshot == null) {
                        continue;
                    }

                    long seconds = result.getLong("closed_seconds");
                    Timestamp openLogin = result.getTimestamp("open_login_time");
                    if (openLogin != null) {
                        seconds += Math.max(0L, (capturedAtMs - openLogin.getTime()) / 1000L);
                    }
                    snapshot.playtimeSecondsByPlayer.put(result.getString("player_uuid"), seconds);
                }
            }
        }
    }

    private void loadDailyCharts(
            Connection connection,
            List<String> names,
            Map<String, Snapshot> snapshots,
            long sinceMs
    ) throws SQLException {
        String sql = "SELECT faction_name, date(ts_ms/1000, 'unixepoch') AS date, "
                + "AVG(online) AS avg_online, MAX(online) AS peak_online "
                + "FROM faction_online_samples WHERE ts_ms >= ?"
                + andIn("faction_name", names)
                + " GROUP BY faction_name, date ORDER BY faction_name, date";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceMs);
            bindNames(statement, 2, names);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Snapshot snapshot = snapshots.get(result.getString("faction_name"));
                    if (snapshot == null) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", result.getString("date"));
                    row.put("avgOnline", result.getDouble("avg_online"));
                    row.put("peakOnline", result.getInt("peak_online"));
                    snapshot.onlineByDay.add(row);
                }
            }
        }
    }

    private void loadHourlyCharts(
            Connection connection,
            List<String> names,
            Map<String, Snapshot> snapshots,
            long sinceMs
    ) throws SQLException {
        String sql = "SELECT faction_name, "
                + "CAST(strftime('%H', ts_ms/1000, 'unixepoch') AS INTEGER) AS hour, "
                + "AVG(online) AS avg_online "
                + "FROM faction_online_samples WHERE ts_ms >= ?"
                + andIn("faction_name", names)
                + " GROUP BY faction_name, hour ORDER BY faction_name, hour";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceMs);
            bindNames(statement, 2, names);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Snapshot snapshot = snapshots.get(result.getString("faction_name"));
                    if (snapshot == null) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("hour", result.getInt("hour"));
                    row.put("avgOnline", result.getDouble("avg_online"));
                    snapshot.onlineByHour.add(row);
                }
            }
        }
    }

    private void loadBlocks(Connection connection, List<String> names, Map<String, Snapshot> snapshots) throws SQLException {
        String sql = "SELECT faction_name, block_type, "
                + "COALESCE(SUM(broken), 0) AS broken_total, "
                + "COALESCE(SUM(placed), 0) AS placed_total "
                + "FROM faction_block_stats"
                + whereIn("faction_name", names)
                + " GROUP BY faction_name, block_type "
                + "HAVING broken_total > 0 OR placed_total > 0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindNames(statement, 1, names);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Snapshot snapshot = snapshots.get(result.getString("faction_name"));
                    if (snapshot == null) {
                        continue;
                    }
                    String blockType = result.getString("block_type");
                    long broken = result.getLong("broken_total");
                    long placed = result.getLong("placed_total");
                    if (broken > 0) {
                        snapshot.brokenByType.put(blockType, broken);
                    }
                    if (placed > 0) {
                        snapshot.placedByType.put(blockType, placed);
                    }
                    snapshot.brokenTotal += broken;
                    snapshot.placedTotal += placed;
                }
            }
        }
    }

    private static List<String> normalizeNames(Collection<String> requestedNames) {
        if (requestedNames == null) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String name : requestedNames) {
            if (name != null && !name.isBlank()) {
                normalized.add(name);
            }
        }
        return new ArrayList<>(normalized);
    }

    private static String whereIn(String column, List<String> names) {
        return names == null ? "" : " WHERE " + column + " IN (" + placeholders(names.size()) + ")";
    }

    private static String andIn(String column, List<String> names) {
        return names == null ? "" : " AND " + column + " IN (" + placeholders(names.size()) + ")";
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static int bindNames(PreparedStatement statement, int startIndex, List<String> names) throws SQLException {
        if (names == null) {
            return startIndex;
        }
        int index = startIndex;
        for (String name : names) {
            statement.setString(index++, name);
        }
        return index;
    }

    static final class Snapshot {
        final String name;
        FactionInfo faction;
        final List<Member> members = new ArrayList<>();
        final Map<String, Long> playtimeSecondsByPlayer = new HashMap<>();
        final List<Map<String, Object>> onlineByDay = new ArrayList<>();
        final List<Map<String, Object>> onlineByHour = new ArrayList<>();
        final Map<String, Long> brokenByType = new LinkedHashMap<>();
        final Map<String, Long> placedByType = new LinkedHashMap<>();
        long brokenTotal;
        long placedTotal;

        private Snapshot(String name) {
            this.name = name;
        }
    }

    static final class FactionInfo {
        final long id;
        final String leaderUuid;
        final String leaderName;
        final String colorHex;
        final boolean discordRoleSyncEnabled;

        private FactionInfo(long id, String leaderUuid, String leaderName, String colorHex, boolean discordRoleSyncEnabled) {
            this.id = id;
            this.leaderUuid = leaderUuid;
            this.leaderName = leaderName;
            this.colorHex = colorHex;
            this.discordRoleSyncEnabled = discordRoleSyncEnabled;
        }
    }

    static final class Member {
        final String uuid;
        final String name;

        private Member(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }
}
