package com.xreatlabs.xreatoptimizer.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

public class MemoryUtils {
    
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    public static long getUsedMemoryMB() {
        MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
        return heapMemoryUsage.getUsed() / (1024 * 1024);
    }
    
    public static long getMaxMemoryMB() {
        MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
        return heapMemoryUsage.getMax() / (1024 * 1024);
    }
    
    public static long getCommittedMemoryMB() {
        MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
        return heapMemoryUsage.getCommitted() / (1024 * 1024);
    }
    
    public static double getMemoryUsagePercentage() {
        long max = getMaxMemoryMB();
        if (max == 0) return 0;
        return (double) getUsedMemoryMB() / max * 100;
    }
    
    public static void suggestGarbageCollection() {
        System.gc();
    }
    
    public static boolean isMemoryUsageAbove(double percentageThreshold) {
        return getMemoryUsagePercentage() > percentageThreshold;
    }
    
    public static boolean isMemoryPressureHigh() {
        return isMemoryUsageAbove(80.0);
    }

    public static long getUsedMemory() {
        return getUsedMemoryMB();
    }

    public static long getMaxMemory() {
        return getMaxMemoryMB();
    }

    public static double getMemoryUsagePercent() {
        return getMemoryUsagePercentage();
    }
}
