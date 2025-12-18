package org.degree.factions.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.degree.factions.utils.BlockStatCache;
import org.degree.factions.database.FactionDatabase;

import java.util.Collections;
import java.util.Map;

public class BlockStatQuitListener implements Listener {
    private final FactionDatabase db;

    public BlockStatQuitListener(FactionDatabase db) {
        this.db = db;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        Map<String, BlockStatCache.BlockStat> stats = BlockStatCache.getAndClearStats(uuid);
        if (stats != null && !stats.isEmpty()) {
            db.saveOrUpdateBlockStatsBatch(Collections.singletonMap(uuid, stats));
        }
    }
}
