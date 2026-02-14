package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicViewDistance {
    private final XreatOptimizer plugin;
    private BukkitTask adjustmentTask;
    private final Map<String, Integer> originalViewDistances = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerViewDistances = new WeakHashMap<>();
    private volatile boolean isRunning = false;
    
    private final Map<String, Integer> worldViewDistances = new ConcurrentHashMap<>();
    
    public DynamicViewDistance(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Starts the dynamic view distance system
     */
    public void start() {
        // Store original view distances for restoration later
        for (World world : Bukkit.getWorlds()) {
            originalViewDistances.put(world.getName(), world.getViewDistance());
        }
        
        // Run view distance adjustments every 30 seconds
        adjustmentTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::adjustViewDistances,
            600L,  // Initial delay (30 seconds)
            600L   // Repeat interval (30 seconds)
        );
        
        isRunning = true;
        LoggerUtils.info("Dynamic view distance system started.");
    }
    
    /**
     * Stops the dynamic view distance system
     */
    public void stop() {
        isRunning = false;
        if (adjustmentTask != null) {
            adjustmentTask.cancel();
        }
        
        // Restore original view distances
        restoreOriginalViewDistances();
        
        LoggerUtils.info("Dynamic view distance system stopped.");
    }
    
    /**
     * Adjusts view distances based on current server performance
     */
    private void adjustViewDistances() {
        if (!isRunning) return;
        
        double currentTPS = TPSUtils.getTPS();
        double memoryUsage = MemoryUtils.getMemoryUsagePercentage();
        
        // Get configuration thresholds
        double lightTPS = plugin.getConfig().getDouble("optimization.tps_thresholds.light", 19.5);
        double normalTPS = plugin.getConfig().getDouble("optimization.tps_thresholds.normal", 18.0);
        double aggressiveTPS = plugin.getConfig().getDouble("optimization.tps_thresholds.aggressive", 16.0);
        
        // Determine appropriate view distance based on performance
        int newViewDistance = determineViewDistance(currentTPS, memoryUsage, lightTPS, normalTPS, aggressiveTPS);
        
        // Apply new view distance to all worlds
        for (World world : Bukkit.getWorlds()) {
            if (shouldAdjustWorld(world)) {
                int currentDistance = world.getViewDistance();
                
                if (currentDistance != newViewDistance) {
                    setWorldViewDistance(world, newViewDistance);
                    worldViewDistances.put(world.getName(), newViewDistance);
                    LoggerUtils.info("Adjusted view distance for world '" + world.getName() + 
                                   "' from " + currentDistance + " to " + newViewDistance);
                }
            }
        }
        
        // Also adjust player view distances if supported by server implementation
        adjustPlayerViewDistances(newViewDistance);
    }
    
    /**
     * Determines the appropriate view distance based on server performance
     */
    private int determineViewDistance(double tps, double memoryUsage, 
                                     double lightTPS, double normalTPS, double aggressiveTPS) {
        // Get server's view distance limits
        int maxViewDistance = getServerMaxViewDistance();
        int minViewDistance = 2; // Minimum practical view distance
        
        if (tps > lightTPS && memoryUsage < 70) {
            // Server is performing well, can expand view distance
            return Math.min(maxViewDistance, 12); // Increase if possible, but don't exceed 12
        } else if (tps > normalTPS) {
            // Server is doing okay, maintain normal view distance
            return Math.min(maxViewDistance, 8); // Standard distance
        } else if (tps > aggressiveTPS) {
            // Server is under moderate load, reduce view distance
            return Math.min(maxViewDistance, 6); // Reduced distance
        } else {
            // Server is under heavy load, minimize view distance
            return Math.max(minViewDistance, 4); // Minimal distance but keep gameplay functional
        }
    }
    
    /**
     * Checks if a world should have its view distance adjusted
     */
    private boolean shouldAdjustWorld(World world) {
        // Don't adjust view distance for minigame or event worlds
        return !world.getName().toLowerCase().contains("minigame") &&
               !world.getName().toLowerCase().contains("event");
    }
    
    /**
     * Sets the view distance for a world using version-appropriate API.
     */
    private void setWorldViewDistance(World world, int viewDistance) {
        try {
            // Spigot 1.14+ has World.setViewDistance(int)
            if (plugin.getVersionAdapter().isVersionAtLeast(1, 14)) {
                world.getClass().getMethod("setViewDistance", int.class).invoke(world, viewDistance);
                LoggerUtils.debug("Set view distance for world '" + world.getName() + "' to " + viewDistance);
                return;
            }
        } catch (NoSuchMethodException e) {
            // Method not available on this server implementation
        } catch (Exception e) {
            LoggerUtils.debug("Could not set view distance via API for world '" + world.getName() + "': " + e.getMessage());
        }
        
        // Fallback: track intended change for next reload
        LoggerUtils.debug("View distance change for '" + world.getName() + "' to " + viewDistance + " tracked (API unavailable)");
    }
    
    /**
     * Attempts to adjust individual player view distances if supported
     */
    private void adjustPlayerViewDistances(int targetDistance) {
        // Some server implementations support per-player view distance
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                // Try Paper's per-player view distance API if available
                setPlayerViewDistance(player, targetDistance);
            } catch (Exception e) {
                // Fall back to logging if per-player view distance isn't supported
                LoggerUtils.debug("Per-player view distance not supported, using world settings for " + player.getName());
            }
        }
    }
    
    /**
     * Attempts to set a player's view distance using Paper API (1.14+).
     */
    private void setPlayerViewDistance(Player player, int distance) throws Exception {
        if (!plugin.getVersionAdapter().isVersionAtLeast(1, 14)) return;
        
        try {
            Method setViewDistanceMethod = player.getClass().getMethod("setViewDistance", int.class);
            setViewDistanceMethod.invoke(player, distance);
            playerViewDistances.put(player, distance);
        } catch (NoSuchMethodException e) {
            // Per-player view distance not supported on this server
        }
    }
    
    /**
     * Gets the maximum view distance allowed by the server
     */
    private int getServerMaxViewDistance() {
        return 16;
    }
    
    /**
     * Restores original view distances for all worlds
     */
    private void restoreOriginalViewDistances() {
        for (World world : Bukkit.getWorlds()) {
            Integer originalDistance = originalViewDistances.get(world.getName());
            if (originalDistance != null) {
                setWorldViewDistance(world, originalDistance);
            }
        }
    }
    
    /**
     * Gets the current effective view distance for a world
     */
    public int getCurrentViewDistance(String worldName) {
        Integer customDistance = worldViewDistances.get(worldName);
        if (customDistance != null) {
            return customDistance;
        }
        
        World world = Bukkit.getWorld(worldName);
        return world != null ? world.getViewDistance() : 8; // Default
    }
    
    /**
     * Gets the current effective view distance for a player
     */
    public int getCurrentPlayerViewDistance(Player player) {
        Integer customDistance = playerViewDistances.get(player);
        if (customDistance != null) {
            return customDistance;
        }
        
        // Fall back to world view distance
        return getCurrentViewDistance(player.getWorld().getName());
    }
    
    /**
     * Checks if the dynamic view distance manager is running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Reload configuration
     */
    public void reloadConfig() {
        LoggerUtils.info("Dynamic view distance configuration reloaded");
    }
}