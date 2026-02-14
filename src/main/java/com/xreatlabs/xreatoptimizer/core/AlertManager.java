package com.xreatlabs.xreatoptimizer.core;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.api.OptimizationEvent;
import com.xreatlabs.xreatoptimizer.api.XreatOptimizerAPI;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class AlertManager {

    private final XreatOptimizer plugin;
    private BukkitTask detectionTask;
    private volatile boolean isRunning = false;

    private final Deque<MetricSnapshot> history = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY = 300;

    private static final double Z_SCORE_THRESHOLD = 2.5;

    private long lastMemoryCheck = 0;
    private long lastEntityCheck = 0;
    private int consecutiveMemoryIncreases = 0;
    private double lastMemoryValue = 0;

    private final Map<AlertType, Long> lastReportedTime = new ConcurrentHashMap<>();
    private static final long REPORT_COOLDOWN_MS = 30000;

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

    public static class Alert {
        public final AlertType type;
        public final String description;
        public final double severity;
        public final String rootCause;
        public final String recommendation;
        public final long timestamp;

        public Alert(AlertType type, String description, double severity, String rootCause, String recommendation) {
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

    public enum AlertType {
        MEMORY_LEAK,
        SUDDEN_TPS_DROP,
        ENTITY_EXPLOSION,
        CHUNK_THRASHING,
        MEMORY_SPIKE,
        TPS_OSCILLATION,
        PLUGIN_CONFLICT
    }

    public AlertManager(XreatOptimizer plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("anomaly_detection.enabled", true)) {
            LoggerUtils.info("Alert manager is disabled in config");
            return;
        }

        isRunning = true;

        detectionTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runDetectionCycle,
            20L,
            20L
        );

        LoggerUtils.info("Alert manager started");
    }

    public void stop() {
        isRunning = false;
        if (detectionTask != null) {
            detectionTask.cancel();
            detectionTask = null;
        }
    }

    private void runDetectionCycle() {
        if (!isRunning) return;

        try {
            long now = System.currentTimeMillis();
            double tps = plugin.getPerformanceMonitor().getCurrentTPS();
            double memory = plugin.getPerformanceMonitor().getCurrentMemoryPercentage();
            int entities = plugin.getPerformanceMonitor().getCurrentEntityCount();
            int chunks = plugin.getPerformanceMonitor().getCurrentChunkCount();
            int players = Bukkit.getOnlinePlayers().size();

            MetricSnapshot snapshot = new MetricSnapshot(now, tps, memory, entities, chunks, players);
            history.addLast(snapshot);

            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }

            if (history.size() < 60) {
                return;
            }

            List<Alert> alerts = detectAlerts(snapshot);

            for (Alert alert : alerts) {
                reportAlert(alert);
            }

        } catch (Exception e) {
            LoggerUtils.debug("Error in alert detection: " + e.getMessage());
        }
    }

    private List<Alert> detectAlerts(MetricSnapshot current) {
        List<Alert> alerts = new ArrayList<>();

        Alert memoryLeak = detectMemoryLeak(current);
        if (memoryLeak != null) alerts.add(memoryLeak);

        Alert tpsDrop = detectSuddenTPSDrop(current);
        if (tpsDrop != null) alerts.add(tpsDrop);

        Alert entityExplosion = detectEntityExplosion(current);
        if (entityExplosion != null) alerts.add(entityExplosion);

        Alert chunkThrashing = detectChunkThrashing(current);
        if (chunkThrashing != null) alerts.add(chunkThrashing);

        Alert memorySpike = detectMemorySpike(current);
        if (memorySpike != null) alerts.add(memorySpike);

        Alert tpsOscillation = detectTPSOscillation(current);
        if (tpsOscillation != null) alerts.add(tpsOscillation);

        return alerts;
    }

    private Alert detectMemoryLeak(MetricSnapshot current) {
        long now = System.currentTimeMillis();

        if (now - lastMemoryCheck < 30000) {
            return null;
        }
        lastMemoryCheck = now;

        if (current.memory > lastMemoryValue + 0.5) {
            consecutiveMemoryIncreases++;
        } else {
            consecutiveMemoryIncreases = 0;
        }
        lastMemoryValue = current.memory;

        if (consecutiveMemoryIncreases >= 10) {
            double severity = Math.min(1.0, consecutiveMemoryIncreases / 20.0);

            return new Alert(
                AlertType.MEMORY_LEAK,
                "Possible memory leak - memory steadily increasing",
                severity,
                "Memory increasing for " + (consecutiveMemoryIncreases * 30) + " seconds",
                "Check for plugin memory leaks, consider restarting server"
            );
        }

        return null;
    }

    private Alert detectSuddenTPSDrop(MetricSnapshot current) {
        if (history.size() < 30) return null;

        Iterator<MetricSnapshot> iter = history.descendingIterator();
        for (int i = 0; i < 30 && iter.hasNext(); i++) {
            iter.next();
        }

        if (!iter.hasNext()) return null;
        MetricSnapshot past = iter.next();

        double tpsDrop = past.tps - current.tps;
        if (tpsDrop > 5.0) {
            double severity = Math.min(1.0, tpsDrop / 10.0);

            return new Alert(
                AlertType.SUDDEN_TPS_DROP,
                String.format("Sudden TPS drop: %.2f -> %.2f", past.tps, current.tps),
                severity,
                "TPS dropped by " + String.format("%.2f", tpsDrop) + " in 30 seconds",
                "Check for plugin conflicts or chunk generation issues"
            );
        }

        return null;
    }

    private Alert detectEntityExplosion(MetricSnapshot current) {
        long now = System.currentTimeMillis();

        if (now - lastEntityCheck < 10000) {
            return null;
        }
        lastEntityCheck = now;

        if (history.size() < 60) return null;

        MetricSnapshot past = history.peekFirst();
        if (past == null) return null;

        int entityIncrease = current.entities - past.entities;
        if (entityIncrease > 500) {
            double severity = Math.min(1.0, entityIncrease / 1000.0);

            return new Alert(
                AlertType.ENTITY_EXPLOSION,
                "Entity explosion: " + entityIncrease + " new entities in 60 seconds",
                severity,
                "Possible mob farm or entity duplication",
                "Check for mob farms or enable entity limiting"
            );
        }

        return null;
    }

    private Alert detectChunkThrashing(MetricSnapshot current) {
        if (history.size() < 60) return null;

        List<Integer> chunkCounts = new ArrayList<>();
        for (MetricSnapshot snapshot : history) {
            chunkCounts.add(snapshot.chunks);
        }

        double variance = calculateVariance(chunkCounts);
        double stdDev = Math.sqrt(variance);

        if (stdDev > 100) {
            double severity = Math.min(1.0, stdDev / 200.0);

            return new Alert(
                AlertType.CHUNK_THRASHING,
                "Chunk thrashing detected",
                severity,
                "Chunk count variance: " + String.format("%.0f", variance),
                "Check view distance or player teleportation"
            );
        }

        return null;
    }

    private Alert detectMemorySpike(MetricSnapshot current) {
        if (history.size() < 60) return null;

        List<Double> memoryValues = new ArrayList<>();
        for (MetricSnapshot snapshot : history) {
            memoryValues.add(snapshot.memory);
        }

        double mean = calculateMean(memoryValues);
        double stdDev = Math.sqrt(calculateVariance(memoryValues));

        double zScore = (current.memory - mean) / stdDev;

        if (zScore > Z_SCORE_THRESHOLD) {
            double severity = Math.min(1.0, (zScore - Z_SCORE_THRESHOLD) / 2.0);

            return new Alert(
                AlertType.MEMORY_SPIKE,
                String.format("Memory spike: %.1f%% (Z-score: %.2f)", current.memory, zScore),
                severity,
                "Memory is " + String.format("%.1f", zScore) + " std deviations above normal",
                "Run garbage collection"
            );
        }

        return null;
    }

    private Alert detectTPSOscillation(MetricSnapshot current) {
        if (history.size() < 120) return null;

        List<Double> recentTps = new ArrayList<>();
        Iterator<MetricSnapshot> iter = history.descendingIterator();
        for (int i = 0; i < 120 && iter.hasNext(); i++) {
            recentTps.add(iter.next().tps);
        }

        int oscillations = 0;
        boolean wasIncreasing = false;
        for (int i = 1; i < recentTps.size(); i++) {
            boolean isIncreasing = recentTps.get(i) > recentTps.get(i - 1);
            if (i > 1 && isIncreasing != wasIncreasing) {
                oscillations++;
            }
            wasIncreasing = isIncreasing;
        }

        if (oscillations > 40) {
            double severity = Math.min(1.0, oscillations / 80.0);

            return new Alert(
                AlertType.TPS_OSCILLATION,
                "TPS oscillation detected",
                severity,
                oscillations + " TPS direction changes in 2 minutes",
                "Check for periodic tasks or conflicting operations"
            );
        }

        return null;
    }

    private void reportAlert(Alert alert) {
        long now = System.currentTimeMillis();

        Long lastReported = lastReportedTime.get(alert.type);
        if (lastReported != null && (now - lastReported) < REPORT_COOLDOWN_MS) {
            return;
        }

        lastReportedTime.put(alert.type, now);

        if (alert.severity >= 0.7) {
            LoggerUtils.warn("ALERT: " + alert.toString());
        } else {
            LoggerUtils.info("Alert: " + alert.toString());
        }

        OptimizationEvent.AnomalyDetectedEvent event =
            new OptimizationEvent.AnomalyDetectedEvent(
                alert.type.toString(),
                alert.description,
                alert.severity
            );
        XreatOptimizerAPI.fireEvent(event);

        if (plugin.getNotificationManager() != null && alert.severity >= 0.5) {
            plugin.getNotificationManager().notifyAnomaly(
                alert.type.toString(),
                alert.description,
                alert.recommendation
            );
        }

        if (plugin.getPrometheusExporter() != null && plugin.getPrometheusExporter().isEnabled()) {
            plugin.getPrometheusExporter().getMetricsRegistry().recordLagSpike();
        }
    }

    private double calculateMean(List<? extends Number> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Number value : values) {
            sum += value.doubleValue();
        }
        return sum / values.size();
    }

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

    public boolean isRunning() {
        return isRunning;
    }
}
