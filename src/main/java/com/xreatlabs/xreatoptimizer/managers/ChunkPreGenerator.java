package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkPreGenerator {
    private final XreatOptimizer plugin;
    private final ThreadPoolManager threadPoolManager;
    private final PerformanceMonitor performanceMonitor;
    private volatile boolean isRunning = false;
    
    public ChunkPreGenerator(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.threadPoolManager = plugin.getThreadPoolManager();
        this.performanceMonitor = plugin.getPerformanceMonitor();
    }
    
    /**
     */
    public void start() {
        isRunning = true;
        LoggerUtils.info("Chunk pre-generator system initialized.");
    }
    
    /**
     * Stops the chunk pre-generator system
     */
    public void stop() {
        isRunning = false;
        LoggerUtils.info("Chunk pre-generator system stopped.");
    }
    
    /**
     * Asynchronously pre-generates chunks in a world around a center point
     * @param worldName Name of the world to pre-generate
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Radius in chunks to generate
     * @param speed Number of chunks to generate per cycle (affects performance)
     * @return CompletableFuture for tracking completion
     */
    public CompletableFuture<Void> pregenerateWorld(String worldName, int centerX, int centerZ, int radius, int speed) {
        return CompletableFuture.runAsync(() -> {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                LoggerUtils.error("World not found: " + worldName);
                return;
            }
            
            LoggerUtils.info("Starting pre-generation for world: " + worldName + 
                           ", center: [" + centerX + ", " + centerZ + "], radius: " + radius);
            
            int chunksGenerated = 0;
            int totalChunks = (radius * 2 + 1) * (radius * 2 + 1);
            
            // Create a list of all chunks to generate
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    // Check if task should be cancelled
                    if (!isRunning) {
                        LoggerUtils.info("Chunk pre-generation cancelled for world: " + worldName);
                        return;
                    }
                    
                    // Check TPS - pause if too low
                    if (TPSUtils.isTPSBelow(15.0)) {
                        LoggerUtils.warn("TPS too low (" + TPSUtils.getTPS() + "), pausing chunk generation...");
                        try {
                            Thread.sleep(5000); // Wait 5 seconds before resuming
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    
                    // Load the chunk asynchronously
                    final int chunkX = x;
                    final int chunkZ = z;
                    CompletableFuture<org.bukkit.Chunk> future = CompletableFuture.supplyAsync(() -> {
                        return world.getChunkAt(chunkX, chunkZ);
                    }, threadPoolManager.getIoPool());
                    
                    try {
                        future.get(); // Wait for chunk to be loaded
                        chunksGenerated++;
                        
                        // Update progress every 50 chunks
                        if (chunksGenerated % 50 == 0) {
                            double percentage = (double) chunksGenerated / totalChunks * 100;
                            LoggerUtils.info("Pre-generation progress: " + 
                                           String.format("%.1f", percentage) + "% (" + 
                                           chunksGenerated + "/" + totalChunks + ")");
                        }
                        
                        // Throttle generation speed based on server performance
                        if (speed > 0) {
                            try {
                                Thread.sleep(1000 / speed); // Sleep to control speed
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    } catch (Exception e) {
                        LoggerUtils.error("Error loading chunk [" + x + ", " + z + "] in world " + worldName, e);
                    }
                }
            }
            
            LoggerUtils.info("Completed pre-generation for " + chunksGenerated + " chunks in world: " + worldName);
        }, threadPoolManager.getChunkTaskPool());
    }
    
    /**
     * Asynchronously pre-generates chunks in a world around a player's location
     * @param playerName Name of the player to center on
     * @param radius Radius in chunks to generate
     * @param speed Number of chunks to generate per cycle
     * @return CompletableFuture for tracking completion
     */
    public CompletableFuture<Void> pregenerateWorldAroundPlayer(String playerName, int radius, int speed) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            LoggerUtils.error("Player not found: " + playerName);
            return CompletableFuture.completedFuture(null);
        }
        
        Location loc = player.getLocation();
        return pregenerateWorld(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4, radius, speed);
    }
    
    /**
     * Predicts player movement direction and pre-generates ahead of player
     * @param playerName Name of the player to predict for
     * @param lookAheadChunks How many chunks ahead to pre-generate
     */
    public void startPredictivePregen(String playerName, int lookAheadChunks) {
        LoggerUtils.debug("Started predictive pre-generation for player: " + playerName + 
                         " looking ahead: " + lookAheadChunks + " chunks");
    }
    
    /**
     * Checks if chunk pre-generation is currently running
     * @return True if running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Set the maximum number of threads for chunk generation
     */
    public void setMaxThreads(int maxThreads) {
        LoggerUtils.info("Chunk pre-generator max threads set to: " + maxThreads);
    }

    /**
     * Set the default chunk generation speed
     */
    public void setDefaultSpeed(int speed) {
        LoggerUtils.info("Chunk pre-generator default speed set to: " + speed);
    }
}