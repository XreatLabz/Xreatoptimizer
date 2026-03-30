package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * CPU/RAM monitor with non-destructive responses only.
 *
 * This class now only observes load and suggests safe memory cleanup. It no longer
 * removes entities, changes world behavior, or toggles gameplay-affecting systems.
 */
public class AdvancedCPURAMOptimizer {
    private final XreatOptimizer plugin;
    private org.bukkit.scheduler.BukkitTask optimizationTask;
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

        optimizationTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(
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

        peakCPUUsage = Math.max(peakCPUUsage, cpuUsage);
        peakRAMUsage = Math.max(peakRAMUsage, ramUsage);

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
                applyEmergencyOptimizations(memoryPercentage);
            } else {
                applyAggressiveOptimizations(memoryPercentage);
            }
        } else if (highUsageDetected && cpuUsage < 60 && memoryPercentage < 68) {
            highUsageDetected = false;
            LoggerUtils.info("System recovered - returning to monitoring mode");
        }

        if (System.currentTimeMillis() % 60000 < 2000) {
            LoggerUtils.info("Process CPU: " + String.format("%.1f", cpuUsage) + "%" +
                ", Heap: " + String.format("%.1f", memoryPercentage) + "% (" + ramUsage + "MB used, " +
                MemoryUtils.getMaxMemoryMB() + "MB max)");
        }
    }

    private void applyEmergencyOptimizations(double memoryPercentage) {
        LoggerUtils.warn("Emergency optimization mode active - using non-destructive mitigation only");
        forceMemoryOptimizations(memoryPercentage);
    }

    private void applyAggressiveOptimizations(double memoryPercentage) {
        LoggerUtils.debug("Aggressive optimization mode active - using non-destructive mitigation only");
        forceMemoryOptimizations(memoryPercentage);
    }

    private void forceMemoryOptimizations(double memoryPercentage) {
        MemorySaver memorySaver = plugin.getMemorySaver();
        if (memorySaver != null) {
            memorySaver.clearCache();
        }

        if (TPSUtils.getTPS() > 15.0 && memoryPercentage >= 90.0) {
            MemoryUtils.suggestGarbageCollection();
            LoggerUtils.debug("Suggested garbage collection during high memory pressure");
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
