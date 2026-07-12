package org.degree.factions.utils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FactionCache {
    private static final Map<String, Optional<String>> uuidToFaction = new ConcurrentHashMap<>();
    private static final Map<String, String> factionToColorHex = new ConcurrentHashMap<>();

    public static void setFaction(String uuid, String factionName) {
        if (uuid == null) return;
        uuidToFaction.put(uuid, Optional.ofNullable(factionName));
    }

    public static String getFaction(String uuid) {
        if (uuid == null) return null;
        Optional<String> cachedFaction = uuidToFaction.get(uuid);
        return cachedFaction == null ? null : cachedFaction.orElse(null);
    }

    public static void clearPlayer(String uuid) {
        if (uuid == null) return;
        uuidToFaction.remove(uuid);
    }

    public static void clearFaction(String factionName) {
        if (factionName == null) return;
        uuidToFaction.forEach((uuid, cachedFaction) -> {
            if (cachedFaction.filter(factionName::equals).isPresent()) {
                uuidToFaction.remove(uuid, cachedFaction);
            }
        });
        factionToColorHex.remove(factionName);
    }

    public static void renameFaction(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        uuidToFaction.forEach((uuid, cachedFaction) -> {
            if (cachedFaction.filter(oldName::equals).isPresent()) {
                uuidToFaction.replace(uuid, cachedFaction, Optional.of(newName));
            }
        });
        String color = factionToColorHex.remove(oldName);
        if (color != null) {
            factionToColorHex.put(newName, color);
        }
    }

    public static void setFactionColor(String factionName, String colorHex) {
        if (factionName == null) return;
        if (colorHex == null) factionToColorHex.remove(factionName);
        else factionToColorHex.put(factionName, colorHex);
    }

    public static String getFactionColor(String factionName) {
        if (factionName == null) return null;
        return factionToColorHex.get(factionName);
    }

    public static void removeFactionColor(String factionName) {
        if (factionName == null) return;
        factionToColorHex.remove(factionName);
    }
}
