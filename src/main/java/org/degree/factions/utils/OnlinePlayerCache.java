package org.degree.factions.utils;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OnlinePlayerCache {
    private static final Map<String, String> namesByUuid = new ConcurrentHashMap<>();

    private OnlinePlayerCache() {
    }

    public static void add(Player player) {
        namesByUuid.put(player.getUniqueId().toString(), player.getName());
    }

    public static void remove(Player player) {
        namesByUuid.remove(player.getUniqueId().toString());
    }

    public static Map<String, String> snapshot() {
        return new HashMap<>(namesByUuid);
    }

    public static Set<String> names() {
        return Set.copyOf(namesByUuid.values());
    }
}
