package com.xreatlabs.xreatoptimizer.ai;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.OptimizationManager;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Predictive performance management engine
 * Uses time-series forecasting to predict lag spikes before they occur
 *
 * Features:
 * - Exponential smoothing with trend detection
 * - Seasonal decomposition for daily/weekly patterns
 * - Lag spike prediction 30-60 seconds in advance
 * - Player activity pattern learning
 * - Proactive optimization profile switching
 */
public class PredictiveEngine {

    private final XreatOptimizer plugin;
    private BukkitTask predictionTask;
    private volatile boolean isRunning = false;

    // Time-series data (last 10 minutes = 600 samples at 1-second intervals)
    private final Deque<DataPoint> tpsTimeSeries = new ConcurrentLinkedDeque<>();
    private final Deque<DataPoint> memoryTimeSeries = new ConcurrentLinkedDeque<>();
    private final Deque<DataPoint> playerTimeSeries = new ConcurrentLinkedDeque<>();

    // Seasonal patterns (hourly averages for each day of week)
    private final Map<DayOfWeek, Map<Integer, SeasonalData>> seasonalPatterns = new ConcurrentHashMap<>();

    // Holt-Winters parameters
    private static final double ALPHA = 0.3;  // Level smoothing
    private static final double BETA = 0.1;   // Trend smoothing
    private static final double GAMMA = 0.2;  // Seasonal smoothing

    // Prediction thresholds
    private static final double TPS_WARNING_THRESHOLD = 17.0;
    private static final double TPS_CRITICAL_THRESHOLD = 15.0;
    private static final double MEMORY_WARNING_THRESHOLD = 75.0;
    private static final double MEMORY_CRITICAL_THRESHOLD = 85.0;

    // State tracking
    private double lastLevel = 20.0;
    private double lastTrend = 0.0;
    private boolean predictionWarningIssued = false;
    private long lastProactiveSwitch = 0;

    /**
     * Data point for time-series
     */
    private static class DataPoint {
        final long timestamp;
        final double value;

        DataPoint(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    /**
     * Seasonal pattern data
     */
    private static class SeasonalData {
        double avgTps;
        double avgMemory;
        int avgPlayers;
        int sampleCount;

        SeasonalData() {
            this.avgTps = 20.0;
            this.avgMemory = 50.0;
            this.avgPlayers = 0;
            this.sampleCount = 0;
        }

        void update(double tps, double memory, int players) {
            avgTps = (avgTps * sampleCount + tps) / (sampleCount + 1);
            avgMemory = (avgMemory * sampleCount + memory) / (sampleCount + 1);
            avgPlayers = (avgPlayers * sampleCount + players) / (sampleCount + 1);
            sampleCount++;
        }
    }

    /**
     * Prediction result
     */
    public static class Prediction {
        public final double predictedTps;
        public final double predictedMemory;
        public final double confidence;
        public final boolean lagSpikeExpected;
        public final String recommendation;

        Prediction(double tps, double memory, double confidence, boolean lagSpike, String recommendation) {
            this.predictedTps = tps;
            this.predictedMemory = memory;
            this.confidence = confidence;
            this.lagSpikeExpected = lagSpike;
            this.recommendation = recommendation;
        }
    }

    public PredictiveEngine(XreatOptimizer plugin) {
        this.plugin = plugin;
        initializeSeasonalPatterns();
    }

    /**
     * Initialize seasonal pattern storage
     */
    private void initializeSeasonalPatterns() {
        for (DayOfWeek day : DayOfWeek.values()) {
            Map<Integer, SeasonalData> hourlyData = new ConcurrentHashMap<>();
            for (int hour = 0; hour < 24; hour++) {
                hourlyData.put(hour, new SeasonalData());
            }
            seasonalPatterns.put(day, hourlyData);
        }
    }

    /**
     * Start the predictive engine
     */
    public void start() {
        if (!plugin.getConfig().getBoolean("predictive_optimization.enabled", true)) {
            LoggerUtils.info("Predictive engine is disabled in config");
            return;
        }

        isRunning = true;

        // Run predictions every second for real-time forecasting
        predictionTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runPredictionCycle,
            20L,  // 1 second initial delay
            20L   // 1 second interval
        );

        LoggerUtils.info("Predictive performance engine started");
    }

