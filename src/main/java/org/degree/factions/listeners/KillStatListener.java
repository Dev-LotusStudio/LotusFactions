package org.degree.factions.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;
import org.degree.factions.utils.FactionCache;
import org.degree.factions.utils.KillStatCache;

public class KillStatListener implements Listener {
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        String killerUuid = killer.getUniqueId().toString();
        String faction = FactionCache.getFaction(killerUuid);
        if (faction == null) return;

        KillStatCache.incrementKill(killerUuid, faction);
    }
}
