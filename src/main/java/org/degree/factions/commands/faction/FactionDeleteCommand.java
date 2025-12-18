package org.degree.factions.commands.faction;

import org.bukkit.command.CommandSender;
import org.degree.factions.commands.AbstractCommand;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class FactionDeleteCommand extends AbstractCommand {
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("faction.admin.delete")) {
            sender.sendMessage(localization.getMessage("messages.no_permission"));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(localization.getMessage("messages.usage_faction_delete"));
            return;
        }

        String faction = args[0];
        try {
            if (!factionDatabase.factionExists(faction)) {
                sender.sendMessage(localization.getMessage("messages.faction_not_found"));
                return;
            }
            for (Map<String, String> member : factionDatabase.getMemberNameUuidPairsOfFaction(faction)) {
                String uuid = member.get("uuid");
                if (uuid != null) {
                    FactionCache.setFaction(uuid, null);
                }
            }
            FactionCache.removeFactionColor(faction);
            factionDatabase.deleteFaction(faction);
            sender.sendMessage(localization.getMessage(
                    "messages.faction_deleted_by_admin",
                    Map.of("factionName", faction)
            ));
        } catch (SQLException e) {
            sender.sendMessage(localization.getMessage("messages.error_deleting_faction"));
            e.printStackTrace();
        }
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
