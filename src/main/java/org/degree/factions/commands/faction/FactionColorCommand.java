package org.degree.factions.commands.faction;

import org.bukkit.command.CommandSender;
import org.degree.factions.commands.AbstractCommand;
import org.degree.factions.utils.FactionCache;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class FactionColorCommand extends AbstractCommand {
    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(localization.getMessage("messages.usage_faction_color"));
            return;
        }

        String colorHex = args[args.length - 1].trim();
        String factionName = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1)).trim();

        if (factionName.isEmpty() || colorHex.isEmpty()) {
            sender.sendMessage(localization.getMessage("messages.usage_faction_color"));
            return;
        }

        if (!colorHex.matches("^#[0-9A-Fa-f]{6}$")) {
            sender.sendMessage(localization.getMessage("messages.invalid_color_format"));
            return;
        }

        try {
            if (!factionDatabase.factionExists(factionName)) {
                sender.sendMessage(localization.getMessage("messages.faction_not_found"));
                return;
            }

            factionDatabase.updateFactionColor(factionName, colorHex);
            FactionCache.setFactionColor(factionName, colorHex);

            sender.sendMessage(localization.getMessage(
                    "messages.faction_color_updated",
                    java.util.Map.of("factionName", factionName, "color", colorHex)
            ));
        } catch (SQLException e) {
            sender.sendMessage(localization.getMessage("messages.error_updating_faction_color"));
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
