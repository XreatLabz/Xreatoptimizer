package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.api.OptimizationEvent;
import com.xreatlabs.xreatoptimizer.api.XreatOptimizerAPI;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;

/**
 * Manages memory optimization and chunk caching
 */
public class MemorySaver {
    private final XreatOptimizer plugin;
    private BukkitTask memoryTask;
    private final Map<String, SoftReference<CachedChunkData>> chunkCache = new ConcurrentHashMap<>();
    private final Set<String> recentlyAccessedChunks = ConcurrentHashMap.newKeySet();
    private volatile boolean isRunning = false;
    private boolean compressionEnabled = true;
    private int memoryThresholdPercent = 80;
    
    // Data class to store cached chunk information
    private static class CachedChunkData {
        final long cacheTime;
        final byte[] compressedData; // Compressed chunk NBT data
        final int originalSize;
        
        public CachedChunkData(byte[] data) {
            this.cacheTime = System.currentTimeMillis();
            this.originalSize = data.length;
            this.compressedData = compressData(data);
        }
        
        private byte[] compressData(byte[] data) {
            Deflater deflater = new Deflater();
            deflater.setInput(data);
            deflater.finish();
            
            byte[] buffer = new byte[data.length];
            int compressedSize = deflater.deflate(buffer);
            deflater.end();
            
            byte[] result = new byte[compressedSize];
            System.arraycopy(buffer, 0, result, 0, compressedSize);
            return result;
        }
        
        public byte[] getDecompressedData() {
            // In a real implementation, you would decompress the data
            // For now, returning original data structure
            return compressedData; // Placeholder
        }
        
        public double getCompressionRatio() {
            return originalSize > 0 ? (double) compressedData.length / originalSize : 1.0;
        }
    }
    
    public MemorySaver(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Starts the memory saver system
     */
    public void start() {
        // Run memory optimization every 30 seconds
        memoryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runMemoryOptimization,
            600L,  // Initial delay (30 seconds)
            600L   // Repeat interval (30 seconds)
        );
        
        isRunning = true;
        LoggerUtils.info("Memory saver system started.");
    }
    
    /**
     * Stops the memory saver system
     */
    public void stop() {
        isRunning = false;
        if (memoryTask != null) {
            memoryTask.cancel();
        }
        
        // Clear cache to free memory
        chunkCache.clear();
        recentlyAccessedChunks.clear();
        
        LoggerUtils.info("Memory saver system stopped.");
    }
    
    /**
     * Runs a memory optimization cycle
     */
    private void runMemoryOptimization() {
        if (!isRunning) return;

        double memoryPercent = MemoryUtils.getMemoryUsagePercentage();

        // Use the dynamic threshold instead of hardcoded check
        if (memoryPercent > memoryThresholdPercent) {
            LoggerUtils.debug("Memory pressure detected (" + memoryThresholdPercent + "% threshold), running optimization...");

            // Determine pressure level
            OptimizationEvent.MemoryPressureEvent.PressureLevel level;
            if (memoryPercent >= 95) {
                level = OptimizationEvent.MemoryPressureEvent.PressureLevel.CRITICAL;
            } else if (memoryPercent >= 85) {
                level = OptimizationEvent.MemoryPressureEvent.PressureLevel.HIGH;
            } else if (memoryPercent >= 75) {
                level = OptimizationEvent.MemoryPressureEvent.PressureLevel.MEDIUM;
            } else {
                level = OptimizationEvent.MemoryPressureEvent.PressureLevel.LOW;
            }

            // Fire memory pressure event
            long usedMb = MemoryUtils.getUsedMemoryMB();
            long maxMb = MemoryUtils.getMaxMemoryMB();
            OptimizationEvent.MemoryPressureEvent memoryEvent =
                new OptimizationEvent.MemoryPressureEvent(memoryPercent, usedMb, maxMb, level);
            XreatOptimizerAPI.fireEvent(memoryEvent);

            offloadIdleChunks();

            if (TPSUtils.getTPS() > 18.0) {
                MemoryUtils.suggestGarbageCollection();
                LoggerUtils.debug("Suggested garbage collection");
            }
        }

        // Clean up expired cache entries
        cleanupExpiredCache();

        // Log memory stats
        LoggerUtils.debug("Memory usage: " + MemoryUtils.getMemoryUsagePercentage() + "%, " +
                         "Cache size: " + chunkCache.size() + " entries");
    }
    
    /**
     * Offloads idle chunks to reduce memory usage
     */
    private void offloadIdleChunks() {
        int chunksOffloaded = 0;
        double memoryThreshold = plugin.getConfig().getDouble("memory_reclaim_threshold_percent", 80.0);
        
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunksOffloaded >= 10) break; // Limit per cycle to prevent lag
                
                String chunkKey = getChunkKey(chunk);
                
