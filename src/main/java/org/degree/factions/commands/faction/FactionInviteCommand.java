// src/main/java/org/degree/factions/commands/faction/FactionInviteCommand.java
package org.degree.factions.commands.faction;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.degree.factions.commands.AbstractCommand;
import org.degree.factions.utils.OnlinePlayerCache;
import org.degree.factions.utils.SchedulerCompat;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FactionInviteCommand extends AbstractCommand {

    private final Map<UUID, Long> lastInvite = new ConcurrentHashMap<>();

    public FactionInviteCommand() {
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(localization.getMessage("messages.only_players_can_use"));
            return;
        }
        Player inviter = (Player) sender;
        UUID inviterId = inviter.getUniqueId();

        long cooldownMs = config.getInviteCooldownSeconds() * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastInvite.get(inviterId);
        if (last != null && now - last < cooldownMs) {
            long secsLeft = (cooldownMs - (now - last)) / 1000;
            inviter.sendMessage(localization.getMessage(
                    "messages.invite_cooldown",
                    Map.of("time", String.valueOf(secsLeft))
            ));
            return;
        }

        String factionName;
        try {
            factionName = factionDatabase.getFactionNameForPlayer(inviterId.toString());
            if (factionName == null) {
                localization.sendMessageToPlayer(inviter, "messages.not_in_faction");
                return;
            }
        } catch (SQLException e) {
            localization.sendMessageToPlayer(inviter, "messages.error_fetching_faction");
            e.printStackTrace();
            return;
        }

        if (args.length < 1) {
            localization.sendMessageToPlayer(inviter, "messages.usage_faction_invite");
            return;
        }

        Player invitee = Bukkit.getPlayer(args[0]);
        if (invitee == null || !invitee.isOnline()) {
            localization.sendMessageToPlayer(inviter,
                    "messages.player_not_online",
                    Map.of("playerName", args[0])
            );
            return;
        }

        try {
            factionDatabase.addInvite(
                    factionName,
                    inviterId.toString(),
                    invitee.getUniqueId().toString()
            );
            lastInvite.put(inviterId, now);

            String inviterName = inviter.getName();
            SchedulerCompat.runEntity(plugin, invitee, () ->
                    localization.sendMessageToPlayer(invitee,
                            "messages.invited_to_faction",
                            Map.of("inviterName", inviterName)
                    ));
            localization.sendMessageToPlayer(inviter,
                    "messages.invitation_sent",
                    Map.of("inviteeName", invitee.getName())
            );
        } catch (SQLException e) {
            localization.sendMessageToPlayer(inviter, "messages.error_sending_invite");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            String senderName = sender.getName();
            for (String name : OnlinePlayerCache.names()) {
                if (!name.equals(senderName) && name.toLowerCase().startsWith(prefix)) {
                    suggestions.add(name);
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }

}
