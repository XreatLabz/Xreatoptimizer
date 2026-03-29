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
    private final Map<String, Integer> worldViewDistances = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;
    private boolean enabled = true;
    private int lightDistance = 10;
    private int normalDistance = 8;
    private int aggressiveDistance = 6;
    private int emergencyDistance = 4;
    private int minViewDistance = 4;
    private int maxViewDistance = 12;

    public DynamicViewDistance(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("dynamic_view_distance.enabled", true);
        minViewDistance = Math.max(2, plugin.getConfig().getInt("dynamic_view_distance.min", 4));
        maxViewDistance = Math.max(minViewDistance, plugin.getConfig().getInt("dynamic_view_distance.max", 12));

        lightDistance = clamp(plugin.getConfig().getInt("dynamic_view_distance.light", 10));
        normalDistance = clamp(plugin.getConfig().getInt("dynamic_view_distance.normal", 8));
        aggressiveDistance = clamp(plugin.getConfig().getInt("dynamic_view_distance.aggressive", 6));
        emergencyDistance = clamp(plugin.getConfig().getInt("dynamic_view_distance.emergency", 4));
    }

    /** Starts the dynamic view distance system. */
    public void start() {
        if (isRunning) {
            return;
        }

        loadConfig();
        if (!enabled) {
            LoggerUtils.info("Dynamic view distance is disabled in config.");
            return;
        }

        originalViewDistances.clear();
        for (World world : Bukkit.getWorlds()) {
            originalViewDistances.put(world.getName(), world.getViewDistance());
        }

        adjustmentTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::adjustViewDistances,
            600L,
            600L
        );

        isRunning = true;
        LoggerUtils.info("Dynamic view distance system started.");
    }

    /** Stops the dynamic view distance system. */
    public void stop() {
        isRunning = false;
        if (adjustmentTask != null) {
            adjustmentTask.cancel();
            adjustmentTask = null;
        }

        restoreOriginalViewDistances();
        worldViewDistances.clear();
        playerViewDistances.clear();

        LoggerUtils.info("Dynamic view distance system stopped.");
    }

    /** Adjusts view distances based on current server performance. */
    private void adjustViewDistances() {
        if (!isRunning) {
            return;
        }

        double currentTPS = TPSUtils.getTPS();
        double memoryUsage = MemoryUtils.getMemoryUsagePercentage();

        double lightTPS = plugin.getConfig().getDouble("optimization.tps_thresholds.light", 19.5);
        double normalTPS = plugin.getConfig().getDouble("optimization.tps_thresholds.normal", 18.0);
        double aggressiveTPS = plugin.getConfig().getDouble("optimization.tps_thresholds.aggressive", 16.0);

        int newViewDistance = determineViewDistance(currentTPS, memoryUsage, lightTPS, normalTPS, aggressiveTPS);

        for (World world : Bukkit.getWorlds()) {
            if (!shouldAdjustWorld(world)) {
                continue;
            }

            int currentDistance = getCurrentViewDistance(world.getName());
            if (currentDistance != newViewDistance) {
                setWorldViewDistance(world, newViewDistance);
                worldViewDistances.put(world.getName(), newViewDistance);
                LoggerUtils.info("Adjusted view distance for world '" + world.getName() +
                    "' from " + currentDistance + " to " + newViewDistance);
            }
        }

        adjustPlayerViewDistances(newViewDistance);
    }

    private int determineViewDistance(double tps, double memoryUsage,
                                      double lightTPS, double normalTPS, double aggressiveTPS) {
        if (tps > lightTPS && memoryUsage < 70) {
            return lightDistance;
        }
        if (tps > normalTPS) {
            return normalDistance;
        }
        if (tps > aggressiveTPS) {
            return aggressiveDistance;
        }
        return emergencyDistance;
    }

    private boolean shouldAdjustWorld(World world) {
        String name = world.getName().toLowerCase();
        return !name.contains("minigame") && !name.contains("event");
    }

    /** Sets the view distance for a world using version-appropriate API. */
    public void setWorldViewDistance(World world, int viewDistance) {
        int clampedDistance = clamp(viewDistance);

        try {
            if (plugin.getVersionAdapter().isVersionAtLeast(1, 14)) {
                world.getClass().getMethod("setViewDistance", int.class).invoke(world, clampedDistance);
                LoggerUtils.debug("Set view distance for world '" + world.getName() + "' to " + clampedDistance);
                return;
            }
        } catch (NoSuchMethodException e) {
            // Method not available on this server implementation.
        } catch (Exception e) {
            LoggerUtils.debug("Could not set view distance via API for world '" + world.getName() + "': " + e.getMessage());
        }

        LoggerUtils.debug("View distance change for '" + world.getName() + "' to " + clampedDistance + " tracked (API unavailable)");
    }

    /** Attempts to adjust individual player view distances if supported. */
    private void adjustPlayerViewDistances(int targetDistance) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                setPlayerViewDistance(player, targetDistance);
            } catch (Exception e) {
                LoggerUtils.debug("Per-player view distance not supported, using world settings for " + player.getName());
            }
        }
    }

    /** Attempts to set a player's view distance using Paper API (1.14+). */
    private void setPlayerViewDistance(Player player, int distance) throws Exception {
        if (!plugin.getVersionAdapter().isVersionAtLeast(1, 14)) {
            return;
        }

        try {
            Method setViewDistanceMethod = player.getClass().getMethod("setViewDistance", int.class);
            setViewDistanceMethod.invoke(player, clamp(distance));
            playerViewDistances.put(player, clamp(distance));
        } catch (NoSuchMethodException e) {
            // Per-player view distance not supported on this server.
        }
    }

    /** Restores original view distances for all worlds. */
    private void restoreOriginalViewDistances() {
        for (World world : Bukkit.getWorlds()) {
            Integer originalDistance = originalViewDistances.get(world.getName());
            if (originalDistance != null) {
                setWorldViewDistance(world, originalDistance);
            }
        }
    }

    public int getCurrentViewDistance(String worldName) {
        Integer customDistance = worldViewDistances.get(worldName);
        if (customDistance != null) {
            return customDistance;
        }

        World world = Bukkit.getWorld(worldName);
        return world != null ? world.getViewDistance() : normalDistance;
    }

    public int getCurrentPlayerViewDistance(Player player) {
        Integer customDistance = playerViewDistances.get(player);
        if (customDistance != null) {
            return customDistance;
        }

        return getCurrentViewDistance(player.getWorld().getName());
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void applyProfileTarget(int viewDistance) {
        if (!isRunning) {
            return;
        }

        int clampedDistance = clamp(viewDistance);
        for (World world : Bukkit.getWorlds()) {
            if (!shouldAdjustWorld(world)) {
                continue;
            }
            setWorldViewDistance(world, clampedDistance);
            worldViewDistances.put(world.getName(), clampedDistance);
        }

        adjustPlayerViewDistances(clampedDistance);
    }

    /** Reload configuration. */
    public void reloadConfig() {
        boolean wasRunning = isRunning;
        if (wasRunning) {
            stop();
        } else {
            loadConfig();
        }

        if (plugin.getConfig().getBoolean("dynamic_view_distance.enabled", true)) {
            start();
        }
    }

    private int clamp(int value) {
        return Math.max(minViewDistance, Math.min(maxViewDistance, value));
    }
}
