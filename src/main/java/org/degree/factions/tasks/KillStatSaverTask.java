package org.degree.factions.tasks;

import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.utils.KillStatCache;

import java.util.Map;

public class KillStatSaverTask implements Runnable {
    private final Factions plugin;
    private final FactionDatabase factionDatabase;

    public KillStatSaverTask(Factions plugin, FactionDatabase factionDatabase) {
        this.plugin = plugin;
        this.factionDatabase = factionDatabase;
    }

    @Override
    public void run() {
        plugin.runDatabaseTask(() -> {
            Map<String, KillStatCache.KillStat> snapshot = KillStatCache.getAndClear();
            if (!snapshot.isEmpty() && !factionDatabase.saveKillStatsBatch(snapshot)) {
                KillStatCache.merge(snapshot);
            }
        });
    }
}
