package org.degree.factions.utils;

import java.util.HashMap;
import java.util.Map;

public class BlockStatCache {
    private static final Map<String, Map<String, BlockStat>> stats = new HashMap<>();

    public static synchronized void incrementPlaced(String uuid, String faction, String blockType) {
        stats.computeIfAbsent(uuid, k -> new HashMap<>())
                .computeIfAbsent(blockType, k -> new BlockStat(faction))
                .placed++;
    }

    public static synchronized void incrementBroken(String uuid, String faction, String blockType) {
        stats.computeIfAbsent(uuid, k -> new HashMap<>())
                .computeIfAbsent(blockType, k -> new BlockStat(faction))
                .broken++;
    }

    public static synchronized Map<String, Map<String, BlockStat>> getAndClearStats() {
        Map<String, Map<String, BlockStat>> copy = new HashMap<>(stats);
        stats.clear();
        return copy;
    }

    public static synchronized Map<String, BlockStat> getAndClearStats(String uuid) {
        return stats.remove(uuid);
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
