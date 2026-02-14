package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.EntityUtils;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

/** Manages automatic clearing of excess entities (disabled by default) */
public class AutoClearTask {
    private final XreatOptimizer plugin;
    private BukkitTask clearTask;
    private volatile boolean isRunning = false;
    
    public AutoClearTask(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        if (!plugin.getConfig().getBoolean("auto_clear.enabled", false)) {
            LoggerUtils.info("Auto clear task is disabled in config. Skipping start.");
            return;
        }
        
        int intervalSeconds = plugin.getConfig().getInt("clear_interval_seconds", 600);
        
        clearTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runClearCycle,
            intervalSeconds * 20L,
            intervalSeconds * 20L
        );
        
        isRunning = true;
        LoggerUtils.info("Auto clear task started. Will run every " + intervalSeconds + " seconds.");
    }
    
    public void stop() {
        isRunning = false;
        if (clearTask != null) {
            clearTask.cancel();
        }
        LoggerUtils.info("Auto clear task stopped.");
    }
    
    private void runClearCycle() {
        if (!isRunning) return;
        
        if (!plugin.getConfig().getBoolean("auto_clear.enabled", false)) {
            return;
        }
        
        LoggerUtils.debug("Running auto clear cycle...");
        
        int totalRemoved = 0;
        
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
    
    /** Clear excess entities conservatively (skips protected types) */
    private int clearExcessEntitiesInWorld(World world) {
        if (!plugin.getConfig().getBoolean("auto_clear.enabled", false)) {
            return 0;
        }
        
        int totalRemoved = 0;
        
        int arrowLimit = plugin.getConfig().getInt("auto_clear.arrow_limit", 500);
        if (arrowLimit > 0) {
            totalRemoved += EntityUtils.removeExcessEntities(world, EntityType.ARROW, arrowLimit);
            totalRemoved += EntityUtils.removeExcessEntities(world, EntityType.SPECTRAL_ARROW, arrowLimit);
        }
        
        return totalRemoved;
    }
    
    public int immediateClear() {
        int totalRemoved = 0;
        
        for (World world : Bukkit.getWorlds()) {
            totalRemoved += clearExcessEntitiesInWorld(world);
        }
        
        LoggerUtils.info("Immediate clear completed. Removed " + totalRemoved + " entities.");
        return totalRemoved;
    }
    
    public int clearSpecificType(World world, EntityType type, int limit) {
        int removed = EntityUtils.removeExcessEntities(world, type, limit);
        if (removed > 0) {
            LoggerUtils.info("Cleared " + removed + " " + type.name() + " entities in world " + world.getName());
        }
        return removed;
    }
    
    public CompletableFuture<Integer> asyncClear() {
        return CompletableFuture.supplyAsync(this::immediateClear, plugin.getThreadPoolManager().getEntityCleanupPool());
    }
    
    public boolean isRunning() {
        return isRunning;
    }

    public void reloadConfig() {
        if (isRunning) {
            stop();
        }
        start();
    }

    public void clearEntities() {
        immediateClear();
    }
}
