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
        if (isRunning) {
            return;
        }

        optimizationTask = Bukkit.getScheduler().runTaskTimer(
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
            optimizationTask = null;
        }
        LoggerUtils.info("Advanced CPU/RAM optimizer stopped.");
    }

    private void runAdvancedOptimizations() {
        if (!isRunning) {
            return;
        }

        double cpuUsage = getSystemCPUUsage();
        long ramUsage = MemoryUtils.getUsedMemoryMB();
        double memoryPercentage = MemoryUtils.getMemoryUsagePercentage();

        if (cpuUsage > peakCPUUsage) {
            peakCPUUsage = cpuUsage;
        }
        if (ramUsage > peakRAMUsage) {
            peakRAMUsage = ramUsage;
        }

        double cpuHighThreshold = 85;
        double cpuEmergencyThreshold = 95;
        double memHighThreshold = 78;
        double memEmergencyThreshold = 88;

        if (currentIntensity == OptimizationManager.OptimizationProfile.AGGRESSIVE) {
            cpuHighThreshold = 80;
            cpuEmergencyThreshold = 92;
            memHighThreshold = 74;
            memEmergencyThreshold = 84;
        } else if (currentIntensity == OptimizationManager.OptimizationProfile.EMERGENCY) {
            cpuHighThreshold = 75;
            cpuEmergencyThreshold = 90;
            memHighThreshold = 70;
            memEmergencyThreshold = 82;
        }

        if (cpuUsage > cpuHighThreshold || memoryPercentage > memHighThreshold) {
            highUsageDetected = true;
            LoggerUtils.debug("High usage detected: CPU=" + String.format("%.1f", cpuUsage) + "%" +
                ", RAM=" + String.format("%.1f", memoryPercentage) + "%");

            if (cpuUsage > cpuEmergencyThreshold || memoryPercentage > memEmergencyThreshold) {
                applyEmergencyOptimizations(cpuUsage, memoryPercentage);
            } else {
                applyAggressiveOptimizations(cpuUsage, memoryPercentage);
            }
        } else if (highUsageDetected && cpuUsage < 60 && memoryPercentage < 68) {
            highUsageDetected = false;
            applyRecoveryOptimizations();
        }

        if (System.currentTimeMillis() % 60000 < 2000) {
            LoggerUtils.info("Process CPU: " + String.format("%.1f", cpuUsage) + "%" +
                ", Heap: " + String.format("%.1f", memoryPercentage) + "% (" + ramUsage + "MB used, " +
                MemoryUtils.getMaxMemoryMB() + "MB max)");
        }
    }

    private void applyEmergencyOptimizations(double cpuUsage, double memoryPercentage) {
        LoggerUtils.warn("Emergency optimization mode active - CPU: " + String.format("%.1f", cpuUsage) +
            "% , RAM: " + String.format("%.1f", memoryPercentage) + "%");

        int removedEntities = removeExcessEntitiesAggressively();
        maximizeHibernateSettings();
        reduceWorldActivity();
        forceMemoryOptimizations(memoryPercentage);

        if (removedEntities > 0) {
            LoggerUtils.info("Emergency optimizer removed " + removedEntities + " excess projectiles.");
        }
    }

    private void applyAggressiveOptimizations(double cpuUsage, double memoryPercentage) {
        LoggerUtils.debug("Aggressive optimization mode - CPU: " + String.format("%.1f", cpuUsage) +
            "% , RAM: " + String.format("%.1f", memoryPercentage) + "%");

        int removed = removeExcessEntities();
        increaseHibernateRadius();
        optimizeChunkLoading();
        throttleEntityProcessing();

        if (removed > 0) {
            LoggerUtils.debug("Aggressive optimizer removed " + removed + " excess arrows.");
        }
    }

    private void applyRecoveryOptimizations() {
        LoggerUtils.info("System recovered - restoring normal optimization settings");
        restoreNormalHibernateSettings();
        restoreNormalEntityProcessing();
    }

    /** Remove excess entities (arrows only - passive mobs/items protected). */
    private int removeExcessEntitiesAggressively() {
        int removedTotal = 0;

        for (World world : Bukkit.getWorlds()) {
            removedTotal += removeEntitiesByType(world, EntityType.ARROW, 400);
            removedTotal += removeEntitiesByType(world, EntityType.SPECTRAL_ARROW, 300);
        }

        return removedTotal;
    }

    /** Remove excess entities (arrows only). */
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
            if (removed >= toRemove) {
                break;
            }

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
            hm.setRuntimeRadius(48);
        }
    }

    private void maximizeHibernateSettings() {
        HibernateManager hm = plugin.getHibernateManager();
        if (hm != null && hm.isRunning()) {
            hm.setRuntimeRadius(32);
        }
    }

    private void restoreNormalHibernateSettings() {
        HibernateManager hm = plugin.getHibernateManager();
        if (hm != null && hm.isRunning()) {
            hm.resetRuntimeRadius();
        }
    }

    private void optimizeChunkLoading() {
        MemorySaver ms = plugin.getMemorySaver();
        if (ms != null && ms.isEnabled()) {
            ms.clearCache();
        }
    }

    private void throttleEntityProcessing() {
        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(true);
        }
        if (plugin.getAdvancedEntityOptimizer() != null && !plugin.getAdvancedEntityOptimizer().isEnabled()) {
            plugin.getAdvancedEntityOptimizer().start();
        }
    }

    private void restoreNormalEntityProcessing() {
        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(false);
        }
    }

    private void reduceWorldActivity() {
        if (plugin.getDynamicViewDistance() != null && plugin.getDynamicViewDistance().isRunning()) {
            plugin.getDynamicViewDistance().applyProfileTarget(4);
        }
    }

    private void forceMemoryOptimizations(double memoryPercentage) {
        MemorySaver memorySaver = plugin.getMemorySaver();
        if (memorySaver != null) {
            memorySaver.clearCache();
        }

        if (TPSUtils.getTPS() > 15.0 && memoryPercentage >= 90.0) {
            MemoryUtils.suggestGarbageCollection();
            LoggerUtils.debug("Suggested garbage collection during emergency optimization");
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
        return Math.max(0, (20.0 - tps) * 7.5);
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
