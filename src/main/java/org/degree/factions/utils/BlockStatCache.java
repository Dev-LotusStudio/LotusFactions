package org.degree.factions.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class BlockStatCache {
    private static final ConcurrentHashMap<String, Map<String, BlockStat>> stats = new ConcurrentHashMap<>();

    public static void incrementPlaced(String uuid, String faction, String blockType) {
        if (uuid == null) return;
        stats.compute(uuid, (key, statsByBlock) -> {
            Map<String, BlockStat> updated = statsByBlock != null ? statsByBlock : new HashMap<>();
            BlockStat stat = updated.get(blockType);
            if (stat == null) {
                stat = new BlockStat(faction);
                updated.put(blockType, stat);
            } else if (!java.util.Objects.equals(stat.factionName, faction)) {
                stat.factionName = faction;
            }
            stat.placed++;
            return updated;
        });
    }

    public static void incrementBroken(String uuid, String faction, String blockType) {
        if (uuid == null) return;
        stats.compute(uuid, (key, statsByBlock) -> {
            Map<String, BlockStat> updated = statsByBlock != null ? statsByBlock : new HashMap<>();
            BlockStat stat = updated.get(blockType);
            if (stat == null) {
                stat = new BlockStat(faction);
                updated.put(blockType, stat);
            } else if (!java.util.Objects.equals(stat.factionName, faction)) {
                stat.factionName = faction;
            }
            stat.broken++;
            return updated;
        });
    }

    public static Map<String, Map<String, BlockStat>> getAndClearStats() {
        Map<String, Map<String, BlockStat>> drained = new HashMap<>();
        for (String uuid : stats.keySet().toArray(String[]::new)) {
            stats.computeIfPresent(uuid, (key, statsByBlock) -> {
                drained.put(key, statsByBlock);
                return null;
            });
        }
        return drained;
    }

    public static Map<String, BlockStat> getAndClearStats(String uuid) {
        if (uuid == null) return null;
        AtomicReference<Map<String, BlockStat>> drained = new AtomicReference<>();
        stats.computeIfPresent(uuid, (key, statsByBlock) -> {
            drained.set(statsByBlock);
            return null;
        });
        return drained.get();
    }

    public static void renameFaction(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        for (String uuid : stats.keySet().toArray(String[]::new)) {
            stats.computeIfPresent(uuid, (key, statsByBlock) -> {
                for (BlockStat stat : statsByBlock.values()) {
                    if (oldName.equals(stat.factionName)) {
                        stat.factionName = newName;
                    }
                }
                return statsByBlock;
            });
        }
    }

    public static void removeFaction(String factionName) {
        if (factionName == null) return;
        for (String uuid : stats.keySet().toArray(String[]::new)) {
            stats.computeIfPresent(uuid, (key, statsByBlock) -> {
                statsByBlock.values().removeIf(stat -> factionName.equals(stat.factionName));
                return statsByBlock.isEmpty() ? null : statsByBlock;
            });
        }
    }

    public static void merge(Map<String, Map<String, BlockStat>> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        snapshot.forEach((uuid, statsByBlock) -> stats.compute(uuid, (key, currentStats) -> {
            Map<String, BlockStat> merged = currentStats != null ? currentStats : new HashMap<>();
            statsByBlock.forEach((blockType, source) -> {
                BlockStat target = merged.get(blockType);
                if (target == null) {
                    target = new BlockStat(source.factionName);
                    merged.put(blockType, target);
                }
                target.placed += source.placed;
                target.broken += source.broken;
            });
            return merged;
        }));
    }

    public static class BlockStat {
        public String factionName;
        public int placed = 0;
        public int broken = 0;
        public BlockStat(String factionName) {
            this.factionName = factionName;
        }
    }
}
