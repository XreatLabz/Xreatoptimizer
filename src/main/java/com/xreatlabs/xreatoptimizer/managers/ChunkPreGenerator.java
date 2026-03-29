package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

public class ChunkPreGenerator {
    private final XreatOptimizer plugin;
    private volatile boolean isRunning = false;
    private int maxThreads = 2;
    private int defaultSpeed = 100;

    public ChunkPreGenerator(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        maxThreads = Math.max(1, plugin.getConfig().getInt("pregen.max_threads", 2));
        defaultSpeed = Math.max(1, plugin.getConfig().getInt("pregen.default_speed", 100));
    }

    public void start() {
        isRunning = true;
        loadConfig();
        LoggerUtils.info("Chunk pre-generator system initialized.");
    }

    public void stop() {
        isRunning = false;
        LoggerUtils.info("Chunk pre-generator system stopped.");
    }

    /**
     * Pre-generates chunks around a center point.
     *
     * Work is scheduled on the main thread in small batches so chunk access stays thread-safe.
     */
    public CompletableFuture<Void> pregenerateWorld(String worldName, int centerX, int centerZ, int radius, int speed) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            completion.completeExceptionally(new IllegalArgumentException("World not found: " + worldName));
            return completion;
        }

        if (!isRunning) {
            completion.completeExceptionally(new IllegalStateException("Chunk pre-generator is not running"));
            return completion;
        }

        final int safeSpeed = Math.max(1, speed > 0 ? speed : defaultSpeed);
        final int chunksPerTick = Math.max(1, Math.min(Math.max(maxThreads, safeSpeed / 20), 32));
        final int minDelayTicks = Math.max(1, 20 / Math.max(1, Math.min(safeSpeed, 20)));

        final int minX = centerX - radius;
        final int maxX = centerX + radius;
        final int minZ = centerZ - radius;
        final int maxZ = centerZ + radius;
        final int totalChunks = (radius * 2 + 1) * (radius * 2 + 1);

        LoggerUtils.info("Starting pre-generation for world: " + worldName +
            ", center: [" + centerX + ", " + centerZ + "], radius: " + radius +
            ", speed: " + safeSpeed + " chunks/sec");

        final int[] cursorX = {minX};
        final int[] cursorZ = {minZ};
        final int[] generated = {0};

        Runnable step = new Runnable() {
            @Override
            public void run() {
                if (completion.isDone()) {
                    return;
                }

                if (!isRunning) {
                    completion.completeExceptionally(new IllegalStateException("Chunk pre-generation cancelled"));
                    LoggerUtils.info("Chunk pre-generation cancelled for world: " + worldName);
                    return;
                }

                if (TPSUtils.isTPSBelow(15.0)) {
                    Bukkit.getScheduler().runTaskLater(plugin, this, 40L);
                    return;
                }

                int processedThisTick = 0;
                while (processedThisTick < chunksPerTick && cursorX[0] <= maxX) {
                    world.loadChunk(cursorX[0], cursorZ[0], true);
                    generated[0]++;
                    processedThisTick++;

                    if (generated[0] % 50 == 0 || generated[0] == totalChunks) {
                        double percentage = (double) generated[0] / totalChunks * 100.0;
                        LoggerUtils.info("Pre-generation progress: " + String.format("%.1f", percentage) +
                            "% (" + generated[0] + "/" + totalChunks + ")");
                    }

                    cursorZ[0]++;
                    if (cursorZ[0] > maxZ) {
                        cursorZ[0] = minZ;
                        cursorX[0]++;
                    }
                }

                if (cursorX[0] > maxX) {
                    LoggerUtils.info("Completed pre-generation for " + generated[0] + " chunks in world: " + worldName);
                    completion.complete(null);
                    return;
                }

                Bukkit.getScheduler().runTaskLater(plugin, this, minDelayTicks);
            }
        };

        Bukkit.getScheduler().runTask(plugin, step);
        return completion;
    }

    public CompletableFuture<Void> pregenerateWorldAroundPlayer(String playerName, int radius, int speed) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Player not found: " + playerName));
            return failed;
        }

        Location loc = player.getLocation();
        return pregenerateWorld(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4, radius, speed);
    }

    public void startPredictivePregen(String playerName, int lookAheadChunks) {
        LoggerUtils.debug("Started predictive pre-generation for player: " + playerName +
            " looking ahead: " + lookAheadChunks + " chunks");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = Math.max(1, maxThreads);
        LoggerUtils.info("Chunk pre-generator max threads set to: " + this.maxThreads);
    }

    public void setDefaultSpeed(int speed) {
        this.defaultSpeed = Math.max(1, speed);
        LoggerUtils.info("Chunk pre-generator default speed set to: " + this.defaultSpeed);
    }
}
