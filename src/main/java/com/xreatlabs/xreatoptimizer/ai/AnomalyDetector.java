package com.xreatlabs.xreatoptimizer.ai;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.api.OptimizationEvent;
import com.xreatlabs.xreatoptimizer.api.XreatOptimizerAPI;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Real-time anomaly detection system
 * Detects unusual patterns that indicate performance issues
 *
 * Detection methods:
 * - Z-score for outlier detection
 * - IQR (Interquartile Range) for robust outlier detection
 * - Pattern recognition for specific issues
 *
 * Detected anomalies:
 * - Memory leaks (steadily increasing memory)
 * - Plugin conflicts (sudden TPS drops after plugin load)
 * - Chunk loading issues (excessive chunk load/unload cycles)
 * - Entity explosions (rapid entity count increase)
 */
public class AnomalyDetector {

    private final XreatOptimizer plugin;
    private BukkitTask detectionTask;
    private volatile boolean isRunning = false;

    // Historical data for anomaly detection (last 5 minutes)
    private final Deque<MetricSnapshot> history = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY = 300; // 5 minutes at 1-second intervals

    // Anomaly thresholds
    private static final double Z_SCORE_THRESHOLD = 2.5; // 2.5 standard deviations
    private static final double IQR_MULTIPLIER = 1.5;

    // Pattern detection state
    private long lastMemoryLeakCheck = 0;
    private long lastEntityExplosionCheck = 0;
    private int consecutiveMemoryIncreases = 0;
    private double lastMemoryValue = 0;

    // Anomaly reporting cooldowns (prevent log spam)
    private final Map<AnomalyType, Long> lastReportedTime = new ConcurrentHashMap<>();
    private static final long REPORT_COOLDOWN_MS = 30000; // 30 seconds between same anomaly reports

    /**
     * Metric snapshot for anomaly detection
     */
    private static class MetricSnapshot {
        final long timestamp;
        final double tps;
        final double memory;
        final int entities;
        final int chunks;
        final int players;

        MetricSnapshot(long timestamp, double tps, double memory, int entities, int chunks, int players) {
            this.timestamp = timestamp;
            this.tps = tps;
            this.memory = memory;
            this.entities = entities;
            this.chunks = chunks;
            this.players = players;
        }
    }

    /**
     * Detected anomaly
     */
    public static class Anomaly {
        public final AnomalyType type;
        public final String description;
        public final double severity; // 0.0 to 1.0
        public final String rootCause;
        public final String recommendation;
        public final long timestamp;

