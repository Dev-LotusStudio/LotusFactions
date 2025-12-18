package org.degree.factions;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.degree.factions.commands.FactionChatCommand;
import org.degree.factions.commands.FactionCommandRouter;
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
import org.degree.factions.utils.LocalizationManager;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public final class Factions extends JavaPlugin {
    private static Factions instance;
    private ConfigManager configManager;
    private LocalizationManager localizationManager;
    private FactionApiClient apiClient;
    private FactionUtils factionUtils;
    private Database database;
    private FactionDatabase factionDatabase;

    @Override
    public void onEnable() {
        int pluginId = 25785;
        new Metrics(this, pluginId);

        instance = this;
        database = new Database(this);
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

        getServer().getPluginManager().registerEvents(new SessionListener(this, factionDatabase), this);
        getServer().getPluginManager().registerEvents(new BlockStatListener(factionDatabase), this);
        getServer().getPluginManager().registerEvents(new KillStatListener(), this);

        new BlockStatSaverTask(factionDatabase).runTaskTimer(this, 20L * 60, 20L * 60);
        new KillStatSaverTask(this, factionDatabase).runTaskTimer(this, 20L * 60, 20L * 60);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FactionPlaceholder(this, factionDatabase).register();
            getLogger().info("Registered FactionPlaceholder for PAPI");
        } else {
            getLogger().warning("PlaceholderAPI not found; FactionPlaceholder not registered");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
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
            new OnlineSampleTask(this, factionDatabase).runTaskTimer(this, 20L * 5, 20L * sampleSeconds);

            int intervalSeconds = Math.max(30, configManager.getIngestIntervalSeconds());
            new BukkitRunnable() {
                @Override
                public void run() {
                    apiClient.postAllFactionsFromDatabase();
                }
            }.runTaskTimer(this, 20L * 10, 20L * intervalSeconds);

            getLogger().info("Ingest enabled: sampling online every " + sampleSeconds + "s, sending snapshot every " + intervalSeconds + "s");
        }
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.closeConnection();
        }
    }

    public class BlockStatSaverTask extends BukkitRunnable {
        private final FactionDatabase db;

        public BlockStatSaverTask(FactionDatabase db) {
            this.db = db;
        }

        @Override
        public void run() {
            Map<String, Map<String, BlockStatCache.BlockStat>> snapshot = BlockStatCache.getAndClearStats();
            if (snapshot.isEmpty()) return;
            Bukkit.getScheduler().runTaskAsynchronously(Factions.this, () -> db.saveOrUpdateBlockStatsBatch(snapshot));
        }
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
