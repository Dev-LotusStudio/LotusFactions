package org.degree.factions.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class FactionChatCommand extends AbstractCommand {

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage("§cИспользование: /fchat <сообщение>");
            return;
        }

        Player player = (Player) sender;
        try {
            String senderUUID = player.getUniqueId().toString();
            String factionName = factionDatabase.getFactionNameForPlayer(senderUUID);

            if (factionName == null) {
                player.sendMessage("§cВы не состоите во фракции.");
                return;
            }

            List<String> memberNames = factionDatabase.getMemberNamesOfFaction(factionName);
            String formatted = "§7[§b" + factionName + "§7] §a" + player.getName() + "§f: " + String.join(" ", args);

            for (String name : memberNames) {
                Player member = Bukkit.getPlayerExact(name);
                if (member != null && member.isOnline()) {
                    member.sendMessage(formatted);
                }
            }
        } catch (SQLException e) {
            player.sendMessage("§cОшибка отправки сообщения во фракцию.");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