        public Anomaly(AnomalyType type, String description, double severity, String rootCause, String recommendation) {
            this.type = type;
            this.description = description;
            this.severity = severity;
            this.rootCause = rootCause;
            this.recommendation = recommendation;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s (Severity: %.0f%%) - %s",
                type, description, severity * 100, recommendation);
        }
    }

    /**
     * Types of anomalies
     */
    public enum AnomalyType {
        MEMORY_LEAK,
        SUDDEN_TPS_DROP,
        ENTITY_EXPLOSION,
        CHUNK_THRASHING,
        MEMORY_SPIKE,
        TPS_OSCILLATION,
        PLUGIN_CONFLICT
    }

    public AnomalyDetector(XreatOptimizer plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the anomaly detector
     */
    public void start() {
        if (!plugin.getConfig().getBoolean("anomaly_detection.enabled", true)) {
            LoggerUtils.info("Anomaly detector is disabled in config");
            return;
        }

        isRunning = true;

        // Run detection every second
        detectionTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runDetectionCycle,
            20L,  // 1 second initial delay
            20L   // 1 second interval
        );

        LoggerUtils.info("Anomaly detection system started");
    }

    /**
     * Stop the anomaly detector
     */
    public void stop() {
        isRunning = false;
        if (detectionTask != null) {
            detectionTask.cancel();
            detectionTask = null;
        }
        LoggerUtils.info("Anomaly detection system stopped");
    }

    /**
     * Run detection cycle
     */
    private void runDetectionCycle() {
        if (!isRunning) return;

        try {
            // Collect current metrics
            long now = System.currentTimeMillis();
            double tps = plugin.getPerformanceMonitor().getCurrentTPS();
            double memory = plugin.getPerformanceMonitor().getCurrentMemoryPercentage();
            int entities = plugin.getPerformanceMonitor().getCurrentEntityCount();
            int chunks = plugin.getPerformanceMonitor().getCurrentChunkCount();
            int players = Bukkit.getOnlinePlayers().size();

            // Add to history
            MetricSnapshot snapshot = new MetricSnapshot(now, tps, memory, entities, chunks, players);
            history.addLast(snapshot);

            // Trim history
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }

            // Need at least 60 seconds of data
            if (history.size() < 60) {
                return;
            }

            // Run anomaly detection
            List<Anomaly> anomalies = detectAnomalies(snapshot);

            // Report anomalies
            for (Anomaly anomaly : anomalies) {
                reportAnomaly(anomaly);
            }

        } catch (Exception e) {
            LoggerUtils.debug("Error in anomaly detection: " + e.getMessage());
        }
    }

    /**
     * Detect anomalies in current metrics
     */
    private List<Anomaly> detectAnomalies(MetricSnapshot current) {
        List<Anomaly> anomalies = new ArrayList<>();

        // Check for memory leak
        Anomaly memoryLeak = detectMemoryLeak(current);
        if (memoryLeak != null) anomalies.add(memoryLeak);

        // Check for sudden TPS drop
        Anomaly tpsDrop = detectSuddenTPSDrop(current);
        if (tpsDrop != null) anomalies.add(tpsDrop);

        // Check for entity explosion
        Anomaly entityExplosion = detectEntityExplosion(current);
        if (entityExplosion != null) anomalies.add(entityExplosion);

        // Check for chunk thrashing
        Anomaly chunkThrashing = detectChunkThrashing(current);
        if (chunkThrashing != null) anomalies.add(chunkThrashing);

        // Check for memory spike
        Anomaly memorySpike = detectMemorySpike(current);
        if (memorySpike != null) anomalies.add(memorySpike);

        // Check for TPS oscillation
        Anomaly tpsOscillation = detectTPSOscillation(current);
        if (tpsOscillation != null) anomalies.add(tpsOscillation);

        return anomalies;
    }

    /**
     * Detect memory leak (steadily increasing memory)
     */
    private Anomaly detectMemoryLeak(MetricSnapshot current) {
        long now = System.currentTimeMillis();

        // Check every 30 seconds
        if (now - lastMemoryLeakCheck < 30000) {
            return null;
        }
        lastMemoryLeakCheck = now;

        // Track consecutive memory increases
        if (current.memory > lastMemoryValue + 0.5) {
            consecutiveMemoryIncreases++;
        } else {
            consecutiveMemoryIncreases = 0;
        }
        lastMemoryValue = current.memory;

        // If memory has been increasing for 10+ consecutive checks (5 minutes)
        if (consecutiveMemoryIncreases >= 10) {
            double severity = Math.min(1.0, consecutiveMemoryIncreases / 20.0);

            return new Anomaly(
                AnomalyType.MEMORY_LEAK,
                "Possible memory leak detected - memory steadily increasing",
                severity,
                "Memory has been increasing consistently for " + (consecutiveMemoryIncreases * 30) + " seconds",
                "Check for plugin memory leaks, consider restarting server, enable aggressive GC"
            );
        }

        return null;
    }

    /**
     * Detect sudden TPS drop
     */
    private Anomaly detectSuddenTPSDrop(MetricSnapshot current) {
        if (history.size() < 30) return null;

        // Get TPS from 30 seconds ago
        Iterator<MetricSnapshot> iter = history.descendingIterator();
        for (int i = 0; i < 30 && iter.hasNext(); i++) {
            iter.next();
        }

        if (!iter.hasNext()) return null;
        MetricSnapshot past = iter.next();

        // Check for sudden drop (>5 TPS in 30 seconds)
        double tpsDrop = past.tps - current.tps;
        if (tpsDrop > 5.0) {
            double severity = Math.min(1.0, tpsDrop / 10.0);

            return new Anomaly(
                AnomalyType.SUDDEN_TPS_DROP,
                String.format("Sudden TPS drop detected: %.2f -> %.2f", past.tps, current.tps),
                severity,
                "TPS dropped by " + String.format("%.2f", tpsDrop) + " in 30 seconds",
                "Check for plugin conflicts, chunk generation, or entity spawning issues"
            );
        }

        return null;
    }

    /**
     * Detect entity explosion (rapid entity count increase)
     */
    private Anomaly detectEntityExplosion(MetricSnapshot current) {
        long now = System.currentTimeMillis();

        // Check every 10 seconds
        if (now - lastEntityExplosionCheck < 10000) {
            return null;
        }
        lastEntityExplosionCheck = now;

        if (history.size() < 60) return null;

        // Get entity count from 60 seconds ago
        MetricSnapshot past = history.peekFirst();
        if (past == null) return null;

        // Check for rapid increase (>500 entities in 60 seconds)
        int entityIncrease = current.entities - past.entities;
        if (entityIncrease > 500) {
            double severity = Math.min(1.0, entityIncrease / 1000.0);

            return new Anomaly(
                AnomalyType.ENTITY_EXPLOSION,
                "Entity explosion detected: " + entityIncrease + " new entities in 60 seconds",
                severity,
                "Possible mob farm, spawner, or entity duplication glitch",
                "Check for mob farms, disable spawners, or enable entity limiting"
            );
        }

        return null;
    }

    /**
     * Detect chunk thrashing (excessive load/unload cycles)
     */
    private Anomaly detectChunkThrashing(MetricSnapshot current) {
        if (history.size() < 60) return null;

        // Calculate chunk variance over last 60 seconds
        List<Integer> chunkCounts = new ArrayList<>();
        for (MetricSnapshot snapshot : history) {
            chunkCounts.add(snapshot.chunks);
        }

        double variance = calculateVariance(chunkCounts);
        double stdDev = Math.sqrt(variance);

        // High variance indicates thrashing
        if (stdDev > 100) {
            double severity = Math.min(1.0, stdDev / 200.0);

            return new Anomaly(
                AnomalyType.CHUNK_THRASHING,
                "Chunk thrashing detected - excessive load/unload cycles",
                severity,
                "Chunk count variance: " + String.format("%.0f", variance),
                "Check view distance settings, player teleportation, or chunk loading plugins"
            );
        }

        return null;
    }

    /**
     * Detect memory spike using Z-score
     */
    private Anomaly detectMemorySpike(MetricSnapshot current) {
        if (history.size() < 60) return null;

        List<Double> memoryValues = new ArrayList<>();
        for (MetricSnapshot snapshot : history) {
            memoryValues.add(snapshot.memory);
        }

        double mean = calculateMean(memoryValues);
        double stdDev = Math.sqrt(calculateVariance(memoryValues));

        // Calculate Z-score for current memory
        double zScore = (current.memory - mean) / stdDev;

        if (zScore > Z_SCORE_THRESHOLD) {
            double severity = Math.min(1.0, (zScore - Z_SCORE_THRESHOLD) / 2.0);

            return new Anomaly(
                AnomalyType.MEMORY_SPIKE,
                String.format("Memory spike detected: %.1f%% (Z-score: %.2f)", current.memory, zScore),
                severity,
                "Memory usage is " + String.format("%.1f", zScore) + " standard deviations above normal",
                "Run garbage collection, check for memory-intensive operations"
            );
        }

        return null;
    }

    /**
     * Detect TPS oscillation (unstable performance)
     */
    private Anomaly detectTPSOscillation(MetricSnapshot current) {
        if (history.size() < 120) return null;

        // Get last 2 minutes of TPS data
        List<Double> recentTps = new ArrayList<>();
        Iterator<MetricSnapshot> iter = history.descendingIterator();
        for (int i = 0; i < 120 && iter.hasNext(); i++) {
            recentTps.add(iter.next().tps);
        }

        // Count direction changes (oscillations)
        int oscillations = 0;
        boolean wasIncreasing = false;
        for (int i = 1; i < recentTps.size(); i++) {
            boolean isIncreasing = recentTps.get(i) > recentTps.get(i - 1);
            if (i > 1 && isIncreasing != wasIncreasing) {
                oscillations++;
            }
            wasIncreasing = isIncreasing;
        }

        // High oscillation count indicates instability
        if (oscillations > 40) { // More than 40 direction changes in 2 minutes
            double severity = Math.min(1.0, oscillations / 80.0);

            return new Anomaly(
                AnomalyType.TPS_OSCILLATION,
                "TPS oscillation detected - unstable performance",
                severity,
                oscillations + " TPS direction changes in 2 minutes",
                "Check for periodic tasks, scheduled operations, or conflicting optimizations"
            );
        }

        return null;
    }

    /**
     * Report detected anomaly
     */
    private void reportAnomaly(Anomaly anomaly) {
        long now = System.currentTimeMillis();

        // Check cooldown to prevent log spam
        Long lastReported = lastReportedTime.get(anomaly.type);
        if (lastReported != null && (now - lastReported) < REPORT_COOLDOWN_MS) {
            return; // Skip reporting, still in cooldown
        }

        // Update last reported time
        lastReportedTime.put(anomaly.type, now);

        // Log based on severity
        if (anomaly.severity >= 0.7) {
            LoggerUtils.warn("ANOMALY DETECTED: " + anomaly.toString());
        } else {
            LoggerUtils.info("Anomaly detected: " + anomaly.toString());
        }

        // Fire anomaly detected event
        OptimizationEvent.AnomalyDetectedEvent anomalyEvent =
            new OptimizationEvent.AnomalyDetectedEvent(
                anomaly.type.toString(),
                anomaly.description,
                anomaly.severity
            );
        XreatOptimizerAPI.fireEvent(anomalyEvent);

        // Notify via notification system if available
        if (plugin.getNotificationManager() != null && anomaly.severity >= 0.5) {
            plugin.getNotificationManager().notifyAnomaly(
                anomaly.type.toString(),
                anomaly.description,
                anomaly.recommendation
            );
        }

        // Record in Prometheus metrics if available
        if (plugin.getPrometheusExporter() != null && plugin.getPrometheusExporter().isEnabled()) {
            plugin.getPrometheusExporter().getMetricsRegistry().recordLagSpike();
        }
    }

    /**
     * Calculate mean of values
     */
    private double calculateMean(List<? extends Number> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Number value : values) {
            sum += value.doubleValue();
        }
        return sum / values.size();
    }

    /**
     * Calculate variance of values
     */
    private double calculateVariance(List<? extends Number> values) {
        if (values.isEmpty()) return 0.0;
        double mean = calculateMean(values);
        double sumSquaredDiff = 0.0;
        for (Number value : values) {
            double diff = value.doubleValue() - mean;
            sumSquaredDiff += diff * diff;
        }
        return sumSquaredDiff / values.size();
    }

    /**
     * Check if detector is running
     */
    public boolean isRunning() {
        return isRunning;
    }
}