    /**
     * Stop the predictive engine
     */
    public void stop() {
        isRunning = false;
        if (predictionTask != null) {
            predictionTask.cancel();
            predictionTask = null;
        }
        LoggerUtils.info("Predictive performance engine stopped");
    }

    /**
     * Run a prediction cycle
     */
    private void runPredictionCycle() {
        if (!isRunning) return;

        try {
            // Collect current metrics
            double currentTps = plugin.getPerformanceMonitor().getCurrentTPS();
            double currentMemory = plugin.getPerformanceMonitor().getCurrentMemoryPercentage();
            int currentPlayers = Bukkit.getOnlinePlayers().size();

            long now = System.currentTimeMillis();

            // Add to time series
            addDataPoint(tpsTimeSeries, now, currentTps);
            addDataPoint(memoryTimeSeries, now, currentMemory);
            addDataPoint(playerTimeSeries, now, currentPlayers);

            // Update seasonal patterns
            updateSeasonalPatterns(currentTps, currentMemory, currentPlayers);

            // Make predictions (30-60 seconds ahead)
            Prediction prediction = predictFuture(30);

            // Take proactive action if needed
            if (prediction.lagSpikeExpected) {
                handlePredictedLagSpike(prediction);
            }

            // Log predictions periodically (every 30 seconds)
            if (now % 30000 < 1000) {
                LoggerUtils.debug(String.format(
                    "Prediction: TPS %.2f -> %.2f, Memory %.1f%% -> %.1f%%, Confidence: %.0f%%, %s",
                    currentTps, prediction.predictedTps,
                    currentMemory, prediction.predictedMemory,
                    prediction.confidence * 100,
                    prediction.recommendation
                ));
            }

        } catch (Exception e) {
            LoggerUtils.debug("Error in prediction cycle: " + e.getMessage());
        }
    }

    /**
     * Add data point to time series
     */
    private void addDataPoint(Deque<DataPoint> series, long timestamp, double value) {
        series.addLast(new DataPoint(timestamp, value));

        // Keep only last 10 minutes of data
        while (series.size() > 600) {
            series.removeFirst();
        }
    }

    /**
     * Update seasonal patterns with current data
     */
    private void updateSeasonalPatterns(double tps, double memory, int players) {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        int hour = now.getHour();

        SeasonalData data = seasonalPatterns.get(day).get(hour);
        data.update(tps, memory, players);
    }

    /**
     * Predict future performance using Holt-Winters exponential smoothing
     */
    public Prediction predictFuture(int secondsAhead) {
        if (tpsTimeSeries.size() < 60 || memoryTimeSeries.size() < 60) {
            // Not enough data yet
            return new Prediction(20.0, 50.0, 0.0, false, "Insufficient data");
        }

        // Apply Holt-Winters method for TPS prediction
        double predictedTps = forecastWithTrend(tpsTimeSeries, secondsAhead);

        // Apply Holt-Winters method for memory prediction
        double predictedMemory = forecastWithTrend(memoryTimeSeries, secondsAhead);

        // Calculate confidence based on recent variance
        double confidence = calculateConfidence(tpsTimeSeries);

        // Adjust prediction with seasonal component
        predictedTps = adjustForSeasonality(predictedTps, "tps");
        predictedMemory = adjustForSeasonality(predictedMemory, "memory");

        // Determine if lag spike is expected
        boolean lagSpikeExpected = predictedTps < TPS_WARNING_THRESHOLD ||
                                   predictedMemory > MEMORY_WARNING_THRESHOLD;

        // Generate recommendation
        String recommendation = generateRecommendation(predictedTps, predictedMemory);

        return new Prediction(predictedTps, predictedMemory, confidence, lagSpikeExpected, recommendation);
    }

    /**
     * Forecast using exponential smoothing with trend
     */
    private double forecastWithTrend(Deque<DataPoint> series, int stepsAhead) {
        if (series.isEmpty()) return 0.0;

        // Initialize with first value
        Iterator<DataPoint> iter = series.iterator();
        double level = iter.next().value;
        double trend = 0.0;

        // Apply Holt's linear exponential smoothing
        while (iter.hasNext()) {
            double value = iter.next().value;
            double prevLevel = level;

            level = ALPHA * value + (1 - ALPHA) * (level + trend);
            trend = BETA * (level - prevLevel) + (1 - BETA) * trend;
        }

        // Store for next iteration
        lastLevel = level;
        lastTrend = trend;

        // Forecast ahead
        return level + stepsAhead * trend;
    }

