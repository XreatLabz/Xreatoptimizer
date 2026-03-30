package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RedstoneHopperOptimizer implements Listener {

    private final XreatOptimizer plugin;
    private final Map<Location, Long> redstoneUpdateCache = new ConcurrentHashMap<>();
    private final Map<Location, HopperData> hopperCache = new ConcurrentHashMap<>();
    private final Set<Location> optimizedHoppers = ConcurrentHashMap.newKeySet();
    private BukkitTask cleanupTask;
    private volatile boolean isRunning = false;

    private final int MAX_HOPPERS_PER_CHUNK = 16;

    private static class HopperData {
        final Location location;
        long lastCheck = 0;
        int moveEvents = 0;

        HopperData(Location location) {
            this.location = location;
        }

        void markMove() {
            long now = System.currentTimeMillis();
            if (now - lastCheck > 1000) {
                moveEvents = 0;
            }
            moveEvents++;
            lastCheck = now;
        }
    }

    public RedstoneHopperOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("redstone_hopper_optimization.enabled", false)) {
            LoggerUtils.info("Redstone/Hopper optimizer is disabled in config (default: safe for farms).");
            return;
        }

        isRunning = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        cleanupTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::cleanupCaches,
            100L,
            100L
        );

        Bukkit.getScheduler().runTaskLater(plugin, this::scanAndOptimizeHoppers, 100L);
        LoggerUtils.info("Redstone/Hopper optimizer started - monitoring high-density redstone and hopper activity");
    }

    public void stop() {
        isRunning = false;
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        redstoneUpdateCache.clear();
        hopperCache.clear();
        optimizedHoppers.clear();
        LoggerUtils.info("Redstone/Hopper optimizer stopped");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (!isRunning) return;
        redstoneUpdateCache.put(event.getBlock().getLocation(), System.currentTimeMillis());
    }

    /** Monitor hopper item movement for statistics only. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (!isRunning) return;
        if (!(event.getSource().getHolder() instanceof Hopper)) return;

        Hopper hopper = (Hopper) event.getSource().getHolder();
        Location loc = hopper.getLocation();
        HopperData data = hopperCache.computeIfAbsent(loc, HopperData::new);
        data.markMove();
    }

    private void scanAndOptimizeHoppers() {
        plugin.getThreadPoolManager().executeAnalyticsTask(() -> {
            int totalHoppers = 0;
            int optimizedCount = 0;

            for (World world : Bukkit.getWorlds()) {
                try {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        Map<Location, Integer> hopperDensity = new HashMap<>();
                        for (BlockState state : chunk.getTileEntities()) {
                            if (state instanceof Hopper) {
                                totalHoppers++;
                                Location loc = state.getLocation();
                                hopperDensity.put(loc, hopperDensity.getOrDefault(loc, 0) + 1);
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
                    "Hopper pressure scan: Found %d hoppers, marked %d in high-density areas",
                    totalHoppers, optimizedCount
                ));
            }
        });
    }

    private void cleanupCaches() {
        long now = System.currentTimeMillis();
        long cacheExpiry = 5000;
        redstoneUpdateCache.entrySet().removeIf(entry -> now - entry.getValue() > cacheExpiry);
        hopperCache.entrySet().removeIf(entry -> now - entry.getValue().lastCheck > cacheExpiry);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("redstone_cache_size", redstoneUpdateCache.size());
        stats.put("hopper_cache_size", hopperCache.size());
        stats.put("optimized_hoppers", optimizedHoppers.size());
        return stats;
    }

    public boolean isHopperOptimized(Location loc) {
        return optimizedHoppers.contains(loc);
    }
}
