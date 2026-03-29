package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Distributes tasks across ticks. */
public class SmartTickDistributor {
    private final XreatOptimizer plugin;
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask distributorTask;
    private volatile boolean isRunning = false;
    private int tasksPerTick = 5;
    private boolean enabled = true;

    public SmartTickDistributor(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        enabled = plugin.getConfig().getBoolean("smart_tick.enabled", true);
        tasksPerTick = Math.max(1, plugin.getConfig().getInt("smart_tick.tasks_per_tick", 5));
    }

    public void start() {
        loadConfig();
        if (!enabled) {
            LoggerUtils.info("Smart tick distributor is disabled in config.");
            return;
        }

        if (isRunning) {
            return;
        }

        distributorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isRunning) {
                    cancel();
                    return;
                }

                for (int i = 0; i < tasksPerTick && !taskQueue.isEmpty(); i++) {
                    Runnable task = taskQueue.poll();
                    if (task != null) {
                        try {
                            task.run();
                        } catch (Exception e) {
                            LoggerUtils.error("Error executing distributed task", e);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        isRunning = true;
        LoggerUtils.info("Smart tick distributor started (" + tasksPerTick + " tasks per tick).");
    }

    public void addTask(Runnable task) {
        taskQueue.add(task);
    }

    public void addTasks(Iterable<Runnable> tasks) {
        for (Runnable task : tasks) {
            addTask(task);
        }
    }

    public int getQueuedTaskCount() {
        return taskQueue.size();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void stop() {
        isRunning = false;
        if (distributorTask != null) {
            distributorTask.cancel();
            distributorTask = null;
        }
        taskQueue.clear();
        LoggerUtils.info("Smart tick distributor stopped.");
    }

    public void reloadConfig() {
        boolean wasRunning = isRunning;
        if (wasRunning) {
            stop();
        } else {
            loadConfig();
        }

        if (plugin.getConfig().getBoolean("smart_tick.enabled", true)) {
            start();
        }
    }
}
