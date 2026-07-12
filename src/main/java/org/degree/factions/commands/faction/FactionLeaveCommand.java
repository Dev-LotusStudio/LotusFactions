package org.degree.factions.commands.faction;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class FactionLeaveCommand extends AbstractCommand {

    public FactionLeaveCommand() {}

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }

        Player player = (Player) sender;
        String playerUUID = player.getUniqueId().toString();

        try {
            if (factionDatabase.isLeader(playerUUID)) {
                localization.sendMessageToPlayer(player, "messages.cannot_leave_as_leader");
                return;
            }

            String factionName = factionDatabase.getFactionNameForPlayer(playerUUID);
            factionDatabase.removeMemberFromFaction(playerUUID);

            FactionCache.setFaction(playerUUID, null);
            plugin.runDatabaseTask(() -> factionDatabase.logSessionEnd(playerUUID));

            localization.sendMessageToPlayer(player, "messages.faction_left_successfully");
            if (factionName != null) {
                apiClient.postFactionFromDatabase(factionName);
            }

        } catch (SQLException e) {
            localization.sendMessageToPlayer(player, "messages.error_leaving_faction");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
