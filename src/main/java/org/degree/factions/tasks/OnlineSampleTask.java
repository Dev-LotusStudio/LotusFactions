package org.degree.factions.tasks;

import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.utils.FactionCache;
import org.degree.factions.utils.OnlinePlayerCache;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class OnlineSampleTask implements Runnable {
    private final Factions plugin;
    private final FactionDatabase factionDatabase;
    public OnlineSampleTask(Factions plugin, FactionDatabase factionDatabase) {
        this.plugin = plugin;
        this.factionDatabase = factionDatabase;
    }

    @Override
    public void run() {
        plugin.runDatabaseTask(() -> {
            try {
                long tsMs = System.currentTimeMillis();
                Map<String, Integer> onlineByFaction = new HashMap<>();
                for (String uuid : OnlinePlayerCache.snapshot().keySet()) {
                    String faction = FactionCache.getFaction(uuid);
                    if (faction == null) continue;
                    onlineByFaction.merge(faction, 1, Integer::sum);
                }
                var factionNames = factionDatabase.getAllFactionNames();
                if (!factionDatabase.insertOnlineSamples(tsMs, onlineByFaction, factionNames)) {
                    plugin.getLogger().warning("Failed to store online samples");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to store online samples: " + e.getMessage());
            }
        });
    }
}
