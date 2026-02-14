package com.xreatlabs.xreatoptimizer.core;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class AdaptiveThresholdManager {
    private final XreatOptimizer plugin;
    private BukkitTask tuningTask;
    private volatile boolean isRunning = false;
    
    private double adjustedTPSLightThreshold;
    private double adjustedTPSNormalThreshold;
    private double adjustedTPSAggressiveThreshold;
    private int adjustedEntityPassiveLimit;
    private int adjustedEntityHostileLimit;
    private int adjustedEntityItemLimit;
    
    private final List<Double> tpsHistory = new ArrayList<>();
    private final List<Double> memoryHistory = new ArrayList<>();
    private final List<Integer> entityHistory = new ArrayList<>();
    
    private static final double EWMA_ALPHA = 0.3;
    
    public AdaptiveThresholdManager(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.adjustedTPSLightThreshold = plugin.getConfig().getDouble("optimization.tps_thresholds.light", 19.5);
        this.adjustedTPSNormalThreshold = plugin.getConfig().getDouble("optimization.tps_thresholds.normal", 18.0);
        this.adjustedTPSAggressiveThreshold = plugin.getConfig().getDouble("optimization.tps_thresholds.aggressive", 16.0);
        this.adjustedEntityPassiveLimit = plugin.getConfig().getInt("optimization.entity_limits.passive", 200);
        this.adjustedEntityHostileLimit = plugin.getConfig().getInt("optimization.entity_limits.hostile", 150);
        this.adjustedEntityItemLimit = plugin.getConfig().getInt("optimization.entity_limits.item", 1000);
    }
    
    public void start() {
        if (plugin.getConfig().getBoolean("auto_tune", true)) {
            tuningTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::runAdjustmentCycle,
                18000L,
                18000L
            );
            
            isRunning = true;
            LoggerUtils.info("Adaptive threshold manager started.");
        } else {
            LoggerUtils.info("Adaptive threshold manager is disabled via config.");
        }
    }
    
    public void stop() {
        isRunning = false;
        if (tuningTask != null) {
            tuningTask.cancel();
        }
    }
    
    private void runAdjustmentCycle() {
        if (!isRunning) return;
        
        double currentTPS = TPSUtils.getTPS();
        double currentMemory = MemoryUtils.getMemoryUsagePercentage();
        int currentEntities = plugin.getPerformanceMonitor().getCurrentEntityCount();
        
        addToHistory(currentTPS, currentMemory, currentEntities);
        adjustThresholds(currentTPS, currentMemory, currentEntities);
        applyAdjustedSettings();
        
        LoggerUtils.debug("Threshold adjustment completed. TPS: " + currentTPS + 
                         ", Memory: " + currentMemory + "%, Entities: " + currentEntities);
    }
    
    private void addToHistory(double tps, double memory, int entities) {
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
    
    private void adjustThresholds(double currentTPS, double currentMemory, int currentEntities) {
        double avgTPS = getEWMA(tpsHistory);
        double avgMemory = getEWMA(memoryHistory);
        List<Double> entityDoubles = new ArrayList<>();
        for (int e : entityHistory) entityDoubles.add((double) e);
        int avgEntities = (int) getEWMA(entityDoubles);
        
        adjustTPSThresholds(currentTPS, avgTPS);
        adjustEntityLimits(currentMemory, avgMemory, avgEntities, avgTPS);
    }
    
    private void adjustTPSThresholds(double currentTPS, double avgTPS) {
        if (avgTPS > 19.5) {
            adjustedTPSLightThreshold = Math.min(19.8, adjustedTPSLightThreshold + 0.1);
            adjustedTPSNormalThreshold = Math.min(18.5, adjustedTPSNormalThreshold + 0.2);
            adjustedTPSAggressiveThreshold = Math.min(16.5, adjustedTPSAggressiveThreshold + 0.2);
        } else if (avgTPS < 17.0) {
            adjustedTPSLightThreshold = Math.max(19.0, adjustedTPSLightThreshold - 0.2);
            adjustedTPSNormalThreshold = Math.max(16.5, adjustedTPSNormalThreshold - 0.3);
            adjustedTPSAggressiveThreshold = Math.max(14.0, adjustedTPSAggressiveThreshold - 0.5);
        }
        
        ensureThresholdBounds();
    }
    
    private void adjustEntityLimits(double currentMemory, double avgMemory, int avgEntities, double avgTPS) {
        final int MIN_PASSIVE_LIMIT = 200;
        final int MIN_HOSTILE_LIMIT = 150;
        final int MIN_ITEM_LIMIT = 500;
        
        if (avgMemory > 85) {
            adjustedEntityPassiveLimit = Math.max(MIN_PASSIVE_LIMIT, (int) (adjustedEntityPassiveLimit * 0.95));
            adjustedEntityHostileLimit = Math.max(MIN_HOSTILE_LIMIT, (int) (adjustedEntityHostileLimit * 0.95));
            adjustedEntityItemLimit = Math.max(MIN_ITEM_LIMIT, (int) (adjustedEntityItemLimit * 0.9));
            
            LoggerUtils.debug("Reduced entity limits due to high memory: P:" + 
                             adjustedEntityPassiveLimit + " H:" + adjustedEntityHostileLimit + 
                             " I:" + adjustedEntityItemLimit);
        } else if (avgMemory < 50 && avgTPS > 19.0) {
            adjustedEntityPassiveLimit = Math.min(500, (int) (adjustedEntityPassiveLimit * 1.1));
            adjustedEntityHostileLimit = Math.min(400, (int) (adjustedEntityHostileLimit * 1.1));
            adjustedEntityItemLimit = Math.min(2000, (int) (adjustedEntityItemLimit * 1.15));
            
            LoggerUtils.debug("Increased entity limits: P:" + 
                             adjustedEntityPassiveLimit + " H:" + adjustedEntityHostileLimit + 
                             " I:" + adjustedEntityItemLimit);
        }
        
        adjustedEntityPassiveLimit = Math.max(MIN_PASSIVE_LIMIT, adjustedEntityPassiveLimit);
        adjustedEntityHostileLimit = Math.max(MIN_HOSTILE_LIMIT, adjustedEntityHostileLimit);
        adjustedEntityItemLimit = Math.max(MIN_ITEM_LIMIT, adjustedEntityItemLimit);
    }
    
    private void applyAdjustedSettings() {
        LoggerUtils.debug("Adjusted TPS thresholds - Light: " + adjustedTPSLightThreshold +
                         ", Normal: " + adjustedTPSNormalThreshold +
                         ", Aggressive: " + adjustedTPSAggressiveThreshold);
        LoggerUtils.debug("Adjusted entity limits - Passive: " + adjustedEntityPassiveLimit +
                         ", Hostile: " + adjustedEntityHostileLimit +
                         ", Item: " + adjustedEntityItemLimit);
    }
    
    private void ensureThresholdBounds() {
        adjustedTPSLightThreshold = Math.max(18.0, Math.min(20.0, adjustedTPSLightThreshold));
        adjustedTPSNormalThreshold = Math.max(10.0, Math.min(19.0, adjustedTPSNormalThreshold));
        adjustedTPSAggressiveThreshold = Math.max(5.0, Math.min(18.0, adjustedTPSAggressiveThreshold));
        
        if (adjustedTPSLightThreshold <= adjustedTPSNormalThreshold) {
            adjustedTPSNormalThreshold = adjustedTPSLightThreshold - 1.0;
        }
        if (adjustedTPSNormalThreshold <= adjustedTPSAggressiveThreshold) {
            adjustedTPSAggressiveThreshold = adjustedTPSNormalThreshold - 1.0;
        }
    }
    
    private double getEWMA(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        
        double ewma = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            ewma = EWMA_ALPHA * values.get(i) + (1 - EWMA_ALPHA) * ewma;
        }
        return ewma;
    }
    
    public double getAdjustedLightThreshold() {
        return adjustedTPSLightThreshold;
    }
    
    public double getAdjustedNormalThreshold() {
        return adjustedTPSNormalThreshold;
    }
    
    public double getAdjustedAggressiveThreshold() {
        return adjustedTPSAggressiveThreshold;
    }
    
    public int getAdjustedPassiveLimit() {
        return adjustedEntityPassiveLimit;
    }
    
    public int getAdjustedHostileLimit() {
        return adjustedEntityHostileLimit;
    }
    
    public int getAdjustedItemLimit() {
        return adjustedEntityItemLimit;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
}
