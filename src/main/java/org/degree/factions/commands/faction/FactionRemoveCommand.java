package org.degree.factions.commands.faction;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.Factions;
import org.degree.factions.commands.AbstractCommand;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

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
                leader.sendMessage("§cВы не можете удалить самого себя. Для выхода используйте /faction leave.");
                return;
            }

            String factionName = factionDatabase.getFactionNameByLeader(leaderUUID);
            if (factionName == null) {
                leader.sendMessage("§cУ вас нет фракции.");
                return;
            }

            if (!factionDatabase.isMemberOfFaction(factionName, memberName)) {
                leader.sendMessage("§cЭтот игрок не состоит в вашей фракции.");
                return;
            }

            OfflinePlayer member = org.bukkit.Bukkit.getOfflinePlayer(memberName);
            String memberUUID = member.getUniqueId().toString();

            if (factionDatabase.isLeader(memberUUID)) {
                leader.sendMessage("§cВы не можете удалить другого лидера.");
                return;
            }

            factionDatabase.removeMemberFromFaction(memberUUID);
            FactionCache.setFaction(memberUUID, null);
            Bukkit.getScheduler().runTaskAsynchronously(Factions.getInstance(), () -> factionDatabase.logSessionEnd(memberUUID));
            leader.sendMessage("§aИгрок " + memberName + " был удален из фракции.");
            if (member.isOnline()) {
                ((Player) member).sendMessage("§cВы были исключены из фракции " + factionName + ".");
            }
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
