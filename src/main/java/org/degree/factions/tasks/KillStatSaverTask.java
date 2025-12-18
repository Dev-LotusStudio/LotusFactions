package org.degree.factions.tasks;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.utils.KillStatCache;

import java.util.Map;

public class KillStatSaverTask extends BukkitRunnable {
    private final Factions plugin;
    private final FactionDatabase factionDatabase;

    public KillStatSaverTask(Factions plugin, FactionDatabase factionDatabase) {
        this.plugin = plugin;
        this.factionDatabase = factionDatabase;
    }

    @Override
    public void run() {
        Map<String, KillStatCache.KillStat> snapshot = KillStatCache.getAndClear();
        if (snapshot.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> factionDatabase.saveKillStatsBatch(snapshot));
    }
}
