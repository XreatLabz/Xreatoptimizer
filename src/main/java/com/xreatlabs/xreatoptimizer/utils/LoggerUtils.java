package com.xreatlabs.xreatoptimizer.utils;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.Bukkit;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerUtils {
    private static final XreatOptimizer plugin = XreatOptimizer.getInstance();
    private static final Logger logger = plugin.getLogger();
    
    public static void info(String message) {
        if (!isQuietMode()) {
            logger.info(message);
        }
        addToWebDashboard("INFO", message);
    }
    
    public static void warn(String message) {
        if (!isQuietMode()) {
            logger.warning(message);
        }
        addToWebDashboard("WARN", message);
    }
    
    public static void error(String message) {
        logger.severe(message);
        addToWebDashboard("ERROR", message);
    }
    
    public static void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
        addToWebDashboard("ERROR", message + ": " + throwable.getMessage());
    }
    
    private static boolean isQuietMode() {
        try {
            return plugin.getConfig().getBoolean("quiet_mode", false);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void addToWebDashboard(String level, String message) {
        try {
            if (plugin != null && plugin.getWebDashboard() != null && plugin.getWebDashboard().isRunning()) {
                plugin.getWebDashboard().addLogEntry(level, message);
            }
        } catch (Exception ignored) {
        }
    }
    
    public static void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false) && !isQuietMode()) {
            logger.info("[DEBUG] " + message);
        }
    }
    
    public static void logPerformance(String metric, Object value) {
        debug(metric + ": " + value.toString());
    }
    
    public static void broadcastAndLog(String message) {
        Bukkit.broadcastMessage(message);
        info("Broadcast: " + message);
    }
}
