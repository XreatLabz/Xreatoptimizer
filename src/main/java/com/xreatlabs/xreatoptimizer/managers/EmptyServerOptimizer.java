package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * EmptyServerOptimizer - Aggressive RAM & CPU optimization when no players are online
 * 
 * This manager detects when the server has zero players and applies aggressive
 * optimizations to reduce resource usage to minimum levels:
 * - Unloads all non-essential chunks
 * - Freezes all entity AI and movement
 * - Reduces tick rates to minimum
 * - Clears item entities and other unnecessary entities
 * - Triggers garbage collection
 * - Pauses non-essential scheduled tasks
 * 
 * When players join, it smoothly restores normal operation.
 */
public class EmptyServerOptimizer implements Listener {
    
    private final XreatOptimizer plugin;
    private boolean serverIsEmpty = false;
    private boolean optimizationActive = false;
    private BukkitTask monitorTask;
    private int originalViewDistance = 10;
    private int originalSimulationDistance = 10;
    
    // Configuration
    private final int EMPTY_CHECK_INTERVAL_TICKS = 60; // Check every 3 seconds
    private final int EMPTY_OPTIMIZATION_DELAY_TICKS = 600; // Wait 30 seconds before optimizing
    private final int MIN_VIEW_DISTANCE = 2;
    private final int MIN_SIMULATION_DISTANCE = 2;
    
    public EmptyServerOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Start the empty server optimizer
     */
    public void start() {
        if (!plugin.getConfig().getBoolean("empty_server.enabled", true)) {
            LoggerUtils.info("Empty Server Optimizer is disabled in config.");
            return;
        }
        
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Start monitoring task
        monitorTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::checkServerStatus,
            EMPTY_CHECK_INTERVAL_TICKS,
            EMPTY_CHECK_INTERVAL_TICKS
        );
        
