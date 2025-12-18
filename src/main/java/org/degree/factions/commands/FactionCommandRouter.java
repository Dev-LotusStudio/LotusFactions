package org.degree.factions.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.Factions;
import org.degree.factions.commands.faction.*;
import org.degree.factions.utils.LocalizationManager;

import java.util.*;
import java.util.Arrays;

public class FactionCommandRouter extends AbstractCommand {
    private final Map<String, AbstractCommand> subCommands = new HashMap<>();
    private final LocalizationManager localization = Factions.getInstance().getLocalizationManager();

    public FactionCommandRouter() {
        subCommands.put("invite", new FactionInviteCommand());
        subCommands.put("remove", new FactionRemoveCommand());
        subCommands.put("create", new FactionCreateCommand());
        subCommands.put("accept", new FactionAcceptCommand());
        subCommands.put("leave", new FactionLeaveCommand());
        subCommands.put("transfer", new FactionTransferCommand());
        subCommands.put("dissolve",  new FactionDissolveCommand());
        subCommands.put("delete",    new FactionDeleteCommand());
        subCommands.put("stats",    new FactionStatsCommand());
        subCommands.put("update",    new FactionUpdateCommand());
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }
        Player player = (Player) sender;

        if (!player.hasPermission("faction.usage")) {
            player.sendMessage(localization.getMessage("messages.no_permission"));
            return;
        }

        if (args.length == 0) {
            localization.sendMessageToPlayer(player, "messages.unknown_subcommand");
            return;
        }

        String sub = args[0].toLowerCase();
        AbstractCommand cmd = subCommands.get(sub);
        if (cmd == null) {
            localization.sendMessageToPlayer(player, "messages.unknown_subcommand");
            return;
        }

        String node = "faction." + sub;
        if (!player.hasPermission(node)) {
            player.sendMessage(localization.getMessage("messages.no_permission"));
            return;
        }

        cmd.execute(sender, label, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> allowed = new ArrayList<>();
            for (String sub : subCommands.keySet()) {
                if (sender.hasPermission("faction.usage") || sender.hasPermission("faction." + sub)) {
                    allowed.add(sub);
                }
            }
            String prefix = args[0].toLowerCase();
            List<String> filtered = new ArrayList<>();
            for (String sub : allowed) {
                if (sub.startsWith(prefix)) {
                    filtered.add(sub);
                }
            }

            return filtered;
        } else if (args.length > 1) {
            AbstractCommand sub = subCommands.get(args[0].toLowerCase());
            if (sub != null) {
                return sub.complete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return Collections.emptyList();
    }
}
