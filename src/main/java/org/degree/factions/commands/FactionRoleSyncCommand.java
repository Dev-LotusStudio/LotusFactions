package org.degree.factions.commands;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

public class FactionRoleSyncCommand extends AbstractCommand {
    private static final List<String> OPTIONS = List.of("enable", "disable", "status");

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("faction.rolesync")) {
            sender.sendMessage("You do not have permission to use this command.");
            return;
        }

        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " <enable|disable|status> <player>");
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String playerName = args[1].trim();
        if (!OPTIONS.contains(action) || playerName.isEmpty()) {
            sender.sendMessage("Usage: /" + label + " <enable|disable|status> <player>");
            return;
        }

        try {
            String factionName = factionDatabase.getFactionNameForPlayerName(playerName);
            if (factionName == null) {
                sender.sendMessage("Player " + playerName + " is not in a faction.");
                return;
            }

            if ("status".equals(action)) {
                boolean enabled = factionDatabase.loadFaction(factionName).isDiscordRoleSyncEnabled();
                sender.sendMessage("Discord role sync for faction " + factionName + " is "
                        + (enabled ? "enabled." : "disabled."));
                return;
            }

            boolean enabled = "enable".equals(action);
            factionDatabase.setDiscordRoleSyncEnabled(factionName, enabled);
            sender.sendMessage("Discord role sync for faction " + factionName + " has been "
                    + (enabled ? "enabled." : "disabled."));

            if (!config.isIngestEnabled()) {
                sender.sendMessage("Warning: ingest.enabled is false; the setting was saved but cannot be sent to the backend.");
                return;
            }

            apiClient.postFactionFromDatabase(factionName);
            sender.sendMessage("The faction update was queued for the backend.");
        } catch (Exception e) {
            sender.sendMessage("Failed to update Discord role sync: " + e.getMessage());
            plugin.getLogger().warning("[FactionRoleSyncCommand] " + e.getMessage());
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1 || !sender.hasPermission("faction.rolesync")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return OPTIONS.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
