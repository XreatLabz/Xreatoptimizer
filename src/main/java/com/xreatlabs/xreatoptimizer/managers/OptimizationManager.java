package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.core.AdaptiveThresholdManager;
import com.xreatlabs.xreatoptimizer.api.OptimizationEvent;
import com.xreatlabs.xreatoptimizer.api.XreatOptimizerAPI;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.scheduler.BukkitTask;

public class OptimizationManager {
    private final XreatOptimizer plugin;
    private BukkitTask optimizationTask;
    private OptimizationProfile currentProfile = OptimizationProfile.AUTO;
    private OptimizationProfile effectiveProfile = OptimizationProfile.NORMAL;
    private volatile boolean isRunning = false;
    
    public enum OptimizationProfile {
        AUTO,
        LIGHT,
        NORMAL,
        AGGRESSIVE,
        EMERGENCY
    }
    
    private int activeHibernateRadius = 64;
    private int activeEntityPassiveLimit = 200;
    private int activeEntityHostileLimit = 150;
    private int activeEntityItemLimit = 1000;
    private int activeMemoryThreshold = 80;
    private int activeTickTasksPerTick = 5;
    
    public OptimizationManager(XreatOptimizer plugin) {
        this.plugin = plugin;
        String initialProfile = plugin.getConfig().getString("general.initial_profile", "AUTO");
        this.currentProfile = OptimizationProfile.valueOf(initialProfile.toUpperCase());
    }
    
