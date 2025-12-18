package org.degree.factions.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class FactionCache {
    private static final Map<String, String> uuidToFaction = new ConcurrentHashMap<>();
    private static final Map<String, String> factionToColorHex = new ConcurrentHashMap<>();

    public static void setFaction(String uuid, String factionName) {
        if (factionName == null) uuidToFaction.remove(uuid);
        else uuidToFaction.put(uuid, factionName);
    }

    public static String getFaction(String uuid) {
        return uuidToFaction.get(uuid);
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
