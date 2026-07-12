package org.degree.factions.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KillStatCache {
    private static final ConcurrentHashMap<String, KillStat> statsByUuid = new ConcurrentHashMap<>();

    public static void incrementKill(String uuid, String factionName) {
        if (uuid == null || factionName == null) return;
        statsByUuid.compute(uuid, (key, stat) -> {
            KillStat updated = stat != null ? stat : new KillStat(factionName);
            updated.factionName = factionName;
            updated.kills++;
            return updated;
        });
    }

    public static Map<String, KillStat> getAndClear() {
        Map<String, KillStat> drained = new HashMap<>();
        for (String uuid : statsByUuid.keySet().toArray(String[]::new)) {
            statsByUuid.computeIfPresent(uuid, (key, stat) -> {
                drained.put(key, stat);
                return null;
            });
        }
        return drained;
    }

    public static void renameFaction(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        statsByUuid.forEach((uuid, ignored) -> statsByUuid.computeIfPresent(uuid, (key, stat) -> {
            if (oldName.equals(stat.factionName)) {
                stat.factionName = newName;
            }
            return stat;
        }));
    }

    public static void removeFaction(String factionName) {
        if (factionName == null) return;
        statsByUuid.forEach((uuid, ignored) -> statsByUuid.computeIfPresent(uuid, (key, stat) ->
                factionName.equals(stat.factionName) ? null : stat));
    }

    public static void merge(Map<String, KillStat> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        snapshot.forEach((uuid, source) -> statsByUuid.compute(uuid, (key, current) -> {
            KillStat target = current != null ? current : new KillStat(source.factionName);
            target.kills += source.kills;
            return target;
        }));
    }

    public static class KillStat {
        public String factionName;
        public int kills = 0;

        public KillStat(String factionName) {
            this.factionName = factionName;
        }
    }
}
