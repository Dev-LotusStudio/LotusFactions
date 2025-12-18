package org.degree.factions;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.models.Faction;
import org.degree.factions.utils.FactionCache;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.logging.Level;

public class FactionPlaceholder extends PlaceholderExpansion {
    private final FactionDatabase factionDatabase;
    private final Factions plugin;

    public FactionPlaceholder(Factions plugin, FactionDatabase factionDatabase) {
        this.plugin = plugin;
        this.factionDatabase = factionDatabase;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "faction";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist(){
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";

        String uuid = player.getUniqueId().toString();
        String factionName = FactionCache.getFaction(uuid);

        if (factionName == null && Bukkit.isPrimaryThread()) {
            try {
                factionName = factionDatabase.getFactionNameForPlayer(uuid);
                FactionCache.setFaction(uuid, factionName);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to fetch faction for placeholder: " + player.getName(), e);
                return "";
            }
        }

        if (factionName == null || factionName.isEmpty()) return "";

        switch (identifier) {
            case "prefix_tab":
                return factionName;
            case "prefix": {
                String colorHex = FactionCache.getFactionColor(factionName);
                if (colorHex == null && Bukkit.isPrimaryThread()) {
                    try {
                        Faction faction = factionDatabase.loadFaction(factionName);
                        if (faction != null) {
                            colorHex = faction.getColorHex();
                            FactionCache.setFactionColor(factionName, colorHex);
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to fetch faction color for placeholder: " + factionName, e);
                    }
                }
                if (colorHex == null) colorHex = "#FFFFFF";
                try {
                    return ChatColor.of(colorHex) + factionName + " " + ChatColor.RESET;
                } catch (Exception e) {
                    return factionName + " ";
                }
            }
            default:
                return "";
        }
    }


}
