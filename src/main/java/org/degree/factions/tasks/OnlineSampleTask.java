package org.degree.factions.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnlineSampleTask extends BukkitRunnable {
    private final Factions plugin;
    private final FactionDatabase factionDatabase;
    private volatile List<String> cachedFactionNames = List.of();
    private volatile long lastRefreshMs = 0L;
    private static final long REFRESH_INTERVAL_MS = 5 * 60_000L;

    public OnlineSampleTask(Factions plugin, FactionDatabase factionDatabase) {
        this.plugin = plugin;
        this.factionDatabase = factionDatabase;
    }

    @Override
    public void run() {
        long tsMs = System.currentTimeMillis();

        Map<String, Integer> onlineByFaction = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String faction = FactionCache.getFaction(player.getUniqueId().toString());
            if (faction == null) continue;
            onlineByFaction.merge(faction, 1, Integer::sum);
        }

        Map<String, Integer> snapshot = new HashMap<>(onlineByFaction);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long nowMs = System.currentTimeMillis();
                List<String> factionNames = cachedFactionNames;
                if (factionNames.isEmpty() || nowMs - lastRefreshMs >= REFRESH_INTERVAL_MS) {
                    factionNames = factionDatabase.getAllFactionNames();
                    cachedFactionNames = factionNames;
                    lastRefreshMs = nowMs;
                }
                factionDatabase.insertOnlineSamples(tsMs, snapshot, factionNames);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to store online samples: " + e.getMessage());
            }
        });
    }
}
