package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.EntityUtils;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages automatic clearing of excess entities
 * 
 * IMPORTANT: This task is DISABLED by default to prevent any interference with gameplay.
 * It only clears truly excessive entities and never touches player-important entities.
 */
public class AutoClearTask {
    private final XreatOptimizer plugin;
    private BukkitTask clearTask;
    private volatile boolean isRunning = false;
    
    public AutoClearTask(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Starts the auto clear task system
     */
    public void start() {
        // Check if auto-clear is enabled in config (DISABLED by default)
        if (!plugin.getConfig().getBoolean("auto_clear.enabled", false)) {
            LoggerUtils.info("Auto clear task is disabled in config. Skipping start.");
            return;
        }
        
        int intervalSeconds = plugin.getConfig().getInt("clear_interval_seconds", 600); // 10 minutes default
        
        clearTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runClearCycle,
            intervalSeconds * 20L,  // Initial delay
            intervalSeconds * 20L   // Repeat interval
        );
        
        isRunning = true;
        LoggerUtils.info("Auto clear task started. Will run every " + intervalSeconds + " seconds.");
    }
    
    /**
     * Stops the auto clear task system
     */
    public void stop() {
        isRunning = false;
        if (clearTask != null) {
            clearTask.cancel();
        }
        LoggerUtils.info("Auto clear task stopped.");
    }
    
    /**
     * Runs a clear cycle to remove excess entities
     */
    private void runClearCycle() {
        if (!isRunning) return;
        
        // Double-check config in case it was changed
        if (!plugin.getConfig().getBoolean("auto_clear.enabled", false)) {
            return;
        }
        
        LoggerUtils.debug("Running auto clear cycle...");
        
        int totalRemoved = 0;
        
        // Process each world
        for (World world : Bukkit.getWorlds()) {
            int removed = clearExcessEntitiesInWorld(world);
            totalRemoved += removed;
            
            if (removed > 0) {
                LoggerUtils.debug("Cleared " + removed + " excess entities in world: " + world.getName());
            }
        }
        
        if (totalRemoved > 0) {
            LoggerUtils.info("Auto clear task completed. Removed " + totalRemoved + " excess entities across all worlds.");
        }
    }
    
    /**
     * Clears excess entities in a specific world based on thresholds
     * 
     * IMPORTANT: This method is very conservative and only removes truly excessive
     * entities that don't affect gameplay. It NEVER removes:
     * - Player-placed entities (armor stands, item frames, paintings, etc.)
     * - Vehicles (boats, minecarts)
     * - Player-created entities (iron golems, snow golems)
     * - Boss mobs
     * - Named entities
     * - Villagers or other important gameplay mobs
     * - Tamed animals
     */
    private int clearExcessEntitiesInWorld(World world) {
        // Safety check - if auto clear is somehow running when disabled, stop
        if (!plugin.getConfig().getBoolean("auto_clear.enabled", false)) {
            return 0;
        }
        
        int totalRemoved = 0;
        
        // ONLY clear stuck arrows on the ground (very high threshold)
        // These are arrows that have been in the world for a long time
        int arrowLimit = plugin.getConfig().getInt("auto_clear.arrow_limit", 500);
        if (arrowLimit > 0) {
            totalRemoved += EntityUtils.removeExcessEntities(world, EntityType.ARROW, arrowLimit);
            totalRemoved += EntityUtils.removeExcessEntities(world, EntityType.SPECTRAL_ARROW, arrowLimit);
        }
        
        // Note: We intentionally do NOT clear:
        // - Passive mobs (players may have farms or pets)
        // - Hostile mobs (would break mob farms and gameplay)
        // - Projectiles in flight (would break gameplay)
        // - Any player-placed or player-created entities
        // - Villagers, wandering traders, etc.
        // - Experience orbs (players earned them)
        // - Dropped items (handled separately by ItemDropTracker with warnings)
        
        return totalRemoved;
    }
    
    /**
     * Performs a one-time immediate clear of all excess entities
     * @return Number of entities removed
     */
    public int immediateClear() {
        int totalRemoved = 0;
        
        for (World world : Bukkit.getWorlds()) {
            totalRemoved += clearExcessEntitiesInWorld(world);
        }
        
        LoggerUtils.info("Immediate clear completed. Removed " + totalRemoved + " entities.");
        return totalRemoved;
    }
    
    /**
     * Clears specific entity types in a world
     * @param world World to clear in
     * @param type Entity type to clear
     * @param limit Maximum number to keep
     * @return Number of entities removed
     */
    public int clearSpecificType(World world, EntityType type, int limit) {
        int removed = EntityUtils.removeExcessEntities(world, type, limit);
        if (removed > 0) {
            LoggerUtils.info("Cleared " + removed + " " + type.name() + " entities in world " + world.getName());
        }
        return removed;
    }
    
    /**
     * Asynchronously clears entities to avoid main thread lag
     * @return CompletableFuture for tracking completion
     */
    public CompletableFuture<Integer> asyncClear() {
        return CompletableFuture.supplyAsync(this::immediateClear, plugin.getThreadPoolManager().getEntityCleanupPool());
    }
    
    /**
     * Checks if the auto clear task is running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Reload configuration
     */
    public void reloadConfig() {
        if (isRunning) {
            stop();
        }
        start();
    }

    /**
     * Manually clear entities across all worlds
     */
    public void clearEntities() {
        immediateClear();
    }
}