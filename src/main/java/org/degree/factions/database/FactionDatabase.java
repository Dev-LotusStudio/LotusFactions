package org.degree.factions.database;

import org.degree.factions.models.Faction;
import org.degree.factions.utils.BlockStatCache;

import java.sql.*;
import java.util.*;
import java.util.Objects;

public class FactionDatabase {

    private final Database database;
    private static final long MIN_SESSION_MS = 5 * 60_000;
    private static final long MERGE_THRESHOLD_MS = 2 * 60_000;

    public FactionDatabase(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public Connection getConnection() {
        return database.getConnection();
    }

    public void addInvite(String factionName, String inviterUUID, String inviteeUUID) throws SQLException {
        removeAllInvitesForPlayer(inviteeUUID);
        String sql = "INSERT INTO faction_invites (faction_name, inviter_uuid, invitee_uuid, invite_date, expiry_date) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.setString(2, inviterUUID);
            pstmt.setString(3, inviteeUUID);
            pstmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            pstmt.setTimestamp(5, new Timestamp(System.currentTimeMillis() + 86400000));
            pstmt.executeUpdate();
        }
    }

    public void removeAllInvitesForPlayer(String inviteeUUID) throws SQLException {
        String sql = "DELETE FROM faction_invites WHERE invitee_uuid = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, inviteeUUID);
            pstmt.executeUpdate();
        }
    }

    public void removeInvite(String factionName, String inviteeUUID) throws SQLException {
        String sql = "DELETE FROM faction_invites WHERE faction_name = ? AND invitee_uuid = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.setString(2, inviteeUUID);
            pstmt.executeUpdate();
        }
    }

    public String getFactionNameByLeader(String leaderUUID) throws SQLException {
        String sql = "SELECT name FROM factions WHERE leader_uuid = ?";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, leaderUUID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("name") : null;
            }
        }
    }

    public void deleteFaction(String factionName) throws SQLException {
        try (PreparedStatement ps = database.prepareStatement(
                "DELETE FROM faction_members WHERE faction_name = ?")) {
            ps.setString(1, factionName);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = database.prepareStatement(
                "DELETE FROM faction_invites WHERE faction_name = ?")) {
            ps.setString(1, factionName);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = database.prepareStatement(
                "DELETE FROM faction_sessions WHERE faction_name = ?")) {
            ps.setString(1, factionName);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = database.prepareStatement(
                "DELETE FROM factions WHERE name = ?")) {
            ps.setString(1, factionName);
            ps.executeUpdate();
        }
    }

    public String getFactionNameForInvite(String inviteeUUID) throws SQLException {
        String sql = "SELECT faction_name, expiry_date FROM faction_invites WHERE invitee_uuid = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, inviteeUUID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp expiry = rs.getTimestamp("expiry_date");
                    if (expiry != null && expiry.before(new Timestamp(System.currentTimeMillis()))) {
                        removeInvite(rs.getString("faction_name"), inviteeUUID);
                        return null;
                    }
                    return rs.getString("faction_name");
                }
            }
        }
        return null;
    }

    public String getFactionNameForPlayer(String playerUUID) throws SQLException {
        String sql = "SELECT faction_name FROM faction_members WHERE member_uuid = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("faction_name");
            }
        }
        return null;
    }

    public void addMemberToFaction(String factionName, String memberUUID, String memberName, String role) throws SQLException {
        String sql = "INSERT INTO faction_members (faction_name, member_uuid, member_name, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.setString(2, memberUUID);
            pstmt.setString(3, memberName);
            pstmt.setString(4, role);
            pstmt.executeUpdate();
        }
    }

    public boolean factionExists(String name) throws SQLException {
        String sql = "SELECT id FROM factions WHERE name = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void createFaction(String name, String leaderUUID, String leaderName, String colorHex) throws SQLException {
        String sql = "INSERT INTO factions (name, leader_uuid, leader_name, color) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, leaderUUID);
            pstmt.setString(3, leaderName);
            pstmt.setString(4, colorHex);
            pstmt.executeUpdate();
        }
    }


    public void removeMemberFromFaction(String playerUUID) throws SQLException {
        String sql = "DELETE FROM faction_members WHERE member_uuid = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID);
            pstmt.executeUpdate();
        }
    }

    public boolean isLeader(String playerUUID) throws SQLException {
        String sql = "SELECT id FROM factions WHERE leader_uuid = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void transferLeadership(String factionName, String newLeaderUUID) throws SQLException {
        String sql = "UPDATE factions SET leader_uuid = ? WHERE name = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, newLeaderUUID);
            pstmt.setString(2, factionName);
            pstmt.executeUpdate();
        }
    }

    public boolean isMemberOfFaction(String factionName, String playerName) throws SQLException {
        String sql = "SELECT 1 FROM faction_members WHERE faction_name = ? AND member_name = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            pstmt.setString(2, playerName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<String> getMemberNamesOfFaction(String factionName) throws SQLException {
        List<String> memberNames = new ArrayList<>();
        String sql = "SELECT member_name FROM faction_members WHERE faction_name = ?";
        try (PreparedStatement pstmt = database.prepareStatement(sql)) {
            pstmt.setString(1, factionName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) memberNames.add(rs.getString("member_name"));
            }
        }
        return memberNames;
    }

    public int getJoinsToday(String factionName) throws SQLException {
        String sql =
                "SELECT COUNT(DISTINCT player_uuid) " +
                        "  FROM faction_sessions " +
                        " WHERE faction_name = ? " +
                        "   AND login_time >= (strftime('%s','now','localtime','start of day') * 1000)";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<Map<String,Object>> getActivityOverTime(String factionName) throws SQLException {
        String sql =
                "SELECT " +
                        "  date(login_time/1000, 'unixepoch','localtime') AS date, " +
                        "  COUNT(DISTINCT player_uuid) AS count " +
                        "FROM faction_sessions " +
                        "WHERE faction_name = ? " +
                        "GROUP BY date " +
                        "ORDER BY date";
        List<Map<String,Object>> list = new ArrayList<>();
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> row = new HashMap<>();
                    row.put("date",  rs.getString("date"));
                    row.put("count", rs.getInt("count"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public List<Map<String,Object>> getDailyPlaytime(String factionName) throws SQLException {
        String sql =
                "SELECT " +
                        "  date(login_time/1000, 'unixepoch','localtime') AS date, " +
                        "  SUM((logout_time - login_time) / 1000)               AS seconds " +
                        "FROM faction_sessions " +
                        "WHERE faction_name = ? " +
                        "  AND logout_time IS NOT NULL " +
                        "GROUP BY date " +
                        "ORDER BY date";

        List<Map<String,Object>> list = new ArrayList<>();
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> row = new HashMap<>();
                    row.put("date",    rs.getString("date"));
                    row.put("seconds", rs.getLong("seconds"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public void logSessionStart(String factionName, String playerUuid) {
        long now = System.currentTimeMillis();
        Connection conn = database.getConnection();

        try (PreparedStatement psOpen = conn.prepareStatement(
                "SELECT id, faction_name FROM faction_sessions " +
                        "WHERE player_uuid = ? AND logout_time IS NULL " +
                        "ORDER BY login_time DESC LIMIT 1"
        )) {
            psOpen.setString(1, playerUuid);
            try (ResultSet rs = psOpen.executeQuery()) {
                if (rs.next()) {
                    long openId = rs.getLong("id");
                    String openFaction = rs.getString("faction_name");
                    if (Objects.equals(openFaction, factionName)) {
                        return;
                    }
                    try (PreparedStatement psClose = conn.prepareStatement(
                            "UPDATE faction_sessions SET logout_time = ? WHERE id = ?"
                    )) {
                        psClose.setTimestamp(1, new Timestamp(now));
                        psClose.setLong(2, openId);
                        psClose.executeUpdate();
                    }
                }
            }
        } catch (SQLException ignored) {}

        try (
                PreparedStatement psLast = conn.prepareStatement(
                        "SELECT id, logout_time, login_time, faction_name FROM faction_sessions " +
                                " WHERE player_uuid = ? " +
                                " ORDER BY login_time DESC LIMIT 1"
                )
        ) {
            psLast.setString(1, playerUuid);
            try (ResultSet rs = psLast.executeQuery()) {
                if (rs.next()) {
                    Timestamp tl = rs.getTimestamp("logout_time");
                    long   lid = rs.getLong("id");
                    String lastFaction = rs.getString("faction_name");
                    if (tl != null && now - tl.getTime() <= MERGE_THRESHOLD_MS && Objects.equals(lastFaction, factionName)) {
                        try (PreparedStatement psUpd = conn.prepareStatement(
                                "UPDATE faction_sessions SET logout_time = NULL WHERE id = ?"
                        )) {
                            psUpd.setLong(1, lid);
                            psUpd.executeUpdate();
                            return;
                        }
                    }
                }
            }
        } catch (SQLException ignored) {}

        try (PreparedStatement ps = database.prepareStatement(
                "INSERT INTO faction_sessions (faction_name, player_uuid, login_time) VALUES (?,?,?)"
        )) {
            ps.setString(1, factionName);
            ps.setString(2, playerUuid);
            ps.setTimestamp(3, new Timestamp(now));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void logSessionEnd(String playerUuid) {
        String sql = "UPDATE faction_sessions SET logout_time = ? WHERE player_uuid = ? AND logout_time IS NULL";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setString(2, playerUuid);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public Faction loadFaction(String name) throws SQLException {
        String sql = "SELECT id, name, leader_uuid, leader_name, creation_date, color FROM factions WHERE name = ?";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Faction f = new Faction();
                f.setId(rs.getInt("id"));
                f.setName(rs.getString("name"));
                f.setLeaderUuid(rs.getString("leader_uuid"));
                f.setLeaderName(rs.getString("leader_name"));
                f.setCreationDate(rs.getTimestamp("creation_date"));
                f.setColorHex(rs.getString("color"));
                return f;
            }
        }
    }

    public List<String> getAllFactionNames() throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM factions";
        try (PreparedStatement ps = database.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }

    public void insertOnlineSamples(long tsMs, Map<String, Integer> onlineByFaction, Collection<String> factionNames) {
        String sql = "INSERT OR REPLACE INTO faction_online_samples (ts_ms, faction_name, online) VALUES (?, ?, ?)";
        Connection conn = database.getConnection();
        if (conn == null) return;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String factionName : factionNames) {
                int online = onlineByFaction.getOrDefault(factionName, 0);
                ps.setLong(1, tsMs);
                ps.setString(2, factionName);
                ps.setInt(3, online);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> getOnlineChartByDay(String factionName, int daysBack) throws SQLException {
        long sinceMs = System.currentTimeMillis() - (daysBack * 86_400_000L);
        String sql =
                "SELECT " +
                        "  date(ts_ms/1000, 'unixepoch') AS date, " +
                        "  AVG(online)                   AS avg_online, " +
                        "  MAX(online)                   AS peak_online " +
                        "FROM faction_online_samples " +
                        "WHERE faction_name = ? AND ts_ms >= ? " +
                        "GROUP BY date " +
                        "ORDER BY date";

        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            ps.setLong(2, sinceMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", rs.getString("date"));
                    row.put("avgOnline", rs.getDouble("avg_online"));
                    row.put("peakOnline", rs.getInt("peak_online"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> getOnlineChartByHour(String factionName, int daysBack) throws SQLException {
        long sinceMs = System.currentTimeMillis() - (daysBack * 86_400_000L);
        String sql =
                "SELECT " +
                        "  CAST(strftime('%H', ts_ms/1000, 'unixepoch') AS INTEGER) AS hour, " +
                        "  AVG(online)                                                  AS avg_online " +
                        "FROM faction_online_samples " +
                        "WHERE faction_name = ? AND ts_ms >= ? " +
                        "GROUP BY hour " +
                        "ORDER BY hour";

        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            ps.setLong(2, sinceMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("hour", rs.getInt("hour"));
                    row.put("avgOnline", rs.getDouble("avg_online"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    public Map<String, Long> getPlaytimeSecondsByPlayerInFaction(String factionName, long nowMs) throws SQLException {
        Map<String, Long> secondsByPlayer = new HashMap<>();

        String closedSql =
                "SELECT player_uuid, SUM((logout_time - login_time) / 1000) AS seconds " +
                        "FROM faction_sessions " +
                        "WHERE faction_name = ? AND logout_time IS NOT NULL " +
                        "GROUP BY player_uuid";
        try (PreparedStatement ps = database.prepareStatement(closedSql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    secondsByPlayer.put(rs.getString("player_uuid"), rs.getLong("seconds"));
                }
            }
        }

        String openSql =
                "SELECT player_uuid, MIN(login_time) AS login_time " +
                        "FROM faction_sessions " +
                        "WHERE faction_name = ? AND logout_time IS NULL " +
                        "GROUP BY player_uuid";
        try (PreparedStatement ps = database.prepareStatement(openSql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long loginMs = rs.getTimestamp("login_time").getTime();
                    long seconds = Math.max(0L, (nowMs - loginMs) / 1000L);
                    secondsByPlayer.merge(rs.getString("player_uuid"), seconds, Long::sum);
                }
            }
        }

        return secondsByPlayer;
    }

    public Map<String, Long> getBlockTotalsForFaction(String factionName) throws SQLException {
        String sql =
                "SELECT " +
                        "  COALESCE(SUM(broken), 0) AS broken_total, " +
                        "  COALESCE(SUM(placed), 0) AS placed_total " +
                        "FROM faction_block_stats " +
                        "WHERE faction_name = ?";

        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> totals = new HashMap<>();
                if (!rs.next()) {
                    totals.put("brokenTotal", 0L);
                    totals.put("placedTotal", 0L);
                    return totals;
                }
                totals.put("brokenTotal", rs.getLong("broken_total"));
                totals.put("placedTotal", rs.getLong("placed_total"));
                return totals;
            }
        }
    }

    public Map<String, Long> getBrokenByTypeForFaction(String factionName) throws SQLException {
        String sql =
                "SELECT block_type, COALESCE(SUM(broken), 0) AS broken_total " +
                        "FROM faction_block_stats " +
                        "WHERE faction_name = ? " +
                        "GROUP BY block_type " +
                        "HAVING broken_total > 0";
        Map<String, Long> out = new HashMap<>();
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("block_type"), rs.getLong("broken_total"));
                }
            }
        }
        return out;
    }

    public Map<String, Long> getPlacedByTypeForFaction(String factionName) throws SQLException {
        String sql =
                "SELECT block_type, COALESCE(SUM(placed), 0) AS placed_total " +
                        "FROM faction_block_stats " +
                        "WHERE faction_name = ? " +
                        "GROUP BY block_type " +
                        "HAVING placed_total > 0";
        Map<String, Long> out = new HashMap<>();
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("block_type"), rs.getLong("placed_total"));
                }
            }
        }
        return out;
    }

    public void saveOrUpdateBlockStatsBatch(Map<String, Map<String, BlockStatCache.BlockStat>> allStats) {
        String sql = "INSERT INTO faction_block_stats (player_uuid, faction_name, block_type, placed, broken) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(player_uuid, block_type) DO UPDATE SET " +
                "placed = placed + EXCLUDED.placed, " +
                "broken = broken + EXCLUDED.broken, " +
                "faction_name = EXCLUDED.faction_name";
        Connection conn = database.getConnection();
        if (conn == null) return;

        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, Map<String, BlockStatCache.BlockStat>> entryByUuid : allStats.entrySet()) {
                    String uuid = entryByUuid.getKey();
                    for (Map.Entry<String, BlockStatCache.BlockStat> entry : entryByUuid.getValue().entrySet()) {
                        String blockType = entry.getKey();
                        BlockStatCache.BlockStat stat = entry.getValue();
                        ps.setString(1, uuid);
                        ps.setString(2, stat.factionName);
                        ps.setString(3, blockType);
                        ps.setInt(4, stat.placed);
                        ps.setInt(5, stat.broken);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            e.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {}
        }
    }

    public List<Map<String, String>> getMemberNameUuidPairsOfFaction(String factionName) throws SQLException {
        List<Map<String, String>> list = new ArrayList<>();
        String sql = "SELECT member_name, member_uuid FROM faction_members WHERE faction_name = ?";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> map = new HashMap<>();
                    map.put("name", rs.getString("member_name"));
                    map.put("uuid", rs.getString("member_uuid"));
                    list.add(map);
                }
            }
        }
        return list;
    }

    public long getTotalHoursForPlayer(String playerUUID) throws SQLException {
        String sql = "SELECT SUM((logout_time - login_time) / 1000) AS seconds FROM faction_sessions WHERE player_uuid = ? AND logout_time IS NOT NULL";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, playerUUID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("seconds") / 3600;
            }
        }
        return 0;
    }

    public void incrementKill(String playerUuid, String factionName) {
        try (PreparedStatement ps = database.prepareStatement(
                "INSERT INTO faction_kill_stats (player_uuid, faction_name, kills) " +
                        "VALUES (?, ?, 1) " +
                        "ON CONFLICT(player_uuid) DO UPDATE SET " +
                        "kills = kills + 1, faction_name = EXCLUDED.faction_name"
        )) {
            ps.setString(1, playerUuid);
            ps.setString(2, factionName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveKillStatsBatch(Map<String, org.degree.factions.utils.KillStatCache.KillStat> statsByUuid) {
        if (statsByUuid == null || statsByUuid.isEmpty()) return;

        String sql =
                "INSERT INTO faction_kill_stats (player_uuid, faction_name, kills) " +
                        "VALUES (?, ?, ?) " +
                        "ON CONFLICT(player_uuid) DO UPDATE SET " +
                        "kills = kills + EXCLUDED.kills, faction_name = EXCLUDED.faction_name";

        Connection conn = database.getConnection();
        if (conn == null) return;

        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, org.degree.factions.utils.KillStatCache.KillStat> entry : statsByUuid.entrySet()) {
                    String uuid = entry.getKey();
                    org.degree.factions.utils.KillStatCache.KillStat stat = entry.getValue();
                    if (uuid == null || stat == null || stat.factionName == null || stat.kills <= 0) continue;
                    ps.setString(1, uuid);
                    ps.setString(2, stat.factionName);
                    ps.setInt(3, stat.kills);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            e.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {}
        }
    }

    public int getTotalKillsForPlayer(String playerUUID) throws SQLException {
        String sql = "SELECT kills FROM faction_kill_stats WHERE player_uuid = ?";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, playerUUID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("kills");
            }
        }
        return 0;
    }

    public String getMostKillsMember(String factionName) throws SQLException {
        return "-";
    }

    public List<Map<String, Object>> getResourcesOfFaction(String factionName) {
        List<Map<String, Object>> resources = new ArrayList<>();
        String sql = "SELECT block_type AS resource, SUM(broken) AS count " +
                "FROM faction_block_stats " +
                "WHERE faction_name = ? " +
                "GROUP BY block_type " +
                "ORDER BY count DESC";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("resource", rs.getString("resource"));
                    map.put("count", rs.getInt("count"));
                    resources.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resources;
    }


    public String getMostActiveMember(String factionName) throws SQLException {
        String sql =
                "SELECT m.member_name, SUM((s.logout_time - s.login_time) / 1000) AS seconds " +
                        "FROM faction_sessions s JOIN faction_members m ON s.player_uuid = m.member_uuid " +
                        "WHERE s.faction_name = ? AND s.logout_time IS NOT NULL " +
                        "GROUP BY s.player_uuid " +
                        "ORDER BY seconds DESC LIMIT 1";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("member_name");
            }
        }
        return "-";
    }

    public List<Map<String, Object>> getBlocksOfFaction(String factionName) throws SQLException {
        List<Map<String, Object>> blocks = new ArrayList<>();
        String sql =
                "SELECT m.member_name, SUM(b.placed) as placed, SUM(b.broken) as broken " +
                        "FROM faction_block_stats b " +
                        "JOIN faction_members m ON b.player_uuid = m.member_uuid " +
                        "WHERE b.faction_name = ? " +
                        "GROUP BY m.member_name";
        try (PreparedStatement ps = database.prepareStatement(sql)) {
            ps.setString(1, factionName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", rs.getString("member_name"));
                    map.put("placed", rs.getInt("placed"));
                    map.put("broken", rs.getInt("broken"));
                    blocks.add(map);
                }
            }
        }
        return blocks;
    }


}
