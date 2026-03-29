package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.EntityUtils;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

/** Manages automatic clearing of excess projectile entities with safe defaults. */
public class AutoClearTask {
    private final XreatOptimizer plugin;
    private BukkitTask clearTask;
    private volatile boolean isRunning = false;
    private boolean enabled = false;
    private long intervalTicks = 600L * 20L;
    private int arrowLimit = 500;

    public AutoClearTask(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("auto_clear.enabled", false);
        int intervalSeconds = Math.max(30, plugin.getConfig().getInt("clear_interval_seconds", 600));
        intervalTicks = intervalSeconds * 20L;
        arrowLimit = Math.max(0, plugin.getConfig().getInt("auto_clear.arrow_limit", 500));
    }

    public void start() {
        if (isRunning) {
            return;
        }

        loadConfig();

        if (!enabled) {
            LoggerUtils.info("Auto clear task is disabled in config. Skipping start.");
            return;
        }

        clearTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::runClearCycle,
            intervalTicks,
            intervalTicks
        );

        isRunning = true;
        LoggerUtils.info("Auto clear task started. Will run every " + (intervalTicks / 20L) + " seconds.");
    }

    public void stop() {
        isRunning = false;
        if (clearTask != null) {
            clearTask.cancel();
            clearTask = null;
        }
        LoggerUtils.info("Auto clear task stopped.");
    }

    private void runClearCycle() {
        if (!isRunning || !enabled) {
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

    /** Clear excess entities conservatively (arrows only, protected entities skipped). */
    private int clearExcessEntitiesInWorld(World world) {
        if (!enabled) {
            return 0;
        }

        int totalRemoved = 0;
        if (arrowLimit > 0) {
            totalRemoved += EntityUtils.removeExcessEntities(world, EntityType.ARROW, arrowLimit);
            totalRemoved += EntityUtils.removeExcessEntities(world, EntityType.SPECTRAL_ARROW, arrowLimit);
        }

        return totalRemoved;
    }

    public int immediateClear() {
        loadConfig();
        if (!enabled) {
            LoggerUtils.info("Immediate clear skipped because auto_clear is disabled.");
            return 0;
        }

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
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(immediateClear());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reloadConfig() {
        boolean wasRunning = isRunning;
        if (wasRunning) {
            stop();
        } else {
            loadConfig();
        }

        if (plugin.getConfig().getBoolean("auto_clear.enabled", false)) {
            start();
        }
    }

    public void clearEntities() {
        immediateClear();
    }
}