                // Skip recently accessed chunks
                if (recentlyAccessedChunks.contains(chunkKey)) {
                    continue;
                }
                
                // Check if this chunk should be offloaded based on hibernation status
                // In a real implementation, you'd integrate with HibernateManager
                if (shouldOffloadChunk(chunk)) {
                    // Store chunk data in cache with soft references
                    cacheChunkData(chunk);
                    
                    // Unload the chunk (release from memory)
                    if (world.unloadChunk(chunk.getX(), chunk.getZ(), true)) {
                        chunksOffloaded++;
                        LoggerUtils.debug("Offloaded chunk: " + chunkKey);
                    }
                }
            }
            if (chunksOffloaded >= 10) break; // Limit per cycle
        }
        
        if (chunksOffloaded > 0) {
            LoggerUtils.info("Offloaded " + chunksOffloaded + " chunks to reduce memory usage");
        }
    }
    
    /**
     * Determines if a chunk should be offloaded
     */
    private boolean shouldOffloadChunk(Chunk chunk) {
        // In a real implementation, this would check more sophisticated conditions
        // such as: no players nearby, chunk hasn't been accessed recently, etc.
        
        // For now, use a simple check: if no players in the world or far from players
        org.bukkit.World world = chunk.getWorld();
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (player.getLocation().getChunk().getX() == chunk.getX() &&
                player.getLocation().getChunk().getZ() == chunk.getZ()) {
                return false; // Don't offload chunks with players
            }
        }
        
        return true; // OK to offload
    }
    
    /**
     * Caches chunk data using soft references
     */
    private void cacheChunkData(Chunk chunk) {
        // In a real implementation, you would serialize the chunk data
        // For now, we'll create placeholder data
        byte[] chunkData = serializeChunkData(chunk);
        CachedChunkData cached = new CachedChunkData(chunkData);
        
        String chunkKey = getChunkKey(chunk);
        chunkCache.put(chunkKey, new SoftReference<>(cached));
    }
    
    /**
     * Serializes chunk data (placeholder implementation)
     */
    private byte[] serializeChunkData(Chunk chunk) {
        // In a real implementation, you would serialize the actual chunk NBT data
        String data = "chunk_data_placeholder_" + chunk.getWorld().getName() + 
                     "_" + chunk.getX() + "_" + chunk.getZ();
        return data.getBytes();
    }
    
    /**
     * Cleans up expired cache entries
     */
    private void cleanupExpiredCache() {
        Iterator<Map.Entry<String, SoftReference<CachedChunkData>>> iter = chunkCache.entrySet().iterator();
        int cleaned = 0;
        
        while (iter.hasNext()) {
            Map.Entry<String, SoftReference<CachedChunkData>> entry = iter.next();
            SoftReference<CachedChunkData> ref = entry.getValue();
            
            // If the soft reference has been cleared by GC, remove from cache
            if (ref.get() == null) {
                iter.remove();
                cleaned++;
            } else {
                // Remove old entries (older than 1 hour)
                CachedChunkData data = ref.get();
                if (data != null && System.currentTimeMillis() - data.cacheTime > 3600000) {
                    iter.remove();
                    cleaned++;
                }
            }
        }
        
        if (cleaned > 0) {
            LoggerUtils.debug("Cleaned up " + cleaned + " expired cache entries");
        }
    }
    
    /**
     * Gets a unique key for a chunk
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
    
    /**
     * Marks a chunk as recently accessed (preventing it from being offloaded)
     */
    public void markChunkAsAccessed(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        recentlyAccessedChunks.add(chunkKey);
        
        // Remove from recently accessed set after a period (e.g., 5 minutes)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            recentlyAccessedChunks.remove(chunkKey);
        }, 20 * 60 * 5); // 5 minutes in ticks
    }
    
    /**
     * Gets the number of cached chunks
     */
    public int getCachedChunkCount() {
        return chunkCache.size();
    }
    
    /**
     * Gets the current memory usage percentage
     */
    public double getMemoryUsage() {
        return MemoryUtils.getMemoryUsagePercentage();
    }
    
    /**
     * Checks if the memory saver is running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Clears the entire chunk cache (for emergency situations)
     */
    public void clearCache() {
        int sizeBefore = chunkCache.size();
        chunkCache.clear();
        recentlyAccessedChunks.clear();
        LoggerUtils.info("Cleared chunk cache: " + sizeBefore + " entries removed");
    }

    public void setCompressionEnabled(boolean enabled) {
        this.compressionEnabled = enabled;
        LoggerUtils.info("Memory compression " + (enabled ? "enabled" : "disabled"));
    }

    public void setThreshold(int threshold) {
        this.memoryThresholdPercent = threshold;
        LoggerUtils.debug("Memory threshold set to: " + threshold + "%");
    }
}