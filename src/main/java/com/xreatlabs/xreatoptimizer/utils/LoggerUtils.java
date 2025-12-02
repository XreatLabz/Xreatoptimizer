package com.xreatlabs.xreatoptimizer.utils;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.Bukkit;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for logging with enhanced formatting
 */
public class LoggerUtils {
    private static final XreatOptimizer plugin = XreatOptimizer.getInstance();
    private static final Logger logger = plugin.getLogger();
    
    /**
     * Logs an info message with plugin prefix
     * @param message The message to log
     */
    public static void info(String message) {
        logger.info("[XreatOptimizer] " + message);
        addToWebDashboard("INFO", message);
    }
    
    /**
     * Logs a warning message with plugin prefix
     * @param message The message to log
     */
    public static void warn(String message) {
        logger.warning("[XreatOptimizer] " + message);
        addToWebDashboard("WARN", message);
    }
    
    /**
     * Logs an error message with plugin prefix
     * @param message The message to log
     */
    public static void error(String message) {
        logger.severe("[XreatOptimizer] " + message);
        addToWebDashboard("ERROR", message);
    }
    
    /**
     * Logs an error message with exception
     * @param message The message to log
     * @param throwable The exception to log
     */
    public static void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, "[XreatOptimizer] " + message, throwable);
        addToWebDashboard("ERROR", message + ": " + throwable.getMessage());
    }
    
    /**
     * Adds log entry to web dashboard if available
     */
    private static void addToWebDashboard(String level, String message) {
        try {
            if (plugin != null && plugin.getWebDashboard() != null && plugin.getWebDashboard().isRunning()) {
                plugin.getWebDashboard().addLogEntry(level, message);
            }
        } catch (Exception ignored) {
            // Ignore - dashboard may not be initialized
        }
    }
    
    /**
     * Logs a debug message if debug mode is enabled
     * @param message The debug message to log
     */
    public static void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[XreatOptimizer-DEBUG] " + message);
        }
    }
    
    /**
     * Logs performance metrics
     * @param metric The metric name
     * @param value The value to log
     */
    public static void logPerformance(String metric, Object value) {
        debug(metric + ": " + value.toString());
    }
    
    /**
     * Broadcasts a message to all players and logs it
     * @param message The message to broadcast and log
     */
    public static void broadcastAndLog(String message) {
        Bukkit.broadcastMessage(message);
        info("Broadcast: " + message);
    }
}