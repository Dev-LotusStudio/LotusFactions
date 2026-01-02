package org.degree.factions.commands.faction;

import org.bukkit.command.CommandSender;
import org.degree.factions.commands.AbstractCommand;
import org.degree.factions.models.Faction;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FactionRenameCommand extends AbstractCommand {
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(localization.getMessage("messages.usage_faction_rename"));
            return;
        }

        String joined = String.join(" ", args).trim();
        String oldName = null;
        String newName = null;
        String[] separators = {" | ", " -> ", " => "};
        for (String separator : separators) {
            int idx = joined.indexOf(separator);
            if (idx > 0) {
                oldName = joined.substring(0, idx).trim();
                newName = joined.substring(idx + separator.length()).trim();
                break;
            }
        }

        if (oldName == null) {
            oldName = args[0].trim();
            newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        }

        if (oldName.isEmpty() || newName.isEmpty()) {
            sender.sendMessage(localization.getMessage("messages.usage_faction_rename"));
            return;
        }

        try {
            if (!factionDatabase.factionExists(oldName)) {
                sender.sendMessage(localization.getMessage("messages.faction_not_found"));
                return;
            }
            if (factionDatabase.factionExists(newName)) {
                sender.sendMessage(localization.getMessage("messages.faction_already_exists"));
                return;
            }

            List<Map<String, String>> members = factionDatabase.getMemberNameUuidPairsOfFaction(oldName);
            Faction faction = factionDatabase.loadFaction(oldName);
            String color = faction != null ? faction.getColorHex() : FactionCache.getFactionColor(oldName);

            factionDatabase.renameFaction(oldName, newName);

            for (Map<String, String> member : members) {
                String uuid = member.get("uuid");
                if (uuid != null) {
                    FactionCache.setFaction(uuid, newName);
                }
            }
            if (color != null) {
                FactionCache.setFactionColor(newName, color);
            }
            FactionCache.removeFactionColor(oldName);

            sender.sendMessage(localization.getMessage(
                    "messages.faction_renamed",
                    Map.of("oldName", oldName, "newName", newName)
            ));
        } catch (SQLException e) {
            sender.sendMessage(localization.getMessage("messages.error_renaming_faction"));
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
