package org.degree.factions;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.degree.factions.utils.FactionCache;
import org.jetbrains.annotations.NotNull;

public class FactionPlaceholder extends PlaceholderExpansion {
    private final Factions plugin;

    public FactionPlaceholder(Factions plugin) {
        this.plugin = plugin;
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

        if (factionName == null || factionName.isEmpty()) return "";

        switch (identifier) {
            case "prefix_tab":
                return factionName;
            case "prefix": {
                String colorHex = FactionCache.getFactionColor(factionName);
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
