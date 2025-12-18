package org.degree.factions.commands.faction;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import net.md_5.bungee.api.chat.TextComponent;

public class FactionStatsCommand extends AbstractCommand {
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }
        Player player = (Player) sender;

        try {
            String factionName = factionDatabase.getFactionNameForPlayer(player.getUniqueId().toString());
            if (factionName == null) {
                localization.sendMessageToPlayer(player, "messages.not_in_faction");
                return;
            }

            apiClient.postFactionFromDatabase(factionName);

            String encoded = URLEncoder.encode(factionName, StandardCharsets.UTF_8);
            TextComponent link = factionUtils.getFactionLink(encoded);
            player.spigot().sendMessage(link);

        } catch (SQLException e) {
            e.printStackTrace();
            localization.sendMessageToPlayer(player, "messages.error_fetching_faction");
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }

}
