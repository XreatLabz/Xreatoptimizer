package com.xreatlabs.xreatoptimizer.async;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.ChunkPreGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/** Compatibility wrapper for chunk pre-generation. */
public class AsyncChunkPregen {
    private final ChunkPreGenerator chunkPreGenerator;

    public AsyncChunkPregen(XreatOptimizer plugin) {
        this.chunkPreGenerator = plugin.getChunkPreGenerator();
    }

    public CompletableFuture<Void> pregenerateWorld(String worldName, int centerX, int centerZ, int radius, int speed) {
        return chunkPreGenerator.pregenerateWorld(worldName, centerX, centerZ, radius, speed);
    }

    public CompletableFuture<Void> pregenerateAroundPlayer(String playerName, int radius, int speed) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Player not found: " + playerName));
            return failed;
        }

        org.bukkit.Location loc = player.getLocation();
        return pregenerateWorld(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4, radius, speed);
    }

    public boolean isValidWorldForPregen(String worldName) {
        World world = Bukkit.getWorld(worldName);
        return world != null;
    }

    public int getProgressForWorld(String worldName) {
        return 0;
    }
}
