package org.degree.factions.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.models.Faction;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;

public class SessionListener implements Listener {
    private final FactionDatabase db;
    private final Factions plugin;

    public SessionListener(Factions plugin, FactionDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String uuid = e.getPlayer().getUniqueId().toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String faction = db.getFactionNameForPlayer(uuid);
                FactionCache.setFaction(uuid, faction);
                if (faction != null) {
                    Faction loaded = db.loadFaction(faction);
                    if (loaded != null) {
                        FactionCache.setFactionColor(faction, loaded.getColorHex());
                    }
                    db.logSessionStart(faction, uuid);
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Error fetching faction for join: " + ex.getMessage());
            } catch (Exception ex) {
                plugin.getLogger().warning("Join handler failed: " + ex.getMessage());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String uuid = e.getPlayer().getUniqueId().toString();
        FactionCache.setFaction(uuid, null);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> db.logSessionEnd(uuid));
    }
}
