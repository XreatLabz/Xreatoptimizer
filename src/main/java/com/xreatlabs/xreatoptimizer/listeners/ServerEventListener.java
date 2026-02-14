package com.xreatlabs.xreatoptimizer.listeners;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.EmptyServerOptimizer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class ServerEventListener implements Listener {

    private final XreatOptimizer plugin;
    private final EmptyServerOptimizer emptyServerOptimizer;

    public ServerEventListener(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.emptyServerOptimizer = plugin.getEmptyServerOptimizer();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (emptyServerOptimizer != null && emptyServerOptimizer.isInEmptyMode()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                emptyServerOptimizer.restoreNormalOperation();
            });
        }

        // Update performance monitor with new player count
        if (plugin.getPerformanceMonitor() != null) {
            plugin.getPerformanceMonitor().updatePlayerCount();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Check if server will be empty after this player leaves
        // Wait 5 seconds (100 ticks) to ensure player fully disconnected
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty() && emptyServerOptimizer != null) {
                emptyServerOptimizer.scheduleEmptyOptimization();
            }

            // Update performance monitor
            if (plugin.getPerformanceMonitor() != null) {
                plugin.getPerformanceMonitor().updatePlayerCount();
            }
        }, 100L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        // Server fully loaded - start optimization systems
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("Server fully loaded - optimization systems active");

                // Check if server started empty
                if (Bukkit.getOnlinePlayers().isEmpty() && emptyServerOptimizer != null) {
                    emptyServerOptimizer.scheduleEmptyOptimization();
                }
            }, 200L); // Wait 10 seconds after full load
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        // Track chunk loads for memory monitoring
        if (plugin.getPerformanceMonitor() != null) {
            plugin.getPerformanceMonitor().incrementChunkLoads();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Track chunk unloads
        if (plugin.getPerformanceMonitor() != null) {
            plugin.getPerformanceMonitor().decrementChunkLoads();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getPathfindingCache() != null) {
            plugin.getPathfindingCache().invalidateArea(event.getBlock().getLocation(), 8.0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getPathfindingCache() != null) {
            plugin.getPathfindingCache().invalidateArea(event.getBlock().getLocation(), 8.0);
        }
    }
}
