package com.xreatlabs.xreatoptimizer.utils;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.Bukkit;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerUtils {
    private static Logger logger;
    
    private static Logger getLogger() {
        if (logger == null) {
            XreatOptimizer plugin = XreatOptimizer.getInstance();
            if (plugin != null) {
                logger = plugin.getLogger();
            }
        }
        return logger;
    }
    
    private static boolean isQuietMode() {
        try {
            XreatOptimizer plugin = XreatOptimizer.getInstance();
            if (plugin != null && plugin.getConfig() != null) {
                return plugin.getConfig().getBoolean("general.quiet_mode", false);
            }
        } catch (Exception ignored) {
        }
        return false;
    }
    
    private static XreatOptimizer getPlugin() {
        return XreatOptimizer.getInstance();
    }
    
    public static void info(String message) {
        if (!isQuietMode() && getLogger() != null) {
            getLogger().info(message);
        }
        addToWebDashboard("INFO", message);
    }
    
    public static void warn(String message) {
        if (!isQuietMode() && getLogger() != null) {
            getLogger().warning(message);
        }
        addToWebDashboard("WARN", message);
    }
    
    public static void error(String message) {
        if (getLogger() != null) {
            getLogger().severe(message);
        }
        addToWebDashboard("ERROR", message);
    }
    
    public static void error(String message, Throwable throwable) {
        if (getLogger() != null) {
            getLogger().log(Level.SEVERE, message, throwable);
        }
        addToWebDashboard("ERROR", message + ": " + throwable.getMessage());
    }
    
    private static void addToWebDashboard(String level, String message) {
        try {
            XreatOptimizer plugin = getPlugin();
            if (plugin != null && plugin.getWebDashboard() != null && plugin.getWebDashboard().isRunning()) {
                plugin.getWebDashboard().addLogEntry(level, message);
            }
        } catch (Exception ignored) {
        }
    }
    
    public static void debug(String message) {
        try {
            XreatOptimizer plugin = getPlugin();
            if (plugin != null && plugin.getConfig().getBoolean("debug", false) && !isQuietMode() && getLogger() != null) {
                getLogger().info("[DEBUG] " + message);
            }
        } catch (Exception ignored) {
        }
    }
    
    public static void logPerformance(String metric, Object value) {
        debug(metric + ": " + value.toString());
    }
    
    public static void broadcastAndLog(String message) {
        Bukkit.broadcastMessage(message);
        info("Broadcast: " + message);
    }
    
    public static void startup(String message) {
        if (getLogger() != null) {
            getLogger().info(message);
        }
        addToWebDashboard("INFO", message);
    }
}
