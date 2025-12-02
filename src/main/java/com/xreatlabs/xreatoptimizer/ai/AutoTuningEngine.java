package com.xreatlabs.xreatoptimizer.ai;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.OptimizationManager;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight AI auto-tuning engine for adaptive optimization
 */
public class AutoTuningEngine {
    private final XreatOptimizer plugin;
    private BukkitTask tuningTask;
    private volatile boolean isRunning = false;
    
    // Adaptive thresholds that the engine adjusts
    private double adjustedTPSLightThreshold;
    private double adjustedTPSNormalThreshold;
    private double adjustedTPSAggressiveThreshold;
    private int adjustedEntityPassiveLimit;
    private int adjustedEntityHostileLimit;
    private int adjustedEntityItemLimit;
    
    // Historical data for adaptive learning
    private final List<Double> tpsHistory = new ArrayList<>();
    private final List<Double> memoryHistory = new ArrayList<>();
    private final List<Integer> entityHistory = new ArrayList<>();
    
    // Exponential Weighted Moving Average (EWMA) parameters
    private static final double EWMA_ALPHA = 0.3; // Smoothing factor
    
    public AutoTuningEngine(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.adjustedTPSLightThreshold = plugin.getConfig().getDouble("optimization.tps_thresholds.light", 19.5);
        this.adjustedTPSNormalThreshold = plugin.getConfig().getDouble("optimization.tps_thresholds.normal", 18.0);
        this.adjustedTPSAggressiveThreshold = plugin.getConfig().getDouble("optimization.tps_thresholds.aggressive", 16.0);
        this.adjustedEntityPassiveLimit = plugin.getConfig().getInt("optimization.entity_limits.passive", 200);
        this.adjustedEntityHostileLimit = plugin.getConfig().getInt("optimization.entity_limits.hostile", 150);
        this.adjustedEntityItemLimit = plugin.getConfig().getInt("optimization.entity_limits.item", 1000);
    }
    
