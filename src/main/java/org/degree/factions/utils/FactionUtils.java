package org.degree.factions.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

public class FactionUtils {
    public String toSlug(String name) {
        String slug = name.replaceAll("[\\s.]+", "-");
        slug = slug.replaceAll("[^a-zA-Z0-9_-]", "");
        slug = slug.toLowerCase();
        return slug;
    }

    public @NotNull TextComponent getFactionLink(String encoded) {
        encoded = toSlug(encoded);
        String url = "https://lotuscraft.fun/faction/" + encoded;

        TextComponent link = new TextComponent(url);
        link.setColor(ChatColor.BLUE);
        link.setUnderlined(true);
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        return link;
    }
}
