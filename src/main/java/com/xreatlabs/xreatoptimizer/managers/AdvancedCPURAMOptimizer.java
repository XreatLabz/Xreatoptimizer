package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;

public class AdvancedCPURAMOptimizer {
    private final XreatOptimizer plugin;
    private BukkitTask optimizationTask;
    private volatile boolean isRunning = false;
    
    private double peakCPUUsage = 0.0;
    private long peakRAMUsage = 0L;
    private boolean highUsageDetected = false;
    private OptimizationManager.OptimizationProfile currentIntensity = OptimizationManager.OptimizationProfile.NORMAL;

    public AdvancedCPURAMOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        optimizationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runAdvancedOptimizations,
            40L,
            40L
        );
        
        isRunning = true;
        LoggerUtils.info("Advanced CPU/RAM optimizer started.");
    }
    
    public void stop() {
        isRunning = false;
        if (optimizationTask != null) {
            optimizationTask.cancel();
        }
        LoggerUtils.info("Advanced CPU/RAM optimizer stopped.");
    }
    
    private void runAdvancedOptimizations() {
        if (!isRunning) return;
        
        double cpuUsage = getSystemCPUUsage();
        long ramUsage = MemoryUtils.getUsedMemoryMB();
        double memoryPercentage = MemoryUtils.getMemoryUsagePercentage();
        
        if (cpuUsage > peakCPUUsage) peakCPUUsage = cpuUsage;
        if (ramUsage > peakRAMUsage) peakRAMUsage = ramUsage;
        
        double cpuHighThreshold = 150;
        double cpuEmergencyThreshold = 200;
        double memHighThreshold = 70;
        double memEmergencyThreshold = 80;
        if (currentIntensity == OptimizationManager.OptimizationProfile.AGGRESSIVE) {
            cpuHighThreshold = 100; cpuEmergencyThreshold = 150;
            memHighThreshold = 60; memEmergencyThreshold = 70;
        } else if (currentIntensity == OptimizationManager.OptimizationProfile.EMERGENCY) {
            cpuHighThreshold = 60; cpuEmergencyThreshold = 100;
            memHighThreshold = 50; memEmergencyThreshold = 60;
        }
        
        if (cpuUsage > cpuHighThreshold || memoryPercentage > memHighThreshold) {
            highUsageDetected = true;
            LoggerUtils.debug("High usage detected: CPU=" + cpuUsage + "%, RAM=" + memoryPercentage + "%");
            
            if (cpuUsage > cpuEmergencyThreshold || memoryPercentage > memEmergencyThreshold) {
                applyEmergencyOptimizations(cpuUsage, memoryPercentage);
            } else {
                applyAggressiveOptimizations(cpuUsage, memoryPercentage);
            }
        } else if (highUsageDetected && cpuUsage < 50 && memoryPercentage < 60) {
            highUsageDetected = false;
            applyRecoveryOptimizations();
        }
        
        if (System.currentTimeMillis() % 60000 < 2000) {
            LoggerUtils.info("Process CPU: " + String.format("%.1f", cpuUsage) + "%, Heap: " +
                           String.format("%.1f", memoryPercentage) + "% (" + ramUsage + "MB used, " +
                           MemoryUtils.getMaxMemoryMB() + "MB max)");
        }
    }
    
    private void applyEmergencyOptimizations(double cpuUsage, double memoryPercentage) {
        LoggerUtils.warn("EMERGENCY OPTIMIZATIONS ACTIVE - CPU: " + cpuUsage + "%, RAM: " + memoryPercentage + "%");
        
        int removedEntities = removeExcessEntitiesAggressively();
        maximizeHibernateSettings();
        reduceWorldActivity();
        forceMemoryOptimizations();
        
        LoggerUtils.info("Emergency optimizations applied: Removed " + removedEntities + " entities");
    }
    
    private void applyAggressiveOptimizations(double cpuUsage, double memoryPercentage) {
        LoggerUtils.debug("AGGRESSIVE OPTIMIZATIONS - CPU: " + cpuUsage + "%, RAM: " + memoryPercentage + "%");
        
        removeExcessEntities();
        increaseHibernateRadius();
        optimizeChunkLoading();
        throttleEntityProcessing();
    }
    
    private void applyRecoveryOptimizations() {
        LoggerUtils.info("System recovery detected, reducing optimization intensity");
        restoreNormalHibernateSettings();
        restoreNormalEntityProcessing();
    }
    
    /** Remove excess entities (arrows only - passive mobs/items protected) */
    private int removeExcessEntitiesAggressively() {
        int removedTotal = 0;
        
        for (World world : Bukkit.getWorlds()) {
            removedTotal += removeEntitiesByType(world, EntityType.ARROW, 500);
            removedTotal += removeEntitiesByType(world, EntityType.SPECTRAL_ARROW, 500);
        }
        
        return removedTotal;
    }
    
    /** Remove excess entities (arrows only) */
    private int removeExcessEntities() {
        int removedTotal = 0;
        
        for (World world : Bukkit.getWorlds()) {
            removedTotal += removeEntitiesByType(world, EntityType.ARROW, 500);
        }
        
        return removedTotal;
    }
    
    private int removeEntitiesByType(World world, EntityType type, int limit) {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity.getType() == type) {
                entities.add(entity);
            }
        }
        
        if (entities.size() <= limit) {
            return 0;
        }
        
        int toRemove = entities.size() - limit;
        int removed = 0;
        
        for (Entity entity : entities) {
            if (removed >= toRemove) break;
            
            if (entity.getCustomName() != null || !entity.getPassengers().isEmpty()) {
                continue;
            }
            
            entity.remove();
            removed++;
        }
        
        return removed;
    }
    
    private void increaseHibernateRadius() {
        HibernateManager hm = plugin.getHibernateManager();
        if (hm != null && hm.isRunning()) {
            hm.setRadius(48);
        }
    }
    
    private void maximizeHibernateSettings() {
        HibernateManager hm = plugin.getHibernateManager();
        if (hm != null && hm.isRunning()) {
            hm.setRadius(32);
        }
    }
    
    private void restoreNormalHibernateSettings() {
        HibernateManager hm = plugin.getHibernateManager();
        if (hm != null && hm.isRunning()) {
            int configRadius = plugin.getConfig().getInt("hibernate.radius", 64);
            hm.setRadius(configRadius);
        }
    }
    
    private void optimizeChunkLoading() {
        MemorySaver ms = plugin.getMemorySaver();
        if (ms != null) {
            ms.clearCache();
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                    boolean nearPlayer = false;
                    for (org.bukkit.entity.Player p : world.getPlayers()) {
                        int dx = Math.abs(p.getLocation().getChunk().getX() - chunk.getX());
                        int dz = Math.abs(p.getLocation().getChunk().getZ() - chunk.getZ());
                        if (dx <= 6 && dz <= 6) { nearPlayer = true; break; }
                    }
                    if (!nearPlayer) chunk.unload(true);
                }
            }
        });
    }
    
    private void throttleEntityProcessing() {
        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(true);
        }
        if (plugin.getAdvancedEntityOptimizer() != null) {
            plugin.getAdvancedEntityOptimizer().setEnabled(true);
        }
    }
    
    private void restoreNormalEntityProcessing() {
        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(false);
        }
    }
    
    private void reduceWorldActivity() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.getPlayers().isEmpty()) {
                    try {
                        world.getClass().getMethod("setViewDistance", int.class).invoke(world, 4);
                    } catch (Exception ignored) {}
                }
            }
        });
    }
    
    private void forceMemoryOptimizations() {
        MemorySaver memorySaver = plugin.getMemorySaver();
        if (memorySaver != null) {
            memorySaver.clearCache();
        }
        
        if (TPSUtils.getTPS() > 15.0) {
            System.gc();
            LoggerUtils.debug("Suggested garbage collection");
        }
    }
    
    private double getSystemCPUUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsBean = 
                    (com.sun.management.OperatingSystemMXBean) osBean;
                double processCpuLoad = sunOsBean.getProcessCpuLoad();
                if (processCpuLoad != -1) {
                    return processCpuLoad * 100;
                }
            }
        } catch (Exception e) {
            LoggerUtils.debug("Could not get detailed CPU usage: " + e.getMessage());
        }
        
        return getEstimatedCPUUsage();
    }
    
    private double getEstimatedCPUUsage() {
        double tps = TPSUtils.getTPS();
        return Math.max(0, (20.0 - tps) * 10);
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public double getPeakCPUUsage() {
        return peakCPUUsage;
    }
    
    public long getPeakRAMUsage() {
        return peakRAMUsage;
    }
    
    public void setIntensity(OptimizationManager.OptimizationProfile profile) {
        this.currentIntensity = profile;
    }
}
