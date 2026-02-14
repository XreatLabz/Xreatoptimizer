package com.xreatlabs.xreatoptimizer.core;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.OptimizationManager;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PerformanceTrendAnalyzer {

    private final XreatOptimizer plugin;
    private BukkitTask analysisTask;
    private volatile boolean isRunning = false;

    private final Deque<DataPoint> tpsTimeSeries = new ConcurrentLinkedDeque<>();
    private final Deque<DataPoint> memoryTimeSeries = new ConcurrentLinkedDeque<>();
    private final Deque<DataPoint> playerTimeSeries = new ConcurrentLinkedDeque<>();

    private final Map<DayOfWeek, Map<Integer, SeasonalData>> seasonalPatterns = new ConcurrentHashMap<>();

    private static final double ALPHA = 0.3;
    private static final double BETA = 0.1;
    private static final double GAMMA = 0.2;

    private static final double TPS_WARNING = 17.0;
    private static final double TPS_CRITICAL = 15.0;
    private static final double MEMORY_WARNING = 75.0;
    private static final double MEMORY_CRITICAL = 85.0;

    private double lastLevel = 20.0;
    private double lastTrend = 0.0;
    private long lastProfileSwitch = 0;

    private static class DataPoint {
        final long timestamp;
        final double value;

        DataPoint(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    private static class SeasonalData {
        double avgTps = 20.0;
        double avgMemory = 50.0;
        int avgPlayers = 0;
        int sampleCount = 0;

        void update(double tps, double memory, int players) {
            avgTps = (avgTps * sampleCount + tps) / (sampleCount + 1);
            avgMemory = (avgMemory * sampleCount + memory) / (sampleCount + 1);
            avgPlayers = (avgPlayers * sampleCount + players) / (sampleCount + 1);
            sampleCount++;
        }
    }

    public static class TrendPrediction {
        public final double predictedTps;
        public final double predictedMemory;
        public final double confidence;
        public final boolean lagSpikeExpected;
        public final String recommendation;

        TrendPrediction(double tps, double memory, double conf, boolean expected, String rec) {
            this.predictedTps = tps;
            this.predictedMemory = memory;
            this.confidence = conf;
            this.lagSpikeExpected = expected;
            this.recommendation = rec;
        }
    }

    public PerformanceTrendAnalyzer(XreatOptimizer plugin) {
        this.plugin = plugin;
        initSeasonalPatterns();
    }

    private void initSeasonalPatterns() {
        for (DayOfWeek day : DayOfWeek.values()) {
            Map<Integer, SeasonalData> hourlyData = new ConcurrentHashMap<>();
            for (int hour = 0; hour < 24; hour++) {
                hourlyData.put(hour, new SeasonalData());
            }
            seasonalPatterns.put(day, hourlyData);
        }
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("predictive_optimization.enabled", true)) {
            LoggerUtils.info("Performance trend analyzer is disabled in config");
            return;
        }

        isRunning = true;

        analysisTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runAnalysisCycle,
            20L,
            20L
        );

        LoggerUtils.info("Performance trend analyzer started");
    }

    public void stop() {
        isRunning = false;
        if (analysisTask != null) {
            analysisTask.cancel();
            analysisTask = null;
        }
    }

    private void runAnalysisCycle() {
        if (!isRunning) return;

        try {
            double currentTps = plugin.getPerformanceMonitor().getCurrentTPS();
            double currentMemory = plugin.getPerformanceMonitor().getCurrentMemoryPercentage();
            int currentPlayers = Bukkit.getOnlinePlayers().size();

            long now = System.currentTimeMillis();

            addDataPoint(tpsTimeSeries, now, currentTps);
            addDataPoint(memoryTimeSeries, now, currentMemory);
            addDataPoint(playerTimeSeries, now, currentPlayers);

            updateSeasonalPatterns(currentTps, currentMemory, currentPlayers);

            TrendPrediction prediction = predictFuture(30);

            if (prediction.lagSpikeExpected) {
                handlePredictedIssue(prediction);
            }

            if (now % 30000 < 1000) {
                LoggerUtils.debug(String.format(
                    "Trend: TPS %.2f -> %.2f, Memory %.1f%% -> %.1f%%, Confidence: %.0f%%, %s",
                    currentTps, prediction.predictedTps,
                    currentMemory, prediction.predictedMemory,
                    prediction.confidence * 100,
                    prediction.recommendation
                ));
            }

        } catch (Exception e) {
            LoggerUtils.debug("Error in trend analysis: " + e.getMessage());
        }
    }

    private void addDataPoint(Deque<DataPoint> series, long timestamp, double value) {
        series.addLast(new DataPoint(timestamp, value));
        while (series.size() > 600) {
            series.removeFirst();
        }
    }

    private void updateSeasonalPatterns(double tps, double memory, int players) {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        int hour = now.getHour();

        SeasonalData data = seasonalPatterns.get(day).get(hour);
        data.update(tps, memory, players);
    }

    public TrendPrediction predictFuture(int secondsAhead) {
        if (tpsTimeSeries.size() < 60 || memoryTimeSeries.size() < 60) {
            return new TrendPrediction(20.0, 50.0, 0.0, false, "Insufficient data");
        }

        double predictedTps = forecastWithTrend(tpsTimeSeries, secondsAhead);
        double predictedMemory = forecastWithTrend(memoryTimeSeries, secondsAhead);

        double confidence = calculateConfidence(tpsTimeSeries);

        predictedTps = adjustForSeasonality(predictedTps, "tps");
        predictedMemory = adjustForSeasonality(predictedMemory, "memory");

        boolean lagSpikeExpected = predictedTps < TPS_WARNING || predictedMemory > MEMORY_WARNING;
        String recommendation = generateRecommendation(predictedTps, predictedMemory);

        return new TrendPrediction(predictedTps, predictedMemory, confidence, lagSpikeExpected, recommendation);
    }

    private double forecastWithTrend(Deque<DataPoint> series, int stepsAhead) {
        if (series.isEmpty()) return 0.0;

        Iterator<DataPoint> iter = series.iterator();
        double level = iter.next().value;
        double trend = 0.0;

        while (iter.hasNext()) {
            double value = iter.next().value;
            double prevLevel = level;

            level = ALPHA * value + (1 - ALPHA) * (level + trend);
            trend = BETA * (level - prevLevel) + (1 - BETA) * trend;
        }

        lastLevel = level;
        lastTrend = trend;

        return level + stepsAhead * trend;
    }

    private double calculateConfidence(Deque<DataPoint> series) {
        if (series.size() < 30) return 0.5;

        List<Double> recent = new ArrayList<>();
        Iterator<DataPoint> iter = series.descendingIterator();
        for (int i = 0; i < 30 && iter.hasNext(); i++) {
            recent.add(iter.next().value);
        }

        double mean = recent.stream().mapToDouble(d -> d).average().orElse(0.0);
        double variance = recent.stream()
            .mapToDouble(d -> Math.pow(d - mean, 2))
            .average()
            .orElse(0.0);

        double stdDev = Math.sqrt(variance);
        return Math.max(0.0, Math.min(1.0, 1.0 - (stdDev / 5.0)));
    }

    private double adjustForSeasonality(double prediction, String metric) {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        int hour = now.getHour();

        SeasonalData seasonal = seasonalPatterns.get(day).get(hour);
        if (seasonal.sampleCount < 10) {
            return prediction;
        }

        double seasonalFactor = metric.equals("tps") ?
            seasonal.avgTps / 20.0 :
            seasonal.avgMemory / 50.0;

        return prediction * (GAMMA * seasonalFactor + (1 - GAMMA));
    }

    private String generateRecommendation(double predictedTps, double predictedMemory) {
        if (predictedTps < TPS_CRITICAL || predictedMemory > MEMORY_CRITICAL) {
            return "CRITICAL: Switch to AGGRESSIVE profile immediately";
        } else if (predictedTps < TPS_WARNING || predictedMemory > MEMORY_WARNING) {
            return "WARNING: Consider switching to NORMAL profile";
        } else if (predictedTps > 19.5 && predictedMemory < 60.0) {
            return "OPTIMAL: Server performing well";
        } else {
            return "STABLE: Continue monitoring";
        }
    }

    private void handlePredictedIssue(TrendPrediction prediction) {
        long now = System.currentTimeMillis();

        if (now - lastProfileSwitch < 60000) {
            return;
        }

        OptimizationManager optManager = plugin.getOptimizationManager();
        if (optManager == null) return;

        OptimizationManager.OptimizationProfile currentProfile = optManager.getCurrentProfile();

        if (prediction.predictedTps < TPS_CRITICAL || prediction.predictedMemory > MEMORY_CRITICAL) {
            if (currentProfile != OptimizationManager.OptimizationProfile.AGGRESSIVE) {
                LoggerUtils.info("TREND: Switching to AGGRESSIVE profile (predicted TPS: " +
                    String.format("%.2f", prediction.predictedTps) + ", memory: " +
                    String.format("%.1f%%", prediction.predictedMemory) + ")");

                Bukkit.getScheduler().runTask(plugin, () ->
                    optManager.setProfile(OptimizationManager.OptimizationProfile.AGGRESSIVE)
                );

                lastProfileSwitch = now;
            }

        } else if (prediction.predictedTps < TPS_WARNING || prediction.predictedMemory > MEMORY_WARNING) {
            if (currentProfile == OptimizationManager.OptimizationProfile.LIGHT) {
                LoggerUtils.info("TREND: Switching to NORMAL profile (predicted TPS: " +
                    String.format("%.2f", prediction.predictedTps) + ", memory: " +
                    String.format("%.1f%%", prediction.predictedMemory) + ")");

                Bukkit.getScheduler().runTask(plugin, () ->
                    optManager.setProfile(OptimizationManager.OptimizationProfile.NORMAL)
                );

                lastProfileSwitch = now;
            }
        }
    }

    public SeasonalData getCurrentSeasonalPattern() {
        LocalDateTime now = LocalDateTime.now();
        return seasonalPatterns.get(now.getDayOfWeek()).get(now.getHour());
    }

    public boolean isRunning() {
        return isRunning;
    }
}
