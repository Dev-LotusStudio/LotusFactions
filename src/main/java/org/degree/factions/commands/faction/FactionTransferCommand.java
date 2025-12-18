package org.degree.factions.commands.faction;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FactionTransferCommand extends AbstractCommand {
    private final Map<UUID, String> pendingTransfers = new HashMap<>();

    public FactionTransferCommand() {}

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }

        Player currentLeader = (Player) sender;
        UUID leaderUUID = currentLeader.getUniqueId();

        if (args.length < 1) {
            localization.sendMessageToPlayer(currentLeader, "messages.usage_faction_transfer");
            return;
        }

        Player newLeader = Bukkit.getPlayerExact(args[0]);
        if (newLeader == null) {
            localization.sendMessageToPlayer(currentLeader,
                    "messages.player_not_online",
                    Map.of("playerName", args[0])
            );
            return;
        }

        try {
            String factionName = factionDatabase.getFactionNameForPlayer(leaderUUID.toString());
            if (factionName == null || !factionDatabase.isLeader(leaderUUID.toString())) {
                localization.sendMessageToPlayer(currentLeader, "messages.not_leader_of_any_faction");
                return;
            }

            if (!factionDatabase.isMemberOfFaction(factionName, newLeader.getName())) {
                localization.sendMessageToPlayer(currentLeader, "messages.player_not_in_your_faction");
                return;
            }

            String requestedName = pendingTransfers.get(leaderUUID);
            if (requestedName != null && requestedName.equals(newLeader.getName())) {
                factionDatabase.transferLeadership(factionName, newLeader.getUniqueId().toString());
                localization.sendMessageToPlayer(currentLeader,
                        "messages.leadership_transferred_successfully",
                        Map.of("newLeaderName", newLeader.getName())
                );
                localization.sendMessageToPlayer(newLeader,
                        "messages.now_leader_of_faction",
                        Map.of("factionName", factionName)
                );
                pendingTransfers.remove(leaderUUID);
            } else {
                pendingTransfers.put(leaderUUID, newLeader.getName());
                localization.sendMessageToPlayer(currentLeader,
                        "messages.confirm_transfer_leadership",
                        Map.of("newLeaderName", newLeader.getName())
                );
                localization.sendMessageToPlayer(currentLeader,
                        "messages.type_command_again_to_confirm",
                        Map.of("newLeaderName", newLeader.getName())
                );
            }

        } catch (SQLException e) {
            localization.sendMessageToPlayer(currentLeader, "messages.error_transferring_leadership");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            Player player = (Player) sender;
            try {
                String factionName = factionDatabase.getFactionNameForPlayer(player.getUniqueId().toString());
                if (factionName != null) {
                    List<String> members = factionDatabase.getMemberNamesOfFaction(factionName);
                    members.remove(player.getName());
                    return members;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return Collections.emptyList();
    }
}
