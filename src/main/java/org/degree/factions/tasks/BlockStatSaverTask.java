package org.degree.factions.tasks;

import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.utils.BlockStatCache;

import java.util.Map;

public class BlockStatSaverTask implements Runnable {
    private final Factions plugin;
    private final FactionDatabase factionDatabase;

    public BlockStatSaverTask(Factions plugin, FactionDatabase factionDatabase) {
        this.plugin = plugin;
        this.factionDatabase = factionDatabase;
    }

    @Override
    public void run() {
        plugin.runDatabaseTask(() -> {
            Map<String, Map<String, BlockStatCache.BlockStat>> snapshot = BlockStatCache.getAndClearStats();
            if (!snapshot.isEmpty() && !factionDatabase.saveOrUpdateBlockStatsBatch(snapshot)) {
                BlockStatCache.merge(snapshot);
            }
        });
    }
}
