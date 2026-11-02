package org.degree.factions.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.utils.SchedulerCompat;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class FactionChatCommand extends AbstractCommand {

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(localization.getMessage("messages.usage_fchat"));
            return;
        }

        Player player = (Player) sender;
        try {
            String senderUUID = player.getUniqueId().toString();
            String factionName = factionDatabase.getFactionNameForPlayer(senderUUID);

            if (factionName == null) {
                localization.sendMessageToPlayer(player, "messages.not_in_faction");
                return;
            }

            List<String> memberNames = factionDatabase.getMemberNamesOfFaction(factionName);
            String formatted = localization.getMessage("messages.fchat_prefix",
                    Map.of("factionName", factionName, "playerName", player.getName()))
                    + String.join(" ", args);

            for (String name : memberNames) {
                Player member = Bukkit.getPlayerExact(name);
                if (member != null && member.isOnline()) {
                    SchedulerCompat.runEntity(plugin, member, () -> member.sendMessage(formatted));
                }
            }
        } catch (SQLException e) {
            localization.sendMessageToPlayer(player, "messages.error_faction_chat");
            plugin.getLogger().log(Level.WARNING, "Failed to send faction chat message", e);
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