    public void start() {
        optimizationTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::runOptimizationCycle,
            20L,
            100L
        );
        isRunning = true;
        LoggerUtils.info("Optimization manager started with profile: " + currentProfile);
    }
    
    public void stop() {
        isRunning = false;
        if (optimizationTask != null) {
            optimizationTask.cancel();
        }
        LoggerUtils.info("Optimization manager stopped.");
    }
    
    private void runOptimizationCycle() {
        if (!isRunning) return;

        long startTime = System.currentTimeMillis();
        double currentTPS = TPSUtils.getTPS();
        double memoryUsage = MemoryUtils.getMemoryUsagePercentage();

        OptimizationEvent.BeforeOptimizationEvent beforeEvent =
            new OptimizationEvent.BeforeOptimizationEvent(effectiveProfile.name(), currentTPS, memoryUsage);
        XreatOptimizerAPI.fireEvent(beforeEvent);

        if (beforeEvent.isCancelled()) {
            return;
        }

        OptimizationProfile previousEffective = effectiveProfile;

        if (currentProfile == OptimizationProfile.AUTO) {
            adjustProfileAutomatically();
        } else {
            effectiveProfile = currentProfile;
        }

        applyProfileParameters();
        propagateToSubsystems();

        if (previousEffective != effectiveProfile) {
            OptimizationEvent.ProfileChangeEvent profileEvent =
                new OptimizationEvent.ProfileChangeEvent(
                    previousEffective.name(),
                    effectiveProfile.name(),
                    "Automatic profile adjustment based on server performance"
                );
            XreatOptimizerAPI.fireEvent(profileEvent);

            if (profileEvent.isCancelled()) {
                effectiveProfile = previousEffective;
                applyProfileParameters();
                propagateToSubsystems();
            } else {
                LoggerUtils.info("Effective profile changed: " + previousEffective + " -> " + effectiveProfile);
                if (plugin.getNotificationManager() != null) {
                    plugin.getNotificationManager().notifyProfileChange(
                        previousEffective.name(), effectiveProfile.name());
                }
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;
        OptimizationEvent.AfterOptimizationEvent afterEvent =
            new OptimizationEvent.AfterOptimizationEvent(effectiveProfile.name(), executionTime, true);
        XreatOptimizerAPI.fireEvent(afterEvent);
    }
    
    private void adjustProfileAutomatically() {
        double currentTPS = TPSUtils.getTPS();
        double memoryUsage = MemoryUtils.getMemoryUsagePercentage();
        
        double lightThreshold = getThreshold("light", 19.5);
        double normalThreshold = getThreshold("normal", 18.0);
        double aggressiveThreshold = getThreshold("aggressive", 16.0);
        
        OptimizationProfile newProfile;
        if (currentTPS > lightThreshold && memoryUsage < 70) {
            newProfile = OptimizationProfile.LIGHT;
        } else if (currentTPS > normalThreshold) {
            newProfile = OptimizationProfile.NORMAL;
        } else if (currentTPS > aggressiveThreshold) {
            newProfile = OptimizationProfile.AGGRESSIVE;
        } else {
            newProfile = OptimizationProfile.EMERGENCY;
        }
        
        if (memoryUsage > plugin.getConfig().getInt("memory_reclaim_threshold_percent", 80)) {
            if (newProfile == OptimizationProfile.LIGHT) {
                newProfile = OptimizationProfile.NORMAL;
            } else if (newProfile == OptimizationProfile.NORMAL) {
                newProfile = OptimizationProfile.AGGRESSIVE;
            }
        }
        
        effectiveProfile = newProfile;
    }
    
    /** Get TPS threshold */
    private double getThreshold(String level, double defaultValue) {
        AdaptiveThresholdManager engine = plugin.getAdaptiveThresholdManager();
        if (engine != null && engine.isRunning()) {
            switch (level) {
                case "light": return engine.getAdjustedLightThreshold();
                case "normal": return engine.getAdjustedNormalThreshold();
                case "aggressive": return engine.getAdjustedAggressiveThreshold();
            }
        }
        return plugin.getConfig().getDouble("optimization.tps_thresholds." + level, defaultValue);
    }
    
    /** Get entity limit */
    private int getEntityLimit(String type, int defaultValue) {
        AdaptiveThresholdManager engine = plugin.getAdaptiveThresholdManager();
        if (engine != null && engine.isRunning()) {
            switch (type) {
                case "passive": return engine.getAdjustedPassiveLimit();
                case "hostile": return engine.getAdjustedHostileLimit();
                case "item": return engine.getAdjustedItemLimit();
            }
        }
        return plugin.getConfig().getInt("optimization.entity_limits." + type, defaultValue);
    }
    
    private void applyProfileParameters() {
        int basePassive = getEntityLimit("passive", 200);
        int baseHostile = getEntityLimit("hostile", 150);
        int baseItem = getEntityLimit("item", 1000);
        
        switch (effectiveProfile) {
            case LIGHT:
                activeHibernateRadius = 96;
                activeEntityPassiveLimit = (int) (basePassive * 1.5);
                activeEntityHostileLimit = (int) (baseHostile * 1.5);
                activeEntityItemLimit = (int) (baseItem * 1.5);
                activeMemoryThreshold = 85;
                activeTickTasksPerTick = 3;
                break;
            case NORMAL:
                activeHibernateRadius = 64;
                activeEntityPassiveLimit = basePassive;
                activeEntityHostileLimit = baseHostile;
                activeEntityItemLimit = baseItem;
                activeMemoryThreshold = 80;
                activeTickTasksPerTick = 5;
                break;
            case AGGRESSIVE:
                activeHibernateRadius = 48;
                activeEntityPassiveLimit = (int) (basePassive * 0.75);
                activeEntityHostileLimit = (int) (baseHostile * 0.75);
                activeEntityItemLimit = (int) (baseItem * 0.75);
                activeMemoryThreshold = 70;
                activeTickTasksPerTick = 8;
                break;
            case EMERGENCY:
                activeHibernateRadius = 32;
                activeEntityPassiveLimit = (int) (basePassive * 0.5);
                activeEntityHostileLimit = (int) (baseHostile * 0.5);
                activeEntityItemLimit = (int) (baseItem * 0.5);
                activeMemoryThreshold = 60;
                activeTickTasksPerTick = 12;
                break;
            default:
                break;
        }
        
        activeEntityPassiveLimit = Math.max(100, activeEntityPassiveLimit);
        activeEntityHostileLimit = Math.max(75, activeEntityHostileLimit);
        activeEntityItemLimit = Math.max(250, activeEntityItemLimit);
    }
    
    private void propagateToSubsystems() {
        if (plugin.getHibernateManager() != null && plugin.getHibernateManager().isRunning()) {
            plugin.getHibernateManager().setRadius(activeHibernateRadius);
        }
        
        if (plugin.getMemorySaver() != null) {
            plugin.getMemorySaver().setThreshold(activeMemoryThreshold);
        }
        
        if (plugin.getAdvancedEntityOptimizer() != null && !plugin.getAdvancedEntityOptimizer().isEnabled()) {
            plugin.getAdvancedEntityOptimizer().setEnabled(true);
        }
        
        if (plugin.getEntityCullingManager() != null) {
            boolean shouldEnable = effectiveProfile == OptimizationProfile.AGGRESSIVE ||
                effectiveProfile == OptimizationProfile.EMERGENCY;
            if (plugin.getEntityCullingManager().isEnabled() != shouldEnable) {
                plugin.getEntityCullingManager().setEnabled(shouldEnable);
            }
        }
        
        if (plugin.getDynamicViewDistance() != null && plugin.getDynamicViewDistance().isRunning()) {
            if (effectiveProfile == OptimizationProfile.EMERGENCY) {
                for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                    try {
                        world.getClass().getMethod("setViewDistance", int.class).invoke(world, 4);
                    } catch (Exception ignored) {}
                }
            } else if (effectiveProfile == OptimizationProfile.AGGRESSIVE) {
                for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                    try {
                        world.getClass().getMethod("setViewDistance", int.class).invoke(world, 6);
                    } catch (Exception ignored) {}
                }
            }
        }
        
        if (plugin.getAdvancedCPURAMOptimizer() != null) {
            plugin.getAdvancedCPURAMOptimizer().setIntensity(effectiveProfile);
        }
        
        if (effectiveProfile == OptimizationProfile.EMERGENCY) {
            if (TPSUtils.isTPSDangerous()) {
                LoggerUtils.warn("TPS is in dangerous territory (< 10). Consider reducing load.");
            }
            MemoryUtils.suggestGarbageCollection();
        }
        
        LoggerUtils.debug("Profile " + effectiveProfile + " applied: hibernate=" + activeHibernateRadius +
            " entities(P/H/I)=" + activeEntityPassiveLimit + "/" + activeEntityHostileLimit + "/" + activeEntityItemLimit +
            " memThresh=" + activeMemoryThreshold + "%");
    }
    
    public OptimizationProfile getCurrentProfile() {
        return currentProfile;
    }
    
    public OptimizationProfile getEffectiveProfile() {
        return effectiveProfile;
    }
    
    public void setProfile(OptimizationProfile profile) {
        OptimizationProfile old = this.currentProfile;
        this.currentProfile = profile;
        if (profile != OptimizationProfile.AUTO) {
            this.effectiveProfile = profile;
        }
        LoggerUtils.info("Optimization profile changed to: " + profile);
    }
    
    public boolean isRunning() {
        return isRunning;
    }

    public void reloadConfig() {
        String profileName = plugin.getConfig().getString("general.initial_profile", "AUTO");
        this.currentProfile = OptimizationProfile.valueOf(profileName.toUpperCase());
        LoggerUtils.info("Configuration reloaded. Profile set to: " + currentProfile);
    }

    public void forceOptimizationCycle() {
        runOptimizationCycle();
        LoggerUtils.info("Forced optimization cycle executed");
    }

    public int getMaxEntityLimit() {
        return plugin.getConfig().getInt("entity_limiter.max_entities_per_chunk", 50);
    }
    
    public int getActiveEntityPassiveLimit() { return activeEntityPassiveLimit; }
    public int getActiveEntityHostileLimit() { return activeEntityHostileLimit; }
    public int getActiveEntityItemLimit() { return activeEntityItemLimit; }
    public int getActiveHibernateRadius() { return activeHibernateRadius; }
    public int getActiveMemoryThreshold() { return activeMemoryThreshold; }
}
