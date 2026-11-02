package org.degree.factions.commands;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FactionRoleSyncCommand extends AbstractCommand {
    private static final List<String> OPTIONS = List.of("enable", "disable", "status");

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("faction.rolesync")) {
            sender.sendMessage(localization.getMessage("messages.no_permission"));
            return;
        }

        if (args.length != 2) {
            sender.sendMessage(localization.getMessage("messages.usage_rolesync", Map.of("label", label)));
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String playerName = args[1].trim();
        if (!OPTIONS.contains(action) || playerName.isEmpty()) {
            sender.sendMessage(localization.getMessage("messages.usage_rolesync", Map.of("label", label)));
            return;
        }

        try {
            String factionName = factionDatabase.getFactionNameForPlayerName(playerName);
            if (factionName == null) {
                sender.sendMessage(localization.getMessage("messages.player_not_in_a_faction", Map.of("playerName", playerName)));
                return;
            }

            if ("status".equals(action)) {
                boolean enabled = factionDatabase.loadFaction(factionName).isDiscordRoleSyncEnabled();
                sendState(sender, factionName, enabled);
                return;
            }

            boolean enabled = "enable".equals(action);
            factionDatabase.setDiscordRoleSyncEnabled(factionName, enabled);
            sendState(sender, factionName, enabled);

            if (!config.isIngestEnabled()) {
                sender.sendMessage(localization.getMessage("messages.rolesync_ingest_disabled"));
                return;
            }

            apiClient.postFactionFromDatabase(factionName);
            sender.sendMessage(localization.getMessage("messages.rolesync_queued"));
        } catch (Exception e) {
            sender.sendMessage(localization.getMessage("messages.rolesync_failed",
                    Map.of("error", String.valueOf(e.getMessage()))));
            plugin.getLogger().warning("[FactionRoleSyncCommand] " + e.getMessage());
        }
    }

    private void sendState(CommandSender sender, String factionName, boolean enabled) {
        String state = localization.getMessage(enabled ? "messages.rolesync_enabled" : "messages.rolesync_disabled");
        sender.sendMessage(localization.getMessage("messages.rolesync_state",
                Map.of("factionName", factionName, "state", state)));
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
