package org.degree.factions.utils;

import java.util.HashMap;
import java.util.Map;

public class KillStatCache {
    private static final Map<String, KillStat> statsByUuid = new HashMap<>();

    public static void incrementKill(String uuid, String factionName) {
        if (uuid == null || factionName == null) return;
        KillStat stat = statsByUuid.computeIfAbsent(uuid, k -> new KillStat(factionName));
        stat.factionName = factionName;
        stat.kills++;
    }

    public static Map<String, KillStat> getAndClear() {
        Map<String, KillStat> copy = new HashMap<>(statsByUuid);
        statsByUuid.clear();
        return copy;
    }

    public static class KillStat {
        public String factionName;
        public int kills = 0;

        public KillStat(String factionName) {
            this.factionName = factionName;
        }
    }
}
