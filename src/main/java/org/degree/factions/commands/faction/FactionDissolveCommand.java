package org.degree.factions.commands.faction;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;
import java.util.*;

public class FactionDissolveCommand extends AbstractCommand {
    private final Map<UUID, Long> pendingConfirms = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30_000;

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        try {
            if (!factionDatabase.isLeader(uuid.toString())) {
                localization.sendMessageToPlayer(player, "messages.only_leader_can_dissolve");
                return;
            }

            String faction = factionDatabase.getFactionNameByLeader(uuid.toString());
            if (faction == null) {
                localization.sendMessageToPlayer(player, "messages.you_have_no_faction");
                return;
            }

            long now = System.currentTimeMillis();
            if (pendingConfirms.containsKey(uuid)) {
                long expire = pendingConfirms.get(uuid);
                if (now <= expire) {
                    for (Map<String, String> member : factionDatabase.getMemberNameUuidPairsOfFaction(faction)) {
                        String memberUuid = member.get("uuid");
                        if (memberUuid != null) {
                            FactionCache.setFaction(memberUuid, null);
                        }
                    }
                    FactionCache.removeFactionColor(faction);
                    factionDatabase.deleteFaction(faction);
                    localization.sendMessageToPlayer(player, "messages.faction_dissolved", Map.of("factionName", faction));
                    pendingConfirms.remove(uuid);
                    return;
                } else {
                    pendingConfirms.remove(uuid);
                }
            }

            pendingConfirms.put(uuid, now + CONFIRM_TIMEOUT_MS);
            localization.sendMessageToPlayer(player, "messages.confirm_dissolve");
            localization.sendMessageToPlayer(player, "messages.type_command_again_to_dissolve_confirm");
        } catch (SQLException e) {
            localization.sendMessageToPlayer(player, "messages.error_deleting_faction");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}
