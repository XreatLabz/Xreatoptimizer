package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.storage.StatisticsStorage;
import com.xreatlabs.xreatoptimizer.utils.EntityUtils;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PerformanceMonitor {
    private final XreatOptimizer plugin;
    private BukkitTask monitorTask;
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 720;
    private final ConcurrentLinkedDeque<Double> tpsHistory = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Double> memoryHistory = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Integer> entityHistory = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Integer> chunkHistory = new ConcurrentLinkedDeque<>();

    private double minTps = 20.0;
    private double maxTps = 20.0;
    private double sumTps = 0.0;
    private int tpsCount = 0;

    private double minMemory = 100.0;
    private double maxMemory = 0.0;
    private double sumMemory = 0.0;
    private int memoryCount = 0;

    private int maxEntities = 0;
    private int maxChunks = 0;
    private int maxPlayers = 0;

    public PerformanceMonitor(XreatOptimizer plugin) {
        this.plugin = plugin;
        metrics.put("tps", 20.0);
        metrics.put("used_memory_mb", 0L);
        metrics.put("max_memory_mb", 0L);
        metrics.put("memory_percentage", 0.0);
        metrics.put("avg_tick_time_ms", 50.0);
        metrics.put("entity_count", 0);
        metrics.put("chunk_count", 0);
        metrics.put("player_count", 0);
    }

    public void start() {
        if (monitorTask != null) {
            return;
        }

        monitorTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::updateMetrics,
            100L,
            100L
        );
        LoggerUtils.info("Performance monitoring started.");
    }

    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
            LoggerUtils.info("Performance monitoring stopped.");
        }
    }

    private void updateMetrics() {
        double currentTPS = TPSUtils.getTPS();
        metrics.put("tps", currentTPS);

        long usedMemory = MemoryUtils.getUsedMemoryMB();
        long maxMemory = MemoryUtils.getMaxMemoryMB();
        double memoryPercentage = MemoryUtils.getMemoryUsagePercentage();
        metrics.put("used_memory_mb", usedMemory);
        metrics.put("max_memory_mb", maxMemory);
        metrics.put("memory_percentage", memoryPercentage);

        double avgTickTime = TPSUtils.getAverageTickTime();
        metrics.put("avg_tick_time_ms", avgTickTime);

        int entityCount = EntityUtils.getTotalEntityCount();
        metrics.put("entity_count", entityCount);

        int chunkCount = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            chunkCount += world.getLoadedChunks().length;
        }
        metrics.put("chunk_count", chunkCount);

        int playerCount = Bukkit.getOnlinePlayers().size();
        metrics.put("player_count", playerCount);

        addToHistory(currentTPS, memoryPercentage, entityCount, chunkCount);
        updateStatistics(currentTPS, memoryPercentage, entityCount, chunkCount, playerCount);

        if (System.currentTimeMillis() % 60000 < 5000) {
            LoggerUtils.debug("Performance Metrics - TPS: " + String.format("%.2f", currentTPS) +
                ", Heap Usage: " + String.format("%.1f", memoryPercentage) + "% (" +
                usedMemory + "MB/" + maxMemory + "MB), Entities: " + entityCount +
                ", Chunks: " + chunkCount);
        }

        feedStatisticsStorage(currentTPS, memoryPercentage, entityCount, chunkCount);
        maybeGenerateReport();
    }

    private void addToHistory(double tps, double memory, int entities, int chunks) {
        tpsHistory.addLast(tps);
        memoryHistory.addLast(memory);
        entityHistory.addLast(entities);
        chunkHistory.addLast(chunks);

        while (tpsHistory.size() > MAX_HISTORY) tpsHistory.removeFirst();
        while (memoryHistory.size() > MAX_HISTORY) memoryHistory.removeFirst();
        while (entityHistory.size() > MAX_HISTORY) entityHistory.removeFirst();
        while (chunkHistory.size() > MAX_HISTORY) chunkHistory.removeFirst();
    }

    private void updateStatistics(double tps, double memory, int entities, int chunks, int players) {
        minTps = Math.min(minTps, tps);
        maxTps = Math.max(maxTps, tps);
        sumTps += tps;
        tpsCount++;

        minMemory = Math.min(minMemory, memory);
        maxMemory = Math.max(maxMemory, memory);
        sumMemory += memory;
        memoryCount++;

        maxEntities = Math.max(maxEntities, entities);
        maxChunks = Math.max(maxChunks, chunks);
        maxPlayers = Math.max(maxPlayers, players);
    }

    private void feedStatisticsStorage(double tps, double memory, int entities, int chunks) {
        StatisticsStorage storage = plugin.getStatisticsStorage();
        if (storage != null) {
            String profile = plugin.getOptimizationManager() != null ?
                plugin.getOptimizationManager().getEffectiveProfile().name() : "NORMAL";
            storage.recordSnapshot(tps, memory, entities, chunks, profile);
        }
    }

    private void maybeGenerateReport() {
        File reportsDir = new File(plugin.getDataFolder(), "reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.getMinute() == 0 && now.getSecond() < 5) {
            generateHourlyReport(now);
        }

        if (now.getHour() == 0 && now.getMinute() == 0 && now.getSecond() < 5) {
            generateDailyReport(now);
        }
    }

    private void generateHourlyReport(LocalDateTime time) {
        try {
            String fileName = "hourly_report_" + time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH")) + ".txt";
            File reportFile = new File(plugin.getDataFolder(), "reports/" + fileName);

            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write("XreatOptimizer Hourly Performance Report\n");
                writer.write("Generated at: " + time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
                writer.write("========================================\n\n");

                writer.write("TPS Average: " + getAverageTPS() + "\n");
                writer.write("Memory Usage Peak: " + getPeakMemoryUsage() + "%\n");
                writer.write("Max Entities: " + getMaxEntityCount() + "\n");
                writer.write("Max Chunks Loaded: " + getMaxChunkCount() + "\n");
                writer.write("Max Players Online: " + getMaxPlayerCount() + "\n");
            }
        } catch (IOException e) {
            LoggerUtils.error("Could not generate hourly report", e);
        }
    }

    private void generateDailyReport(LocalDateTime time) {
        try {
            String fileName = "daily_report_" + time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".txt";
            File reportFile = new File(plugin.getDataFolder(), "reports/" + fileName);

            try (FileWriter writer = new FileWriter(reportFile)) {
                writer.write("XreatOptimizer Daily Performance Report\n");
                writer.write("Generated at: " + time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
                writer.write("========================================\n\n");

                writer.write("Average TPS: " + getAverageTPS() + "\n");
                writer.write("Memory Usage Average: " + getAverageMemoryUsage() + "%\n");
                writer.write("Memory Usage Peak: " + getPeakMemoryUsage() + "%\n");
                writer.write("Average Entities: " + getAverageEntityCount() + "\n");
                writer.write("Max Entities: " + getMaxEntityCount() + "\n");
                writer.write("Average Chunks Loaded: " + getAverageChunkCount() + "\n");
                writer.write("Peak Players: " + getMaxPlayerCount() + "\n");

                writer.write("\nPerformance Statistics:\n");
                writer.write("- Time spent under 15 TPS: " + getTimeUnderTPSThreshold(15.0) + " minutes\n");
                writer.write("- Time spent under 10 TPS: " + getTimeUnderTPSThreshold(10.0) + " minutes\n");
                writer.write("- Memory pressure incidents: " + getMemoryPressureEvents() + "\n");
            }
        } catch (IOException e) {
            LoggerUtils.error("Could not generate daily report", e);
        }
    }

    public Map<String, Object> getMetrics() {
        return new HashMap<>(metrics);
    }

    public Object getMetric(String key) {
        Object value = metrics.get(key);
        if (value == null) {
            switch (key) {
                case "tps":
                case "memory_percentage":
                case "avg_tick_time_ms":
                    return 20.0;
                case "entity_count":
                case "chunk_count":
                case "player_count":
                    return 0;
                case "used_memory_mb":
                case "max_memory_mb":
                    return 0L;
                default:
                    return 0.0;
            }
        }
        return value;
    }

    public double getCurrentTPS() {
        return (double) metrics.getOrDefault("tps", 20.0);
    }

    public double getCurrentMemoryPercentage() {
        return (double) metrics.getOrDefault("memory_percentage", 0.0);
    }

    public int getCurrentEntityCount() {
        return (int) metrics.getOrDefault("entity_count", 0);
    }

    public int getCurrentChunkCount() {
        return (int) metrics.getOrDefault("chunk_count", 0);
    }

    public int getCurrentPlayerCount() {
        return (int) metrics.getOrDefault("player_count", 0);
    }

    private double getAverageTPS() {
        return tpsCount > 0 ? sumTps / tpsCount : 20.0;
    }

    private double getPeakMemoryUsage() {
        return maxMemory;
    }

    private int getMaxEntityCount() {
        return maxEntities;
    }

    private int getMaxChunkCount() {
        return maxChunks;
    }

    private int getMaxPlayerCount() {
        return maxPlayers;
    }

    private double getAverageMemoryUsage() {
        return memoryCount > 0 ? sumMemory / memoryCount : 0.0;
    }

    private int getAverageEntityCount() {
        if (entityHistory.isEmpty()) return 0;
        long sum = 0;
        for (int v : entityHistory) sum += v;
        return (int) (sum / entityHistory.size());
    }

    private int getAverageChunkCount() {
        if (chunkHistory.isEmpty()) return 0;
        long sum = 0;
        for (int v : chunkHistory) sum += v;
        return (int) (sum / chunkHistory.size());
    }

    private int getTimeUnderTPSThreshold(double threshold) {
        int count = 0;
        for (double tps : tpsHistory) {
            if (tps < threshold) count++;
        }
        return (count * 5) / 60;
    }

    private int getMemoryPressureEvents() {
        int events = 0;
        boolean inPressure = false;
        for (double mem : memoryHistory) {
            if (mem > 80.0 && !inPressure) {
                events++;
                inPressure = true;
            } else if (mem <= 80.0) {
                inPressure = false;
            }
        }
        return events;
    }

    public double getMinTps() { return minTps; }
    public double getMaxTps() { return maxTps; }

    public void updatePlayerCount() {
        metrics.put("player_count", Bukkit.getOnlinePlayers().size());
    }

    public void incrementChunkLoads() {
        int current = (int) metrics.getOrDefault("chunk_count", 0);
        metrics.put("chunk_count", current + 1);
    }

    public void decrementChunkLoads() {
        int current = (int) metrics.getOrDefault("chunk_count", 0);
        if (current > 0) {
            metrics.put("chunk_count", current - 1);
        }
    }

    public void incrementEntityCount() {
        // Intentionally left as a no-op. Entity counts are sampled directly each monitor cycle
        // so event-based increments don't drift away from reality.
    }
}
