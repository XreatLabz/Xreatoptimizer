package com.xreatlabs.xreatoptimizer.async;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.ChunkPreGenerator;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/** Async chunk pregeneration utility */
public class AsyncChunkPregen {
    private final XreatOptimizer plugin;
    private final ChunkPreGenerator chunkPreGenerator;
    
    public AsyncChunkPregen(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.chunkPreGenerator = new ChunkPreGenerator(plugin);
    }
    
    public CompletableFuture<Void> pregenerateWorld(String worldName, int centerX, int centerZ, int radius, int speed) {
        return CompletableFuture.runAsync(() -> {
            LoggerUtils.info("Starting async chunk pregeneration for world: " + worldName);
            
            try {
                chunkPreGenerator.pregenerateWorld(worldName, centerX, centerZ, radius, speed).join();
                LoggerUtils.info("Completed async chunk pregeneration for world: " + worldName);
            } catch (Exception e) {
                LoggerUtils.error("Error during async chunk pregeneration for world: " + worldName, e);
            }
        }, plugin.getThreadPoolManager().getChunkTaskPool());
    }
    
    public CompletableFuture<Void> pregenerateAroundPlayer(String playerName, int radius, int speed) {
        return CompletableFuture.supplyAsync(() -> {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerName);
            if (player == null) {
                LoggerUtils.error("Player not found: " + playerName);
                return null;
            }
            
            org.bukkit.Location loc = player.getLocation();
            String worldName = loc.getWorld().getName();
            int chunkX = loc.getBlockX() >> 4;  // Convert to chunk coordinates
            int chunkZ = loc.getBlockZ() >> 4;
            
            return pregenerateWorld(worldName, chunkX, chunkZ, radius, speed);
        }, plugin.getThreadPoolManager().getChunkTaskPool()).thenCompose(cf -> cf);
    }
    
    public boolean isValidWorldForPregen(String worldName) {
        World world = Bukkit.getWorld(worldName);
        return world != null;
    }
    
    public int getProgressForWorld(String worldName) {
        // No pregen currently running for this world
        return 0;
    }
}