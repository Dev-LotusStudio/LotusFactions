package org.degree.factions.commands.faction;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;
import org.degree.factions.utils.FactionCache;
import org.degree.factions.utils.SchedulerCompat;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FactionRemoveCommand extends AbstractCommand {

    public FactionRemoveCommand() {}

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(localization.getMessage("messages.usage_faction_remove"));
            return;
        }

        Player leader = (Player) sender;
        String leaderUUID = leader.getUniqueId().toString();

        try {
            if (!factionDatabase.isLeader(leaderUUID)) {
                localization.sendMessageToPlayer(leader, "messages.not_leader");
                return;
            }

            String memberName = args[0];
            if (leader.getName().equalsIgnoreCase(memberName)) {
                localization.sendMessageToPlayer(leader, "messages.cannot_remove_self");
                return;
            }

            String factionName = factionDatabase.getFactionNameByLeader(leaderUUID);
            if (factionName == null) {
                localization.sendMessageToPlayer(leader, "messages.you_have_no_faction");
                return;
            }

            if (!factionDatabase.isMemberOfFaction(factionName, memberName)) {
                localization.sendMessageToPlayer(leader, "messages.player_not_in_your_faction");
                return;
            }

            OfflinePlayer member = org.bukkit.Bukkit.getOfflinePlayer(memberName);
            String memberUUID = member.getUniqueId().toString();

            if (factionDatabase.isLeader(memberUUID)) {
                localization.sendMessageToPlayer(leader, "messages.cannot_remove_other_leader");
                return;
            }

            factionDatabase.removeMemberFromFaction(memberUUID);
            FactionCache.setFaction(memberUUID, null);
            plugin.runDatabaseTask(() -> factionDatabase.logSessionEnd(memberUUID));
            localization.sendMessageToPlayer(leader, "messages.member_removed", Map.of("memberName", memberName));
            if (member.isOnline()) {
                Player onlineMember = (Player) member;
                SchedulerCompat.runEntity(plugin, onlineMember,
                        () -> localization.sendMessageToPlayer(onlineMember, "messages.kicked_from_faction",
                                Map.of("factionName", factionName)));
            }
            apiClient.postFactionFromDatabase(factionName);
        } catch (SQLException e) {
            localization.sendMessageToPlayer(leader, "messages.error_removing_member");
            e.printStackTrace();
        }
    }


    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        // Показываем подсказки: список участников вашей фракции
        if (sender instanceof Player && args.length == 1) {
            try {
                Player p = (Player) sender;
                String uuid = p.getUniqueId().toString();
                if (!factionDatabase.isLeader(uuid)) return Collections.emptyList();
                String factionName = factionDatabase.getFactionNameByLeader(uuid);
                if (factionName == null) return Collections.emptyList();
                List<String> names = factionDatabase.getMemberNamesOfFaction(factionName);
                names.remove(p.getName()); // не предлагать самого себя
                return names;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return Collections.emptyList();
    }
}
