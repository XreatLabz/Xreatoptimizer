package com.xreatlabs.xreatoptimizer.storage;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Persistent statistics storage */
public class StatisticsStorage {

    private final XreatOptimizer plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;

    // In-memory cache for quick access
    private final Deque<PerformanceSnapshot> recentSnapshots = new ConcurrentLinkedDeque<>();
    private static final int MAX_IN_MEMORY_SNAPSHOTS = 1000;

    public StatisticsStorage(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "statistics.yml");
        loadStatistics();
    }

    public void loadStatistics() {
        if (!statsFile.exists()) {
            try {
                statsFile.getParentFile().mkdirs();
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create statistics file: " + e.getMessage());
                return;
            }
        }

        statsConfig = YamlConfiguration.loadConfiguration(statsFile);

        // Load recent snapshots into memory
        if (statsConfig.contains("snapshots")) {
            List<Map<?, ?>> snapshots = statsConfig.getMapList("snapshots");
            for (Map<?, ?> snapshotData : snapshots) {
                try {
                    PerformanceSnapshot snapshot = PerformanceSnapshot.fromMap(snapshotData);
                    recentSnapshots.add(snapshot);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load snapshot: " + e.getMessage());
                }
            }
        }

        plugin.getLogger().info("Loaded " + recentSnapshots.size() + " performance snapshots from disk");
    }

    public void saveStatistics() {
        if (statsConfig == null) {
            return;
        }

        // Convert snapshots to map format for YAML
        List<Map<String, Object>> snapshotMaps = new ArrayList<>();
        for (PerformanceSnapshot snapshot : recentSnapshots) {
            snapshotMaps.add(snapshot.toMap());
        }

        statsConfig.set("snapshots", snapshotMaps);
        statsConfig.set("last_save", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save statistics: " + e.getMessage());
        }
    }

    public void recordSnapshot(double tps, double memoryPercent, int entityCount, int chunkCount, String profile) {
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
            System.currentTimeMillis(),
            tps,
            memoryPercent,
            entityCount,
            chunkCount,
            profile
        );

        recentSnapshots.addLast(snapshot);

        // Maintain size limit
        if (recentSnapshots.size() > MAX_IN_MEMORY_SNAPSHOTS) {
            recentSnapshots.removeFirst();
        }
    }

    public List<PerformanceSnapshot> getSnapshotsFromLastHours(int hours) {
        long cutoffTime = System.currentTimeMillis() - (hours * 3600000L);
        List<PerformanceSnapshot> result = new ArrayList<>();

        for (PerformanceSnapshot snapshot : recentSnapshots) {
            if (snapshot.getTimestamp() >= cutoffTime) {
                result.add(snapshot);
            }
        }

        return result;
    }

    public double getAverageTPS(int hours) {
        List<PerformanceSnapshot> snapshots = getSnapshotsFromLastHours(hours);
        if (snapshots.isEmpty()) {
            return 20.0;
        }

        return snapshots.stream()
            .mapToDouble(PerformanceSnapshot::getTps)
            .average()
            .orElse(20.0);
    }

    public double getPeakMemory(int hours) {
        List<PerformanceSnapshot> snapshots = getSnapshotsFromLastHours(hours);
        if (snapshots.isEmpty()) {
            return 0.0;
        }

        return snapshots.stream()
            .mapToDouble(PerformanceSnapshot::getMemoryPercent)
            .max()
            .orElse(0.0);
    }

    public double getMinTPS(int hours) {
        List<PerformanceSnapshot> snapshots = getSnapshotsFromLastHours(hours);
        if (snapshots.isEmpty()) {
            return 20.0;
        }

        return snapshots.stream()
            .mapToDouble(PerformanceSnapshot::getTps)
            .min()
            .orElse(20.0);
    }

    public String generateReport(int hours) {
        List<PerformanceSnapshot> snapshots = getSnapshotsFromLastHours(hours);

        if (snapshots.isEmpty()) {
            return "No performance data available for the last " + hours + " hours.";
        }

        double avgTPS = snapshots.stream().mapToDouble(PerformanceSnapshot::getTps).average().orElse(20.0);
        double minTPS = snapshots.stream().mapToDouble(PerformanceSnapshot::getTps).min().orElse(20.0);
        double maxTPS = snapshots.stream().mapToDouble(PerformanceSnapshot::getTps).max().orElse(20.0);

        double avgMemory = snapshots.stream().mapToDouble(PerformanceSnapshot::getMemoryPercent).average().orElse(0.0);
        double peakMemory = snapshots.stream().mapToDouble(PerformanceSnapshot::getMemoryPercent).max().orElse(0.0);

        int avgEntities = (int) snapshots.stream().mapToInt(PerformanceSnapshot::getEntityCount).average().orElse(0);
        int maxEntities = snapshots.stream().mapToInt(PerformanceSnapshot::getEntityCount).max().orElse(0);

        StringBuilder report = new StringBuilder();
        report.append("=== Performance Report (Last ").append(hours).append(" Hours) ===\n");
        report.append("Snapshots Analyzed: ").append(snapshots.size()).append("\n\n");

        report.append("TPS Statistics:\n");
        report.append("  Average: ").append(String.format("%.2f", avgTPS)).append("\n");
        report.append("  Minimum: ").append(String.format("%.2f", minTPS)).append("\n");
        report.append("  Maximum: ").append(String.format("%.2f", maxTPS)).append("\n\n");

        report.append("Memory Statistics:\n");
        report.append("  Average: ").append(String.format("%.1f%%", avgMemory)).append("\n");
        report.append("  Peak: ").append(String.format("%.1f%%", peakMemory)).append("\n\n");

        report.append("Entity Statistics:\n");
        report.append("  Average: ").append(avgEntities).append("\n");
        report.append("  Maximum: ").append(maxEntities).append("\n");

        return report.toString();
    }

    public void clearOldSnapshots(int days) {
        long cutoffTime = System.currentTimeMillis() - (days * 86400000L);
        recentSnapshots.removeIf(snapshot -> snapshot.getTimestamp() < cutoffTime);
        plugin.getLogger().info("Cleared snapshots older than " + days + " days");
    }

    public static class PerformanceSnapshot {
        private final long timestamp;
        private final double tps;
        private final double memoryPercent;
        private final int entityCount;
        private final int chunkCount;
        private final String profile;

        public PerformanceSnapshot(long timestamp, double tps, double memoryPercent,
                                 int entityCount, int chunkCount, String profile) {
            this.timestamp = timestamp;
            this.tps = tps;
            this.memoryPercent = memoryPercent;
            this.entityCount = entityCount;
            this.chunkCount = chunkCount;
            this.profile = profile;
        }

        public long getTimestamp() { return timestamp; }
        public double getTps() { return tps; }
        public double getMemoryPercent() { return memoryPercent; }
        public int getEntityCount() { return entityCount; }
        public int getChunkCount() { return chunkCount; }
        public String getProfile() { return profile; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", timestamp);
            map.put("tps", tps);
            map.put("memory", memoryPercent);
            map.put("entities", entityCount);
            map.put("chunks", chunkCount);
            map.put("profile", profile);
            return map;
        }

        public static PerformanceSnapshot fromMap(Map<?, ?> map) {
            return new PerformanceSnapshot(
                ((Number) map.get("timestamp")).longValue(),
                ((Number) map.get("tps")).doubleValue(),
                ((Number) map.get("memory")).doubleValue(),
                ((Number) map.get("entities")).intValue(),
                ((Number) map.get("chunks")).intValue(),
                (String) map.get("profile")
            );
        }
    }
}
