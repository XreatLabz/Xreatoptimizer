package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EmptyServerOptimizer implements Listener {

    private final XreatOptimizer plugin;
    private boolean serverIsEmpty = false;
    private boolean optimizationActive = false;
    private BukkitTask monitorTask;
    private BukkitTask pendingOptimizationTask;
    private final Map<String, Integer> originalViewDistances = new ConcurrentHashMap<>();
    private final Map<String, Integer> originalSimulationDistances = new ConcurrentHashMap<>();
    private final Map<String, Long> originalTimes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> originalStormStates = new ConcurrentHashMap<>();
    private final Map<String, Boolean> originalThunderStates = new ConcurrentHashMap<>();

    private int emptyCheckIntervalTicks = 60;
    private int emptyOptimizationDelayTicks = 600;
    private int minViewDistance = 2;
    private int minSimulationDistance = 2;
    private boolean freezeTime = true;
    private boolean removeItems = false;

    public EmptyServerOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        emptyCheckIntervalTicks = 60;
        emptyOptimizationDelayTicks = Math.max(5, plugin.getConfig().getInt("empty_server.delay_seconds", 30)) * 20;
        minViewDistance = Math.max(2, plugin.getConfig().getInt("empty_server.min_view_distance", 2));
        minSimulationDistance = Math.max(2, plugin.getConfig().getInt("empty_server.min_simulation_distance", 2));
        freezeTime = plugin.getConfig().getBoolean("empty_server.freeze_time", true);
        removeItems = plugin.getConfig().getBoolean("empty_server.remove_items", false);
    }

    public void start() {
        loadConfig();
        if (!plugin.getConfig().getBoolean("empty_server.enabled", true)) {
            LoggerUtils.info("Empty Server Optimizer is disabled in config.");
            return;
        }

        if (monitorTask != null) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        monitorTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::checkServerStatus,
            emptyCheckIntervalTicks,
            emptyCheckIntervalTicks
        );

        LoggerUtils.info("Empty Server Optimizer started - will reduce RAM/CPU when no players are online");
    }

    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }

        if (pendingOptimizationTask != null) {
            pendingOptimizationTask.cancel();
            pendingOptimizationTask = null;
        }

        HandlerList.unregisterAll(this);

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
            if (pendingOptimizationTask != null) {
                pendingOptimizationTask.cancel();
                pendingOptimizationTask = null;
            }
            if (optimizationActive) {
                restoreNormalOperation();
            }
        }
    }

    public void scheduleEmptyOptimization() {
        if (pendingOptimizationTask != null) {
            pendingOptimizationTask.cancel();
        }

        pendingOptimizationTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingOptimizationTask = null;
            if (Bukkit.getOnlinePlayers().isEmpty() && !optimizationActive) {
                applyEmptyServerOptimizations();
            }
        }, emptyOptimizationDelayTicks);
    }

    private void applyEmptyServerOptimizations() {
        LoggerUtils.info("=== EMPTY SERVER DETECTED - Applying low-power mode ===");
        optimizationActive = true;

        long startTime = System.currentTimeMillis();
        int chunksUnloaded = 0;
        int itemsRemoved = 0;

        for (World world : Bukkit.getWorlds()) {
            try {
                snapshotWorldState(world);

                setWorldViewDistance(world, minViewDistance);
                setWorldSimulationDistance(world, minSimulationDistance);

                if (removeItems) {
                    itemsRemoved += removeDroppedItems(world);
                }

                chunksUnloaded += unloadFarChunks(world);

                if (freezeTime) {
                    world.setTime(6000);
                    world.setStorm(false);
                    world.setThundering(false);
                }
            } catch (Exception e) {
                LoggerUtils.warn("Error optimizing world " + world.getName() + ": " + e.getMessage());
            }
        }

        plugin.getThreadPoolManager().executeIoTask(MemoryUtils::suggestGarbageCollection);
        pauseNonCriticalSystems();

        long duration = System.currentTimeMillis() - startTime;
        long usedMemoryMB = MemoryUtils.getUsedMemoryMB();

        LoggerUtils.info(String.format(
            "Empty server optimizations applied in %dms | Chunks unloaded: %d | Items removed: %d | Heap: %dMB",
            duration, chunksUnloaded, itemsRemoved, usedMemoryMB
        ));
        LoggerUtils.info("Server now in LOW-POWER mode - minimal RAM/CPU usage");
    }

    public void restoreNormalOperation() {
        LoggerUtils.info("=== PLAYERS DETECTED - Restoring normal operation ===");
        optimizationActive = false;

        for (World world : Bukkit.getWorlds()) {
            try {
                Integer originalView = originalViewDistances.remove(world.getName());
                if (originalView != null) {
                    setWorldViewDistance(world, originalView);
                }

                Integer originalSimulation = originalSimulationDistances.remove(world.getName());
                if (originalSimulation != null) {
                    setWorldSimulationDistance(world, originalSimulation);
                }

                Long originalTime = originalTimes.remove(world.getName());
                if (originalTime != null) {
                    world.setTime(originalTime);
                }

                Boolean storm = originalStormStates.remove(world.getName());
                if (storm != null) {
                    world.setStorm(storm);
                }

                Boolean thunder = originalThunderStates.remove(world.getName());
                if (thunder != null) {
                    world.setThundering(thunder);
                }
            } catch (Exception ignored) {
            }
        }

        resumeNonCriticalSystems();
        LoggerUtils.info("Server restored to NORMAL mode - full performance available");
    }

    private void snapshotWorldState(World world) {
        originalViewDistances.putIfAbsent(world.getName(), world.getViewDistance());
        originalTimes.putIfAbsent(world.getName(), world.getTime());
        originalStormStates.putIfAbsent(world.getName(), world.hasStorm());
        originalThunderStates.putIfAbsent(world.getName(), world.isThundering());

        try {
            Object simulationDistance = world.getClass().getMethod("getSimulationDistance").invoke(world);
            if (simulationDistance instanceof Integer) {
                originalSimulationDistances.putIfAbsent(world.getName(), (Integer) simulationDistance);
            }
        } catch (Exception ignored) {
        }
    }

    private void setWorldViewDistance(World world, int distance) {
        try {
            world.getClass().getMethod("setViewDistance", int.class).invoke(world, distance);
        } catch (Exception ignored) {
        }
    }

    private void setWorldSimulationDistance(World world, int distance) {
        try {
            world.getClass().getMethod("setSimulationDistance", int.class).invoke(world, distance);
        } catch (Exception ignored) {
        }
    }

    private int removeDroppedItems(World world) {
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private int unloadFarChunks(World world) {
        int chunksUnloaded = 0;
        int spawnX = world.getSpawnLocation().getChunk().getX();
        int spawnZ = world.getSpawnLocation().getChunk().getZ();

        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            int deltaX = Math.abs(chunk.getX() - spawnX);
            int deltaZ = Math.abs(chunk.getZ() - spawnZ);

            if (deltaX > 4 || deltaZ > 4) {
                if (chunk.unload(true)) {
                    chunksUnloaded++;
                }
            }
        }

        return chunksUnloaded;
    }

    private void pauseNonCriticalSystems() {
        if (plugin.getAdvancedEntityOptimizer() != null && plugin.getAdvancedEntityOptimizer().isEnabled()) {
            plugin.getAdvancedEntityOptimizer().stop();
        }

        if (plugin.getEntityCullingManager() != null) {
            plugin.getEntityCullingManager().setEnabled(false);
        }

        if (plugin.getNetworkOptimizer() != null) {
            plugin.getNetworkOptimizer().setEnabled(false);
        }
    }

    private void resumeNonCriticalSystems() {
        if (plugin.getAdvancedEntityOptimizer() != null && !plugin.getAdvancedEntityOptimizer().isEnabled()) {
            plugin.getAdvancedEntityOptimizer().start();
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
        boolean wasRunning = monitorTask != null;
        if (wasRunning) {
            stop();
        } else {
            loadConfig();
        }

        if (plugin.getConfig().getBoolean("empty_server.enabled", true)) {
            start();
        }

        LoggerUtils.info("Empty server optimizer configuration reloaded");
    }

    public boolean isInEmptyMode() {
        return optimizationActive;
    }
}