    /**
     * Calculate prediction confidence based on recent variance
     */
    private double calculateConfidence(Deque<DataPoint> series) {
        if (series.size() < 30) return 0.5;

        // Calculate variance of recent data
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

        // Lower variance = higher confidence
        // Normalize to 0-1 range (assuming stdDev typically < 5 for TPS)
        return Math.max(0.0, Math.min(1.0, 1.0 - (stdDev / 5.0)));
    }

    /**
     * Adjust prediction with seasonal component
     */
    private double adjustForSeasonality(double prediction, String metric) {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        int hour = now.getHour();

        SeasonalData seasonal = seasonalPatterns.get(day).get(hour);
        if (seasonal.sampleCount < 10) {
            return prediction; // Not enough seasonal data
        }

        // Apply seasonal adjustment
        double seasonalFactor = metric.equals("tps") ?
            seasonal.avgTps / 20.0 :
            seasonal.avgMemory / 50.0;

        return prediction * (GAMMA * seasonalFactor + (1 - GAMMA));
    }

    /**
     * Generate recommendation based on prediction
     */
    private String generateRecommendation(double predictedTps, double predictedMemory) {
        if (predictedTps < TPS_CRITICAL_THRESHOLD || predictedMemory > MEMORY_CRITICAL_THRESHOLD) {
            return "CRITICAL: Switch to AGGRESSIVE profile immediately";
        } else if (predictedTps < TPS_WARNING_THRESHOLD || predictedMemory > MEMORY_WARNING_THRESHOLD) {
            return "WARNING: Consider switching to NORMAL profile";
        } else if (predictedTps > 19.5 && predictedMemory < 60.0) {
            return "OPTIMAL: Server performing well";
        } else {
            return "STABLE: Continue monitoring";
        }
    }

    /**
     * Handle predicted lag spike proactively
     */
    private void handlePredictedLagSpike(Prediction prediction) {
        long now = System.currentTimeMillis();

        // Avoid switching too frequently (minimum 60 seconds between switches)
        if (now - lastProactiveSwitch < 60000) {
            return;
        }

        OptimizationManager optManager = plugin.getOptimizationManager();
        if (optManager == null) return;

        OptimizationManager.OptimizationProfile currentProfile = optManager.getCurrentProfile();

        // Proactively switch to more aggressive profile
        if (prediction.predictedTps < TPS_CRITICAL_THRESHOLD ||
            prediction.predictedMemory > MEMORY_CRITICAL_THRESHOLD) {

            if (currentProfile != OptimizationManager.OptimizationProfile.AGGRESSIVE) {
                LoggerUtils.info("PREDICTIVE: Switching to AGGRESSIVE profile (predicted TPS: " +
                    String.format("%.2f", prediction.predictedTps) + ", memory: " +
                    String.format("%.1f%%", prediction.predictedMemory) + ")");

                Bukkit.getScheduler().runTask(plugin, () ->
                    optManager.setProfile(OptimizationManager.OptimizationProfile.AGGRESSIVE)
                );

                lastProactiveSwitch = now;
                predictionWarningIssued = true;
            }

        } else if (prediction.predictedTps < TPS_WARNING_THRESHOLD ||
                   prediction.predictedMemory > MEMORY_WARNING_THRESHOLD) {

            if (currentProfile == OptimizationManager.OptimizationProfile.LIGHT) {
                LoggerUtils.info("PREDICTIVE: Switching to NORMAL profile (predicted TPS: " +
                    String.format("%.2f", prediction.predictedTps) + ", memory: " +
                    String.format("%.1f%%", prediction.predictedMemory) + ")");

                Bukkit.getScheduler().runTask(plugin, () ->
                    optManager.setProfile(OptimizationManager.OptimizationProfile.NORMAL)
                );

                lastProactiveSwitch = now;
                predictionWarningIssued = true;
            }
        }
    }

    /**
     * Get current seasonal pattern for this time
     */
    public SeasonalData getCurrentSeasonalPattern() {
        LocalDateTime now = LocalDateTime.now();
        return seasonalPatterns.get(now.getDayOfWeek()).get(now.getHour());
    }

    /**
     * Check if engine is running
     */
    public boolean isRunning() {
        return isRunning;
    }
}
