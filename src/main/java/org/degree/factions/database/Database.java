package org.degree.factions.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Database {
    private static final int REGION_BUSY_TIMEOUT_MS = 100;
    public static final int BACKGROUND_BUSY_TIMEOUT_MS = 5_000;

    private final JavaPlugin plugin;
    private final String jdbcUrl;
    private final ConcurrentHashMap<Long, ConnectionHolder> connectionsByThread = new ConcurrentHashMap<>();
    private final Set<Connection> readOnlyConnections = ConcurrentHashMap.newKeySet();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final ThreadLocal<Integer> busyTimeoutByThread =
            ThreadLocal.withInitial(() -> REGION_BUSY_TIMEOUT_MS);
    private final AtomicInteger connectionLookups = new AtomicInteger();

    private volatile boolean closed;

    public Database(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");

        File pluginDir = plugin.getDataFolder();
        if (!pluginDir.exists() && !pluginDir.mkdirs()) {
            plugin.getLogger().severe("Could not create plugin data folder: " + pluginDir.getAbsolutePath());
        }

        File databaseFile = new File(pluginDir, "factions.db");
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        setupSchema();
    }

    private void setupSchema() {
        try (Connection schemaConnection = openConfiguredConnection(BACKGROUND_BUSY_TIMEOUT_MS);
             Statement stmt = schemaConnection.createStatement()) {
            try (ResultSet journalMode = stmt.executeQuery("PRAGMA journal_mode=WAL")) {
                if (journalMode.next() && !"wal".equalsIgnoreCase(journalMode.getString(1))) {
                    plugin.getLogger().warning(
                            "SQLite could not enable WAL mode; database reads may briefly block writes"
                    );
                }
            }
            stmt.execute("CREATE TABLE IF NOT EXISTS factions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL UNIQUE," +
                    "leader_uuid TEXT NOT NULL," +
                    "leader_name TEXT NOT NULL," +
                    "creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "color TEXT NOT NULL DEFAULT '#FFFFFF'," +
                    "discord_role_sync_enabled INTEGER NOT NULL DEFAULT 0" +
                    ");");
            ensureColumn(stmt, "factions", "discord_role_sync_enabled", "INTEGER NOT NULL DEFAULT 0");

            stmt.execute("CREATE TABLE IF NOT EXISTS faction_members (" +
                    "faction_name TEXT NOT NULL," +
                    "member_uuid TEXT NOT NULL," +
                    "member_name TEXT NOT NULL," +
                    "role TEXT NOT NULL," +
                    "FOREIGN KEY(faction_name) REFERENCES factions(name) ON DELETE CASCADE ON UPDATE CASCADE" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS faction_invites (" +
                    "invitee_uuid TEXT NOT NULL," +
                    "inviter_uuid TEXT NOT NULL," +
                    "faction_name TEXT NOT NULL," +
                    "invite_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "expiry_date TIMESTAMP NOT NULL," +
                    "PRIMARY KEY(invitee_uuid, faction_name)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS faction_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "faction_name TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "login_time TIMESTAMP NOT NULL," +
                    "logout_time TIMESTAMP" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS faction_kill_stats (" +
                    "player_uuid TEXT NOT NULL," +
                    "faction_name TEXT NOT NULL," +
                    "kills INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY (player_uuid)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS faction_block_stats (" +
                    "player_uuid  TEXT NOT NULL," +
                    "faction_name TEXT NOT NULL," +
                    "block_type   TEXT NOT NULL," +
                    "placed       INTEGER NOT NULL DEFAULT 0," +
                    "broken       INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY (player_uuid, block_type)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS faction_online_samples (" +
                    "ts_ms       INTEGER NOT NULL," +
                    "faction_name TEXT NOT NULL," +
                    "online      INTEGER NOT NULL," +
                    "PRIMARY KEY (ts_ms, faction_name)" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS faction_online_hourly (" +
                    "hour_ms      INTEGER NOT NULL," +
                    "faction_name TEXT NOT NULL," +
                    "online_sum   INTEGER NOT NULL," +
                    "sample_count INTEGER NOT NULL," +
                    "peak_online  INTEGER NOT NULL," +
                    "PRIMARY KEY (hour_ms, faction_name)" +
                    ");");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_members_faction " +
                    "ON faction_members (faction_name);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_members_uuid " +
                    "ON faction_members (member_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_members_faction_uuid " +
                    "ON faction_members (faction_name, member_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_members_name_nocase " +
                    "ON faction_members (member_name COLLATE NOCASE);");
            stmt.execute("DROP INDEX IF EXISTS idx_faction_sessions_faction_player_logout;");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_sessions_faction_player_logout_login " +
                    "ON faction_sessions (faction_name, player_uuid, logout_time, login_time);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_sessions_player_logout_login " +
                    "ON faction_sessions (player_uuid, logout_time, login_time);");
            stmt.execute("DROP INDEX IF EXISTS idx_faction_block_stats_faction_type;");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_block_stats_faction_type_totals " +
                    "ON faction_block_stats (faction_name, block_type, broken, placed);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_online_samples_faction_ts " +
                    "ON faction_online_samples (faction_name, ts_ms);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_online_samples_ts " +
                    "ON faction_online_samples (ts_ms);");
            stmt.execute("DROP INDEX IF EXISTS idx_faction_online_hourly_faction_hour;");
            stmt.execute("DROP INDEX IF EXISTS idx_faction_online_hourly_hour;");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_online_hourly_faction_hour_values " +
                    "ON faction_online_hourly " +
                    "(faction_name, hour_ms, online_sum, sample_count, peak_online);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_faction_online_hourly_hour_values " +
                    "ON faction_online_hourly " +
                    "(hour_ms, faction_name, online_sum, sample_count, peak_online);");

            migrateOnlineSamplesToHourly(schemaConnection);
            stmt.execute("PRAGMA optimize=0x10002");

            plugin.getLogger().info("SQLite database setup completed.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not set up SQLite database: " + e.getMessage());
        }
    }

    private void ensureColumn(Statement stmt, String table, String column, String definition) throws SQLException {
        try (ResultSet columns = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) {
                    return;
                }
            }
        }
        stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    /**
     * One-time compaction of legacy minute samples. Keeping sums and a sample
     * count preserves the exact weighted averages while reducing a 14-day full
     * export from hundreds of thousands of rows to a few thousand hourly rows.
     */
    private void migrateOnlineSamplesToHourly(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String migrateSql = "INSERT INTO faction_online_hourly "
                    + "(hour_ms, faction_name, online_sum, sample_count, peak_online) "
                    + "SELECT (ts_ms / 3600000) * 3600000, faction_name, "
                    + "SUM(online), COUNT(*), MAX(online) "
                    + "FROM faction_online_samples WHERE 1 = 1 "
                    + "GROUP BY (ts_ms / 3600000) * 3600000, faction_name "
                    + "ON CONFLICT(hour_ms, faction_name) DO UPDATE SET "
                    + "online_sum = faction_online_hourly.online_sum + excluded.online_sum, "
                    + "sample_count = faction_online_hourly.sample_count + excluded.sample_count, "
                    + "peak_online = MAX(faction_online_hourly.peak_online, excluded.peak_online)";
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(migrateSql);
                int compactedRows = statement.executeUpdate("DELETE FROM faction_online_samples");
                connection.commit();
                if (compactedRows > 0) {
                    plugin.getLogger().info("Compacted " + compactedRows + " online samples into hourly aggregates.");
                }
            }
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public Connection getConnection() {
        lifecycleLock.readLock().lock();
        try {
            if (closed) {
                return null;
            }

            long threadId = Thread.currentThread().getId();
            Thread currentThread = Thread.currentThread();
            ConnectionHolder existing = connectionsByThread.get(threadId);
            if (existing != null && existing.owner == currentThread && isOpen(existing.connection)) {
                if ((connectionLookups.incrementAndGet() & 0xff) == 0) {
                    reapTerminatedConnections(currentThread);
                }
                return existing.connection;
            }

            if (existing != null) {
                connectionsByThread.remove(threadId, existing);
                closeQuietly(existing.connection);
            }
            reapTerminatedConnections(currentThread);

            try {
                Connection connection = openConfiguredConnection(busyTimeoutByThread.get());
                connectionsByThread.put(threadId, new ConnectionHolder(currentThread, connection));
                return connection;
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not open SQLite connection for thread " + threadId + ": " + e.getMessage());
                return null;
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        Connection conn = getConnection();
        if (conn == null) {
            throw new SQLException("Database connection is not available");
        }
        return conn.prepareStatement(sql);
    }

    public Connection openReadOnlyConnection() throws SQLException {
        lifecycleLock.readLock().lock();
        try {
            if (closed) {
                throw new SQLException("Database has been closed");
            }

            readOnlyConnections.removeIf(connection -> !isOpen(connection));
            Connection connection = openConfiguredConnection(BACKGROUND_BUSY_TIMEOUT_MS);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA temp_store=MEMORY");
                stmt.execute("PRAGMA cache_size=-8192");
                stmt.execute("PRAGMA query_only=ON");
            } catch (SQLException e) {
                closeQuietly(connection);
                throw e;
            }
            readOnlyConnections.add(connection);
            return connection;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public void closeConnection() {
        lifecycleLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;

            connectionsByThread.values().forEach(holder -> closeQuietly(holder.connection));
            readOnlyConnections.forEach(this::closeQuietly);
            connectionsByThread.clear();
            readOnlyConnections.clear();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
        plugin.getLogger().info("Database connections closed.");
    }

    public void setBusyTimeoutForCurrentThread(int timeoutMs) {
        int normalizedTimeout = Math.max(0, timeoutMs);
        busyTimeoutByThread.set(normalizedTimeout);
        Connection connection = getConnection();
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=" + normalizedTimeout);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not configure SQLite busy timeout: " + e.getMessage());
        }
    }

    /**
     * Performs WAL maintenance only from the background database worker. Auto
     * checkpoints are disabled per connection so a gameplay command can never
     * become the thread that performs checkpoint I/O after its COMMIT.
     */
    public void checkpointWalPassive() {
        Connection connection = getConnection();
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement();
             ResultSet ignored = statement.executeQuery("PRAGMA wal_checkpoint(PASSIVE)")) {
            // Reading/closing the result is enough; PASSIVE never waits for readers.
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not checkpoint SQLite WAL: " + e.getMessage());
        }
    }

    public void invalidateCurrentConnection() {
        lifecycleLock.readLock().lock();
        try {
            long threadId = Thread.currentThread().getId();
            ConnectionHolder holder = connectionsByThread.get(threadId);
            if (holder != null
                    && holder.owner == Thread.currentThread()
                    && connectionsByThread.remove(threadId, holder)) {
                closeQuietly(holder.connection);
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    private Connection openConfiguredConnection(int busyTimeoutMs) throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try {
            configureConnection(connection, busyTimeoutMs);
            return connection;
        } catch (SQLException e) {
            closeQuietly(connection);
            throw e;
        }
    }

    private void configureConnection(Connection connection, int busyTimeoutMs) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA busy_timeout=" + Math.max(0, busyTimeoutMs));
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA wal_autocheckpoint=0");
        }
    }

    private boolean isOpen(Connection connection) {
        if (connection == null) {
            return false;
        }
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private void reapTerminatedConnections(Thread currentThread) {
        connectionsByThread.forEach((threadId, holder) -> {
            if (holder.owner != currentThread
                    && !holder.owner.isAlive()
                    && connectionsByThread.remove(threadId, holder)) {
                closeQuietly(holder.connection);
            }
        });
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error while closing SQLite connection: " + e.getMessage());
        }
    }

    private static final class ConnectionHolder {
        private final Thread owner;
        private final Connection connection;

        private ConnectionHolder(Thread owner, Connection connection) {
            this.owner = owner;
            this.connection = connection;
        }
    }
}
