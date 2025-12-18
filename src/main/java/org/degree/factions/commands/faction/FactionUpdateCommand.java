package org.degree.factions.commands.faction;

import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public class FactionUpdateCommand extends AbstractCommand {
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) return;
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§cУкажите имя фракции!");
            return;
        }

        // Собираем имя фракции из всех аргументов
        String factionName = String.join(" ", args);

        apiClient.postFactionFromDatabase(factionName);

        String encoded = URLEncoder.encode(factionName, StandardCharsets.UTF_8);
        TextComponent link = factionUtils.getFactionLink(encoded);
        player.spigot().sendMessage(link);
    }


    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            try {
                List<String> all = factionDatabase.getAllFactionNames();
                String prefix = args[0].toLowerCase();
                return all.stream()
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .toList();
            } catch (SQLException e) {
                return List.of();
            }
        }
        return List.of();
    }
}
