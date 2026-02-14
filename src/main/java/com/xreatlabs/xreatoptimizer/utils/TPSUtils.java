package com.xreatlabs.xreatoptimizer.utils;

import org.bukkit.Bukkit;

public class TPSUtils {
    
    public static double getTPS() {
        try {
            Object server = Bukkit.getServer();
            java.lang.reflect.Method getTPSMethod = server.getClass().getMethod("getTPS");
            double[] tpsArray = (double[]) getTPSMethod.invoke(server);
            if (tpsArray.length > 0) {
                return tpsArray[0];
            }
        } catch (Exception e) {
        }
        
        return 20.0;
    }
    
    public static double[] getTPSArray() {
        try {
            Object server = Bukkit.getServer();
            java.lang.reflect.Method getTPSMethod = server.getClass().getMethod("getTPS");
            return (double[]) getTPSMethod.invoke(server);
        } catch (Exception e) {
            return new double[]{20.0, 20.0, 20.0};
        }
    }
    
    public static boolean isTPSBelow(double threshold) {
        return getTPS() < threshold;
    }
    
    public static boolean isTPSDangerous() {
        return isTPSBelow(10.0);
    }
    
    public static double getAverageTickTime() {
        try {
            Object server = Bukkit.getServer();
            java.lang.reflect.Method getWorldTickTimesMethod = server.getClass().getMethod("getWorldTickTimes");
            long[] recentTickTimes = (long[]) getWorldTickTimesMethod.invoke(server);
            if (recentTickTimes.length > 0) {
                long sum = 0;
                for (long time : recentTickTimes) {
                    sum += time;
                }
                return (sum / (double) recentTickTimes.length) / 1_000_000.0;
            }
        } catch (Exception e) {
        }
        
        return 50.0;
    }
}
