package org.degree.factions;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.degree.factions.commands.FactionChatCommand;
import org.degree.factions.commands.FactionCommandRouter;
import org.degree.factions.commands.FactionRoleSyncCommand;
import org.degree.factions.database.Database;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.http.FactionApiClient;
import org.degree.factions.listeners.BlockStatListener;
import org.degree.factions.listeners.KillStatListener;
import org.degree.factions.listeners.SessionListener;
import org.degree.factions.models.Faction;
import org.degree.factions.tasks.KillStatSaverTask;
import org.degree.factions.tasks.OnlineSampleTask;
import org.degree.factions.utils.BlockStatCache;
import org.degree.factions.utils.ConfigManager;
import org.degree.factions.utils.FactionCache;
import org.degree.factions.utils.FactionUtils;
import org.degree.factions.utils.KillStatCache;
import org.degree.factions.utils.LocalizationManager;
import org.degree.factions.utils.OnlinePlayerCache;
import org.degree.factions.utils.SchedulerCompat;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

public final class Factions extends JavaPlugin {
    private static Factions instance;
    private ConfigManager configManager;
    private LocalizationManager localizationManager;
    private FactionApiClient apiClient;
    private FactionUtils factionUtils;
    private Database database;
    private FactionDatabase factionDatabase;
    private ExecutorService databaseExecutor;

    @Override
    public void onEnable() {
        int pluginId = 25785;
        new Metrics(this, pluginId);

        instance = this;
        database = new Database(this);
        databaseExecutor = Executors.newSingleThreadExecutor(worker -> {
            Thread thread = new Thread(() -> {
                database.setBusyTimeoutForCurrentThread(Database.BACKGROUND_BUSY_TIMEOUT_MS);
                worker.run();
            }, getName() + "-Database");
            thread.setDaemon(true);
            return thread;
        });
        factionDatabase = new FactionDatabase(database);
        factionUtils = new FactionUtils();
        configManager = new ConfigManager(this);
        String lang = configManager.getString("lang", "en");
        localizationManager = new LocalizationManager(this, lang);

        apiClient = new FactionApiClient(this, configManager, factionDatabase, factionUtils);

        Objects.requireNonNull(getCommand("faction"), "Command /faction not found in plugin.yml")
                .setExecutor(new FactionCommandRouter());
        Objects.requireNonNull(getCommand("fchat"), "Command /fchat not found in plugin.yml")
                .setExecutor(new FactionChatCommand());
        Objects.requireNonNull(getCommand("factionrolesync"), "Command /factionrolesync not found in plugin.yml")
                .setExecutor(new FactionRoleSyncCommand());

        getServer().getPluginManager().registerEvents(new SessionListener(this, factionDatabase), this);
        getServer().getPluginManager().registerEvents(new BlockStatListener(factionDatabase), this);
        getServer().getPluginManager().registerEvents(new KillStatListener(), this);

        SchedulerCompat.runGlobalTimer(this, new BlockStatSaverTask(factionDatabase), 20L * 60, 20L * 60);
        SchedulerCompat.runGlobalTimer(this, new KillStatSaverTask(this, factionDatabase), 20L * 60, 20L * 60);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FactionPlaceholder(this).register();
            getLogger().info("Registered FactionPlaceholder for PAPI");
        } else {
            getLogger().warning("PlaceholderAPI not found; FactionPlaceholder not registered");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            OnlinePlayerCache.add(player);
            String uuid = player.getUniqueId().toString();
            try {
                String faction = factionDatabase.getFactionNameForPlayer(uuid);
                FactionCache.setFaction(uuid, faction);
                if (faction != null) {
                    Faction loaded = factionDatabase.loadFaction(faction);
                    if (loaded != null) {
                        FactionCache.setFactionColor(faction, loaded.getColorHex());
                    }
                }
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Failed to warm faction cache for " + player.getName(), e);
            }
        }

        if (configManager.isIngestEnabled()) {
            int sampleSeconds = Math.max(10, configManager.getIngestOnlineSampleSeconds());
            SchedulerCompat.runGlobalTimer(this, new OnlineSampleTask(this, factionDatabase), 20L * 5, 20L * sampleSeconds);

            int intervalSeconds = Math.max(30, configManager.getIngestIntervalSeconds());
            SchedulerCompat.runGlobalTimer(this, apiClient::postAllFactionsFromDatabase, 20L * 10, 20L * intervalSeconds);

            getLogger().info("Ingest enabled: sampling online every " + sampleSeconds + "s, sending snapshot every " + intervalSeconds + "s");
        }
    }

    @Override
    public void onDisable() {
        if (apiClient != null) {
            apiClient.close();
        }
        flushAndStopDatabaseExecutor();
        if (database != null) {
            database.closeConnection();
        }
    }

    public class BlockStatSaverTask implements Runnable {
        private final FactionDatabase db;

        public BlockStatSaverTask(FactionDatabase db) {
            this.db = db;
        }

        @Override
        public void run() {
            runDatabaseTask(() -> {
                Map<String, Map<String, BlockStatCache.BlockStat>> snapshot = BlockStatCache.getAndClearStats();
                if (!snapshot.isEmpty() && !db.saveOrUpdateBlockStatsBatch(snapshot)) {
                    BlockStatCache.merge(snapshot);
                }
            });
        }
    }

    public boolean runDatabaseTask(Runnable task) {
        ExecutorService executor = databaseExecutor;
        if (executor == null || executor.isShutdown()) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    getLogger().log(Level.WARNING, "Asynchronous database task failed", e);
                }
            });
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private void flushAndStopDatabaseExecutor() {
        ExecutorService executor = databaseExecutor;
        if (executor == null) {
            return;
        }

        Future<?> finalFlush;
        try {
            finalFlush = executor.submit(this::flushPendingStatistics);
        } catch (RejectedExecutionException ignored) {
            forceStopDatabaseExecutor(executor);
            return;
        }
        executor.shutdown();
        try {
            finalFlush.get(20, TimeUnit.SECONDS);
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                getLogger().warning("Database worker did not stop after the final flush; stopping it now");
                forceStopDatabaseExecutor(executor);
            }
        } catch (TimeoutException e) {
            getLogger().warning("Database worker did not flush within 20 seconds; stopping it now");
            forceStopDatabaseExecutor(executor);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Final database flush failed", e);
            forceStopDatabaseExecutor(executor);
        }
    }

    private void forceStopDatabaseExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void flushPendingStatistics() {
        for (int attempt = 0; attempt < 2; attempt++) {
            Map<String, Map<String, BlockStatCache.BlockStat>> blockStats = BlockStatCache.getAndClearStats();
            Map<String, KillStatCache.KillStat> killStats = KillStatCache.getAndClear();

            boolean blocksSaved = blockStats.isEmpty() || factionDatabase.saveOrUpdateBlockStatsBatch(blockStats);
            boolean killsSaved = killStats.isEmpty() || factionDatabase.saveKillStatsBatch(killStats);
            if (!blocksSaved) {
                BlockStatCache.merge(blockStats);
            }
            if (!killsSaved) {
                KillStatCache.merge(killStats);
            }
            if (blocksSaved && killsSaved) {
                return;
            }
        }
        getLogger().warning("Some pending faction statistics could not be flushed before shutdown");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LocalizationManager getLocalizationManager() {
        return localizationManager;
    }

    public static Factions getInstance() {
        return instance;
    }

    public FactionApiClient getApiClient() {
        return apiClient;
    }

    public FactionUtils getFactionUtils() {
        return factionUtils;
    }

    public FactionDatabase getFactionDatabase() {
        return factionDatabase;
    }
}
