package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public class EmptyServerOptimizer implements Listener {
    
    private final XreatOptimizer plugin;
    private boolean serverIsEmpty = false;
    private boolean optimizationActive = false;
    private BukkitTask monitorTask;
    private int originalViewDistance = 10;
    private int originalSimulationDistance = 10;
    
    private final int EMPTY_CHECK_INTERVAL_TICKS = 60;
    private final int EMPTY_OPTIMIZATION_DELAY_TICKS = 600;
    private final int MIN_VIEW_DISTANCE = 2;
    private final int MIN_SIMULATION_DISTANCE = 2;
    
    public EmptyServerOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        if (!plugin.getConfig().getBoolean("empty_server.enabled", true)) {
            LoggerUtils.info("Empty Server Optimizer is disabled in config.");
            return;
        }
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        monitorTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::checkServerStatus,
            EMPTY_CHECK_INTERVAL_TICKS,
            EMPTY_CHECK_INTERVAL_TICKS
        );
        
        LoggerUtils.info("Empty Server Optimizer started - will reduce RAM/CPU when no players online");
    }
    
    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        
        if (optimizationActive) {
            restoreNormalOperation();
        }
        
        LoggerUtils.info("Empty Server Optimizer stopped");
    }
    
    private void checkServerStatus() {
        int playerCount = Bukkit.getOnlinePlayers().size();
        boolean isEmpty = playerCount == 0;
        
        if (isEmpty && !serverIsEmpty) {
            serverIsEmpty = true;
            scheduleEmptyOptimization();
        } else if (!isEmpty && serverIsEmpty) {
            serverIsEmpty = false;
            if (optimizationActive) {
                restoreNormalOperation();
            }
        }
    }
    
    public void scheduleEmptyOptimization() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty() && !optimizationActive) {
                applyEmptyServerOptimizations();
            }
        }, EMPTY_OPTIMIZATION_DELAY_TICKS);
    }
    
    private void applyEmptyServerOptimizations() {
        LoggerUtils.info("=== EMPTY SERVER DETECTED - Applying aggressive optimizations ===");
        optimizationActive = true;
        
        long startTime = System.currentTimeMillis();
        int chunksUnloaded = 0;
        int entitiesRemoved = 0;
        
        for (World world : Bukkit.getWorlds()) {
            try {
                try {
                    originalViewDistance = (int) world.getClass().getMethod("getViewDistance").invoke(world);
                    world.getClass().getMethod("setViewDistance", int.class).invoke(world, MIN_VIEW_DISTANCE);
                } catch (Exception e) {
                    LoggerUtils.warn("View distance methods not available in this server version");
                }
                
                try {
                    originalSimulationDistance = (int) world.getClass().getMethod("getSimulationDistance").invoke(world);
                    world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, MIN_SIMULATION_DISTANCE);
                } catch (Exception e) {
                }
                
                if (plugin.getConfig().getBoolean("empty_server.remove_items", false)) {
                    for (Entity entity : world.getEntities()) {
                        if (entity instanceof Item) {
                            entity.remove();
                            entitiesRemoved++;
                        }
                    }
                }
                
                int spawnX = world.getSpawnLocation().getChunk().getX();
                int spawnZ = world.getSpawnLocation().getChunk().getZ();
                
                try {
                    for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                        int deltaX = Math.abs(chunk.getX() - spawnX);
                        int deltaZ = Math.abs(chunk.getZ() - spawnZ);
                        
                        if (deltaX > 4 || deltaZ > 4) {
                            if (chunk.unload(true)) {
                                chunksUnloaded++;
                            }
                        }
                    }
                } catch (Exception e) {
                    LoggerUtils.warn("Error unloading chunks in " + world.getName() + ": " + e.getMessage());
                }
                
                if (plugin.getConfig().getBoolean("empty_server.freeze_time", true)) {
                    world.setTime(6000);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                
            } catch (Exception e) {
                LoggerUtils.warn("Error optimizing world " + world.getName() + ": " + e.getMessage());
            }
        }
        
        plugin.getThreadPoolManager().executeIoTask(() -> {
            MemoryUtils.suggestGarbageCollection();
        });
        
        pauseNonCriticalSystems();
        
        long duration = System.currentTimeMillis() - startTime;
        long memoryFreedMB = MemoryUtils.getUsedMemoryMB();
        
        LoggerUtils.info(String.format(
            "Empty server optimizations applied in %dms | Chunks unloaded: %d | Entities removed: %d | Memory: %dMB",
            duration, chunksUnloaded, entitiesRemoved, memoryFreedMB
        ));
        LoggerUtils.info("Server now in LOW-POWER mode - minimal RAM/CPU usage");
    }
    
    public void restoreNormalOperation() {
        LoggerUtils.info("=== PLAYERS DETECTED - Restoring normal operation ===");
        optimizationActive = false;
        
        for (World world : Bukkit.getWorlds()) {
            try {
                world.getClass().getMethod("setViewDistance", int.class).invoke(world, originalViewDistance);
            } catch (Exception e) {
            }
            
            try {
                world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, originalSimulationDistance);
            } catch (Exception e) {
            }
        }
        
        resumeNonCriticalSystems();
        
        LoggerUtils.info("Server restored to NORMAL mode - full performance available");
    }
    
    private void pauseNonCriticalSystems() {
        if (plugin.getAdvancedEntityOptimizer() != null) {
            plugin.getAdvancedEntityOptimizer().setEnabled(false);
        }
        
        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(false);
        }
        
        if (plugin.getNetworkOptimizer() != null) {
            plugin.getNetworkOptimizer().setEnabled(false);
        }
    }
    
    private void resumeNonCriticalSystems() {
        if (plugin.getAdvancedEntityOptimizer() != null) {
            plugin.getAdvancedEntityOptimizer().setEnabled(true);
        }
        
        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(true);
        }
        
        if (plugin.getNetworkOptimizer() != null) {
            plugin.getNetworkOptimizer().setEnabled(true);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (optimizationActive) {
            Bukkit.getScheduler().runTask(plugin, this::restoreNormalOperation);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                scheduleEmptyOptimization();
            }
        }, 100L);
    }
    
    public boolean isOptimizationActive() {
        return optimizationActive;
    }
    
    public boolean isServerEmpty() {
        return serverIsEmpty;
    }

    public void reloadConfig() {
        LoggerUtils.info("Empty server optimizer configuration reloaded");
    }

    public boolean isInEmptyMode() {
        return optimizationActive;
    }
}