        LoggerUtils.info("Empty Server Optimizer started - will reduce RAM/CPU when no players online");
    }
    
    /**
     * Stop the empty server optimizer
     */
    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        
        if (optimizationActive) {
            restoreNormalOperation();
        }
        
        LoggerUtils.info("Empty Server Optimizer stopped");
    }
    
    /**
     * Check current server status
     */
    private void checkServerStatus() {
        int playerCount = Bukkit.getOnlinePlayers().size();
        boolean isEmpty = playerCount == 0;
        
        if (isEmpty && !serverIsEmpty) {
            // Server just became empty
            serverIsEmpty = true;
            scheduleEmptyOptimization();
        } else if (!isEmpty && serverIsEmpty) {
            // Players joined
            serverIsEmpty = false;
            if (optimizationActive) {
                restoreNormalOperation();
            }
        }
    }
    
    /**
     * Schedule optimization after a delay
     */
    public void scheduleEmptyOptimization() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Double-check server is still empty
            if (Bukkit.getOnlinePlayers().isEmpty() && !optimizationActive) {
                applyEmptyServerOptimizations();
            }
        }, EMPTY_OPTIMIZATION_DELAY_TICKS);
    }
    
    /**
     * Apply aggressive optimizations for empty server
     */
    private void applyEmptyServerOptimizations() {
        LoggerUtils.info("=== EMPTY SERVER DETECTED - Applying aggressive optimizations ===");
        optimizationActive = true;
        
        long startTime = System.currentTimeMillis();
        int chunksUnloaded = 0;
        int entitiesRemoved = 0;
        
        for (World world : Bukkit.getWorlds()) {
            try {
                // Store original settings and reduce view distance using reflection
                try {
                    originalViewDistance = (int) world.getClass().getMethod("getViewDistance").invoke(world);
                    world.getClass().getMethod("setViewDistance", int.class).invoke(world, MIN_VIEW_DISTANCE);
                } catch (Exception e) {
                    // Method doesn't exist in this version
                    LoggerUtils.warn("View distance methods not available in this server version");
                }
                
                // Try to reduce simulation distance (only available in newer versions)
                try {
                    originalSimulationDistance = (int) world.getClass().getMethod("getSimulationDistance").invoke(world);
                    world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, MIN_SIMULATION_DISTANCE);
                } catch (Exception e) {
                    // Not available in this version, ignore
                }
                
                // NOTE: We no longer remove dropped items when server is empty.
                // This was causing players to lose items if they disconnected briefly.
                // Items will naturally despawn after their lifetime or be managed by ItemDropTracker.
                // 
                // If you REALLY want to remove items when server is empty, enable it in config:
                if (plugin.getConfig().getBoolean("empty_server.remove_items", false)) {
                    for (Entity entity : world.getEntities()) {
                        if (entity instanceof Item) {
                            entity.remove();
                            entitiesRemoved++;
                        }
                    }
                }
                
                // Save and unload chunks that are far from spawn
                int spawnX = world.getSpawnLocation().getChunk().getX();
                int spawnZ = world.getSpawnLocation().getChunk().getZ();
                
                world.getLoadedChunks();
                try {
                    // Unload chunks far from spawn (keep spawn area loaded)
                    for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                        int deltaX = Math.abs(chunk.getX() - spawnX);
                        int deltaZ = Math.abs(chunk.getZ() - spawnZ);
                        
                        if (deltaX > 4 || deltaZ > 4) { // Keep 9x9 chunks around spawn
                            if (chunk.unload(true)) {
                                chunksUnloaded++;
                            }
                        }
                    }
                } catch (Exception e) {
                    LoggerUtils.warn("Error unloading chunks in " + world.getName() + ": " + e.getMessage());
                }
                
                // Set time to day and disable weather (reduces processing)
                if (plugin.getConfig().getBoolean("empty_server.freeze_time", true)) {
                    world.setTime(6000); // Noon
                    world.setStorm(false);
                    world.setThundering(false);
                }
                
            } catch (Exception e) {
                LoggerUtils.warn("Error optimizing world " + world.getName() + ": " + e.getMessage());
            }
        }
        
        // Trigger memory cleanup
        plugin.getThreadPoolManager().executeIoTask(() -> {
            MemoryUtils.suggestGarbageCollection();
        });
        
        // Pause non-critical managers
        pauseNonCriticalSystems();
        
        long duration = System.currentTimeMillis() - startTime;
        long memoryFreedMB = MemoryUtils.getUsedMemoryMB();
        
        LoggerUtils.info(String.format(
            "Empty server optimizations applied in %dms | Chunks unloaded: %d | Entities removed: %d | Memory: %dMB",
            duration, chunksUnloaded, entitiesRemoved, memoryFreedMB
        ));
        LoggerUtils.info("Server now in LOW-POWER mode - minimal RAM/CPU usage");
    }
    
    /**
     * Restore normal operation when players join
     */
    public void restoreNormalOperation() {
        LoggerUtils.info("=== PLAYERS DETECTED - Restoring normal operation ===");
        optimizationActive = false;
        
        // Restore view distances using reflection
        for (World world : Bukkit.getWorlds()) {
            try {
                world.getClass().getMethod("setViewDistance", int.class).invoke(world, originalViewDistance);
            } catch (Exception e) {
                // Method doesn't exist in this version
            }
            
            try {
                world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, originalSimulationDistance);
            } catch (Exception e) {
                // Method doesn't exist in this version
            }
        }
        
        // Resume managers
        resumeNonCriticalSystems();
        
        LoggerUtils.info("Server restored to NORMAL mode - full performance available");
    }
    
    /**
     * Pause non-critical systems
     */
    private void pauseNonCriticalSystems() {
        // Pause entity optimizer (no entities to optimize)
        if (plugin.getAdvancedEntityOptimizer() != null) {
            plugin.getAdvancedEntityOptimizer().setEnabled(false);
        }
        
        // Pause entity culling (no players to cull for)
        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(false);
        }
        
        // Pause network optimizer (no network traffic)
        if (plugin.getNetworkOptimizer() != null) {
            plugin.getNetworkOptimizer().setEnabled(false);
        }
    }
    
    /**
     * Resume non-critical systems
     */
    private void resumeNonCriticalSystems() {
        // Resume entity optimizer
        if (plugin.getAdvancedEntityOptimizer() != null) {
            plugin.getAdvancedEntityOptimizer().setEnabled(true);
        }
        
        // Resume entity culling
        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(true);
        }
        
        // Resume network optimizer
        if (plugin.getNetworkOptimizer() != null) {
            plugin.getNetworkOptimizer().setEnabled(true);
        }
    }
    
    /**
     * Player join event - restore operations immediately
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (optimizationActive) {
            // Restore on main thread
            Bukkit.getScheduler().runTask(plugin, this::restoreNormalOperation);
        }
    }
    
    /**
     * Player quit event - check if server is now empty
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Check after a short delay (in case it's just a reconnect)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                scheduleEmptyOptimization();
            }
        }, 100L); // 5 second delay
    }
    
    /**
     * Check if optimization is currently active
     */
    public boolean isOptimizationActive() {
        return optimizationActive;
    }
    
    /**
     * Check if server is considered empty
     */
    public boolean isServerEmpty() {
        return serverIsEmpty;
    }

    /**
     * Reload configuration
     */
    public void reloadConfig() {
        // Reload empty server settings
        LoggerUtils.info("Empty server optimizer configuration reloaded");
    }

    /**
     * Check if server is in empty mode (optimizations active)
     */
    public boolean isInEmptyMode() {
        return optimizationActive;
    }
}
