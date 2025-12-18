package org.degree.factions.commands.faction;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;
import org.bukkit.Bukkit;
import org.degree.factions.Factions;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FactionCreateCommand extends AbstractCommand {
    private static final String HEX_REGEX = "^#[0-9A-Fa-f]{6}$";

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            localization.sendMessageToPlayer(player, "messages.usage_faction_create");
            return;
        }

        String colorHex = "#FFFFFF";
        String factionName;

        if (args.length >= 2 && args[args.length - 1].matches(HEX_REGEX)) {
            colorHex = args[args.length - 1];
            factionName = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1));
        } else {
            factionName = String.join(" ", args);
        }

        // Убираем лишние пробелы по краям
        factionName = factionName.trim();

        String leaderUUID = player.getUniqueId().toString();
        String leaderName = player.getName();

        try {
            if (factionDatabase.factionExists(factionName)) {
                localization.sendMessageToPlayer(player, "messages.faction_already_exists");
                return;
            }

            factionDatabase.createFaction(factionName, leaderUUID, leaderName, colorHex);
            factionDatabase.addMemberToFaction(factionName, leaderUUID, leaderName, "LEADER");

            FactionCache.setFaction(leaderUUID, factionName);
            FactionCache.setFactionColor(factionName, colorHex);

            final String sessionFactionName = factionName;
            final String sessionLeaderUuid = leaderUUID;
            Bukkit.getScheduler().runTaskAsynchronously(Factions.getInstance(), () -> {
                factionDatabase.logSessionEnd(sessionLeaderUuid);
                factionDatabase.logSessionStart(sessionFactionName, sessionLeaderUuid);
            });

            localization.sendMessageToPlayer(
                    player,
                    "messages.faction_created_successfully",
                    Map.of("factionName", factionName, "color", colorHex)
            );
        } catch (SQLException e) {
            localization.sendMessageToPlayer(player, "messages.error_creating_faction");
            e.printStackTrace();
        }
    }


    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("<FactionName>");
        } else if (args.length == 2) {
            return Collections.singletonList("<hexColor>");
        }
        return Collections.emptyList();
    }
}
