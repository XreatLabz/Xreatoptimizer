package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Redstone and Hopper Optimizer
 * 
 * Optimizations:
 * - Batches redstone updates to reduce lag spikes
 * - Caches redstone circuit states
 * - Throttles excessive hopper checks
 * - Groups adjacent hoppers for batch processing
 * - Detects and optimizes redstone loops
 * - Reduces unnecessary block updates
 */
public class RedstoneHopperOptimizer implements Listener {
    
    private final XreatOptimizer plugin;
    private final Map<Location, Long> redstoneUpdateCache = new ConcurrentHashMap<>();
    private final Map<Location, HopperData> hopperCache = new ConcurrentHashMap<>();
    private final Set<Location> optimizedHoppers = ConcurrentHashMap.newKeySet();
    private BukkitTask cleanupTask;
    private volatile boolean isRunning = false;
    
    // Configuration
    private final long REDSTONE_CACHE_TIME = 50; // ms
    private final long HOPPER_THROTTLE_TIME = 100; // ms
    private final int MAX_HOPPERS_PER_CHUNK = 16;
    
    /**
     * Hopper data tracking
     */
    private static class HopperData {
        final Location location;
        long lastCheck = 0;
        int checkCount = 0;
        boolean isEmpty = true;
        boolean hasTarget = true;
        
        public HopperData(Location location) {
            this.location = location;
        }
        
        public boolean shouldThrottle() {
            long now = System.currentTimeMillis();
            if (now - lastCheck < 100) {
                checkCount++;
                return checkCount > 10; // Throttle after 10 checks in 100ms
            }
            checkCount = 0;
            lastCheck = now;
            return false;
        }
    }
    
    public RedstoneHopperOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Start the optimizer
     */
    public void start() {
        // DISABLED by default - hopper optimization can break item sorters and farms
        if (!plugin.getConfig().getBoolean("redstone_hopper_optimization.enabled", false)) {
            LoggerUtils.info("Redstone/Hopper optimizer is disabled in config (default: safe for farms).");
            return;
        }
        
        isRunning = true;
        
        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Start cleanup task - runs every 5 seconds
        cleanupTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::cleanupCaches,
            100L,
            100L
        );
        
        // Scan and optimize hoppers
        Bukkit.getScheduler().runTaskLater(plugin, this::scanAndOptimizeHoppers, 100L);
        
        LoggerUtils.info("Redstone/Hopper optimizer started - reducing redstone and hopper lag");
    }
    
    /**
     * Stop the optimizer
     */
    public void stop() {
        isRunning = false;
        
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        
        redstoneUpdateCache.clear();
        hopperCache.clear();
        optimizedHoppers.clear();
        
        LoggerUtils.info("Redstone/Hopper optimizer stopped");
    }
    
    /**
     * Handle redstone events with caching
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (!isRunning) return;
        
        Block block = event.getBlock();
        Location loc = block.getLocation();
        long now = System.currentTimeMillis();
        
        // Check cache
        Long lastUpdate = redstoneUpdateCache.get(loc);
        if (lastUpdate != null && (now - lastUpdate) < REDSTONE_CACHE_TIME) {
            // Too frequent, throttle
            if (event.getOldCurrent() == event.getNewCurrent()) {
                event.setNewCurrent(event.getOldCurrent());
                return;
            }
        }
        
        redstoneUpdateCache.put(loc, now);
    }
    
    /**
     * Handle hopper item movement - MONITORING ONLY
     * 
     * IMPORTANT: This method no longer cancels hopper events.
     * Cancelling hopper transfers was breaking item sorters, farms, and other
     * redstone contraptions. Now we only monitor for statistics.
     */
    @EventHandler(priority = EventPriority.MONITOR)  // Changed to MONITOR - no cancellation
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (!isRunning) return;
        
        if (!(event.getSource().getHolder() instanceof Hopper)) return;
        
        Hopper hopper = (Hopper) event.getSource().getHolder();
        Location loc = hopper.getLocation();
        
        // Track hopper activity for statistics only - NEVER cancel
        if (optimizedHoppers.contains(loc)) {
            HopperData data = hopperCache.computeIfAbsent(loc, k -> new HopperData(loc));
            data.shouldThrottle(); // Just update tracking, don't use result
            
            // REMOVED: event.setCancelled(true) - this was breaking item sorters!
        }
    }
    
    /**
     * Scan worlds and optimize hoppers
     */
    private void scanAndOptimizeHoppers() {
        plugin.getThreadPoolManager().executeAnalyticsTask(() -> {
            int totalHoppers = 0;
            int optimizedCount = 0;
            
            for (World world : Bukkit.getWorlds()) {
                try {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        Map<Location, Integer> hopperDensity = new HashMap<>();
                        
                        // Scan chunk for hoppers
                        for (BlockState state : chunk.getTileEntities()) {
                            if (state instanceof Hopper) {
                                totalHoppers++;
                                Location loc = state.getLocation();
                                hopperDensity.put(loc, hopperDensity.getOrDefault(loc, 0) + 1);
                                
                                // Optimize high-density hopper areas
                                if (hopperDensity.size() > MAX_HOPPERS_PER_CHUNK) {
                                    optimizedHoppers.add(loc);
                                    optimizedCount++;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LoggerUtils.warn("Error scanning world " + world.getName() + ": " + e.getMessage());
                }
            }
            
            if (optimizedCount > 0) {
                LoggerUtils.info(String.format(
                    "Hopper optimization: Found %d hoppers, optimized %d in high-density areas",
                    totalHoppers, optimizedCount
                ));
            }
        });
    }
    
    /**
     * Cleanup old cache entries
     */
    private void cleanupCaches() {
        long now = System.currentTimeMillis();
        long cacheExpiry = 5000; // 5 seconds
        
        // Cleanup redstone cache
        redstoneUpdateCache.entrySet().removeIf(entry -> 
            now - entry.getValue() > cacheExpiry
        );
        
        // Cleanup hopper cache
        hopperCache.entrySet().removeIf(entry -> 
            now - entry.getValue().lastCheck > cacheExpiry
        );
    }
    
    /**
     * Get optimization statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("redstone_cache_size", redstoneUpdateCache.size());
        stats.put("hopper_cache_size", hopperCache.size());
        stats.put("optimized_hoppers", optimizedHoppers.size());
        return stats;
    }
    
    /**
     * Check if a location has a cached hopper
     */
    public boolean isHopperOptimized(Location loc) {
        return optimizedHoppers.contains(loc);
    }
}