    /**
     * Starts the auto-tuning engine
     */
    public void start() {
        if (plugin.getConfig().getBoolean("auto_tune", true)) {
            // Run tuning adjustments every 15 minutes
            tuningTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::runAdaptiveTuning,
                18000L,  // Initial delay (15 minutes)
                18000L   // Repeat interval (15 minutes)
            );
            
            isRunning = true;
            LoggerUtils.info("Auto-tuning engine started.");
        } else {
            LoggerUtils.info("Auto-tuning engine is disabled via config.");
        }
    }
    
    /**
     * Stops the auto-tuning engine
     */
    public void stop() {
        isRunning = false;
        if (tuningTask != null) {
            tuningTask.cancel();
        }
        LoggerUtils.info("Auto-tuning engine stopped.");
    }
    
    /**
     * Runs the adaptive tuning cycle
     */
    private void runAdaptiveTuning() {
        if (!isRunning) return;
        
        // Collect current metrics
        double currentTPS = TPSUtils.getTPS();
        double currentMemory = MemoryUtils.getMemoryUsagePercentage();
        int currentEntities = plugin.getPerformanceMonitor().getCurrentEntityCount();
        
        // Add to historical data
        addToHistory(currentTPS, currentMemory, currentEntities);
        
        // Adjust thresholds based on historical performance
        adjustThresholds(currentTPS, currentMemory, currentEntities);
        
        // Apply new settings to optimization manager
        applyAdjustedSettings();
        
        LoggerUtils.debug("Auto-tuning completed. TPS: " + currentTPS + 
                         ", Memory: " + currentMemory + "%, Entities: " + currentEntities);
    }
    
    /**
     * Adds current metrics to historical data
     */
    private void addToHistory(double tps, double memory, int entities) {
        // Keep only the last 60 data points (15 min * 4 checks per hour * 1 hour = 60)
        if (tpsHistory.size() >= 60) {
            tpsHistory.remove(0);
        }
        if (memoryHistory.size() >= 60) {
            memoryHistory.remove(0);
        }
        if (entityHistory.size() >= 60) {
            entityHistory.remove(0);
        }
        
        tpsHistory.add(tps);
        memoryHistory.add(memory);
        entityHistory.add(entities);
    }
    
    /**
     * Adjusts thresholds based on historical performance and current conditions
     */
    private void adjustThresholds(double currentTPS, double currentMemory, int currentEntities) {
        // Calculate historical averages for decision making
        double avgTPS = getAverage(tpsHistory);
        double avgMemory = getAverage(memoryHistory);
        int avgEntities = getAverageInt(entityHistory);
        
        // Adjust TPS thresholds based on performance consistency
        adjustTPSThresholds(currentTPS, avgTPS);
        
        // Adjust entity limits based on server capacity and usage patterns
        adjustEntityLimits(currentMemory, avgMemory, avgEntities, avgTPS);
    }
    
    /**
     * Adjusts TPS thresholds based on performance
     */
    private void adjustTPSThresholds(double currentTPS, double avgTPS) {
        // If server consistently performs better than current thresholds, tighten them (more aggressive)
        if (avgTPS > 19.5) {
            // Server is doing very well, can be more aggressive with optimizations
            adjustedTPSLightThreshold = Math.min(19.8, adjustedTPSLightThreshold + 0.1);
            adjustedTPSNormalThreshold = Math.min(18.5, adjustedTPSNormalThreshold + 0.2);
            adjustedTPSAggressiveThreshold = Math.min(16.5, adjustedTPSAggressiveThreshold + 0.2);
        } else if (avgTPS < 17.0) {
            // Server is struggling, be more conservative with thresholds
            adjustedTPSLightThreshold = Math.max(19.0, adjustedTPSLightThreshold - 0.2);
            adjustedTPSNormalThreshold = Math.max(16.5, adjustedTPSNormalThreshold - 0.3);
            adjustedTPSAggressiveThreshold = Math.max(14.0, adjustedTPSAggressiveThreshold - 0.5);
        }
        
        // Ensure thresholds remain within reasonable bounds
        ensureThresholdBounds();
    }
    
    /**
     * Adjusts entity limits based on memory usage
     * 
     * IMPORTANT: Minimum limits are set high to prevent breaking player farms.
     * We never reduce passive mob limits below 200 or hostile below 150.
     */
    private void adjustEntityLimits(double currentMemory, double avgMemory, int avgEntities, double avgTPS) {
        // Safe minimum limits - NEVER go below these to protect player farms
        final int MIN_PASSIVE_LIMIT = 200;  // Players have farms!
        final int MIN_HOSTILE_LIMIT = 150;  // Mob farms exist
        final int MIN_ITEM_LIMIT = 500;     // Players drop items
        
        // If memory pressure is consistently high, slightly reduce entity limits
        // but NEVER below safe minimums
        if (avgMemory > 85) { // Increased threshold - only reduce at very high memory
            adjustedEntityPassiveLimit = Math.max(MIN_PASSIVE_LIMIT, (int) (adjustedEntityPassiveLimit * 0.95));
            adjustedEntityHostileLimit = Math.max(MIN_HOSTILE_LIMIT, (int) (adjustedEntityHostileLimit * 0.95));
            adjustedEntityItemLimit = Math.max(MIN_ITEM_LIMIT, (int) (adjustedEntityItemLimit * 0.9));
            
            LoggerUtils.debug("Slightly reduced entity limits due to high memory pressure: P:" + 
                             adjustedEntityPassiveLimit + " H:" + adjustedEntityHostileLimit + 
                             " I:" + adjustedEntityItemLimit);
        } else if (avgMemory < 50 && avgTPS > 19.0) {
            // If server has headroom, can increase limits for better gameplay experience
            adjustedEntityPassiveLimit = Math.min(500, (int) (adjustedEntityPassiveLimit * 1.1));
            adjustedEntityHostileLimit = Math.min(400, (int) (adjustedEntityHostileLimit * 1.1));
            adjustedEntityItemLimit = Math.min(2000, (int) (adjustedEntityItemLimit * 1.15));
            
            LoggerUtils.debug("Increased entity limits due to available resources: P:" + 
                             adjustedEntityPassiveLimit + " H:" + adjustedEntityHostileLimit + 
                             " I:" + adjustedEntityItemLimit);
        }
        
        // Final safety check - ensure we never go below minimums
        adjustedEntityPassiveLimit = Math.max(MIN_PASSIVE_LIMIT, adjustedEntityPassiveLimit);
        adjustedEntityHostileLimit = Math.max(MIN_HOSTILE_LIMIT, adjustedEntityHostileLimit);
        adjustedEntityItemLimit = Math.max(MIN_ITEM_LIMIT, adjustedEntityItemLimit);
    }
    
    /**
     * Applies adjusted settings to the optimization manager
     */
    private void applyAdjustedSettings() {
        // In a real implementation, we would update the config dynamically
        // For now, we'll just log the new values
        LoggerUtils.debug("Adjusted TPS thresholds - Light: " + adjustedTPSLightThreshold + 
                         ", Normal: " + adjustedTPSNormalThreshold + 
                         ", Aggressive: " + adjustedTPSAggressiveThreshold);
        LoggerUtils.debug("Adjusted entity limits - Passive: " + adjustedEntityPassiveLimit + 
                         ", Hostile: " + adjustedEntityHostileLimit + 
                         ", Item: " + adjustedEntityItemLimit);
    }
    
    /**
     * Ensures thresholds remain within reasonable bounds
     */
    private void ensureThresholdBounds() {
        adjustedTPSLightThreshold = Math.max(18.0, Math.min(20.0, adjustedTPSLightThreshold));
        adjustedTPSNormalThreshold = Math.max(10.0, Math.min(19.0, adjustedTPSNormalThreshold));
        adjustedTPSAggressiveThreshold = Math.max(5.0, Math.min(18.0, adjustedTPSAggressiveThreshold));
        
        // Ensure thresholds are in proper order (light > normal > aggressive)
        if (adjustedTPSLightThreshold <= adjustedTPSNormalThreshold) {
            adjustedTPSNormalThreshold = adjustedTPSLightThreshold - 1.0;
        }
        if (adjustedTPSNormalThreshold <= adjustedTPSAggressiveThreshold) {
            adjustedTPSAggressiveThreshold = adjustedTPSNormalThreshold - 1.0;
        }
    }
    
    /**
     * Gets the exponential weighted moving average of TPS values
     */
    private double getEWMA(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        
        double ewma = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            ewma = EWMA_ALPHA * values.get(i) + (1 - EWMA_ALPHA) * ewma;
        }
        return ewma;
    }
    
    /**
     * Gets simple average of TPS values
     */
    private double getAverage(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }
    
    /**
     * Gets simple average of integer values
     */
    private int getAverageInt(List<Integer> values) {
        if (values.isEmpty()) return 0;
        
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum / values.size();
    }
    
    /**
     * Gets the current adjusted light TPS threshold
     */
    public double getAdjustedLightThreshold() {
        return adjustedTPSLightThreshold;
    }
    
    /**
     * Gets the current adjusted normal TPS threshold
     */
    public double getAdjustedNormalThreshold() {
        return adjustedTPSNormalThreshold;
    }
    
    /**
     * Gets the current adjusted aggressive TPS threshold
     */
    public double getAdjustedAggressiveThreshold() {
        return adjustedTPSAggressiveThreshold;
    }
    
    /**
     * Gets the current adjusted passive entity limit
     */
    public int getAdjustedPassiveLimit() {
        return adjustedEntityPassiveLimit;
    }
    
    /**
     * Gets the current adjusted hostile entity limit
     */
    public int getAdjustedHostileLimit() {
        return adjustedEntityHostileLimit;
    }
    
    /**
     * Gets the current adjusted item entity limit
     */
    public int getAdjustedItemLimit() {
        return adjustedEntityItemLimit;
    }
    
    /**
     * Checks if the auto-tuning engine is running
     */
    public boolean isRunning() {
        return isRunning;
    }
}