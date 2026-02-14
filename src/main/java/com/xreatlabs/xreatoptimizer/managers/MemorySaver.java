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

public class MemorySaver {
    private final XreatOptimizer plugin;
    private BukkitTask memoryTask;
    private final Map<String, SoftReference<CachedChunkData>> chunkCache = new ConcurrentHashMap<>();
    private final Set<String> recentlyAccessedChunks = ConcurrentHashMap.newKeySet();
    private volatile boolean isRunning = false;
    private boolean compressionEnabled = true;
    private int memoryThresholdPercent = 80;
    
    private static class CachedChunkData {
        final long cacheTime;
        final byte[] compressedData;
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
            return compressedData;
        }
        
        public double getCompressionRatio() {
            return originalSize > 0 ? (double) compressedData.length / originalSize : 1.0;
        }
    }
    
    public MemorySaver(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        memoryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::runMemoryOptimization,
            600L,
            600L
        );
        
        isRunning = true;
        LoggerUtils.info("Memory saver system started.");
    }
    
    public void stop() {
        isRunning = false;
        if (memoryTask != null) {
            memoryTask.cancel();
        }
        
        chunkCache.clear();
        recentlyAccessedChunks.clear();
        
        LoggerUtils.info("Memory saver system stopped.");
    }
    
    private void runMemoryOptimization() {
        if (!isRunning) return;

        double memoryPercent = MemoryUtils.getMemoryUsagePercentage();

        if (memoryPercent > memoryThresholdPercent) {
            LoggerUtils.debug("Memory pressure detected (" + memoryThresholdPercent + "% threshold), running optimization...");

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

        cleanupExpiredCache();

        LoggerUtils.debug("Memory usage: " + MemoryUtils.getMemoryUsagePercentage() + "%, " +
                         "Cache size: " + chunkCache.size() + " entries");
    }
    
    private void offloadIdleChunks() {
        int chunksOffloaded = 0;
        double memoryThreshold = plugin.getConfig().getDouble("memory_reclaim_threshold_percent", 80.0);
        
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunksOffloaded >= 10) break;
                
                String chunkKey = getChunkKey(chunk);
                
                if (recentlyAccessedChunks.contains(chunkKey)) {
                    continue;
                }
                
                if (shouldOffloadChunk(chunk)) {
                    cacheChunkData(chunk);
                    
                    if (world.unloadChunk(chunk.getX(), chunk.getZ(), true)) {
                        chunksOffloaded++;
                        LoggerUtils.debug("Offloaded chunk: " + chunkKey);
                    }
                }
            }
            if (chunksOffloaded >= 10) break;
        }
        
        if (chunksOffloaded > 0) {
            LoggerUtils.info("Offloaded " + chunksOffloaded + " chunks to reduce memory usage");
        }
    }
    
    private boolean shouldOffloadChunk(Chunk chunk) {
        org.bukkit.World world = chunk.getWorld();
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (player.getLocation().getChunk().getX() == chunk.getX() &&
                player.getLocation().getChunk().getZ() == chunk.getZ()) {
                return false;
            }
        }
        
        return true;
    }
    
    private void cacheChunkData(Chunk chunk) {
        byte[] chunkData = serializeChunkData(chunk);
        CachedChunkData cached = new CachedChunkData(chunkData);

        String chunkKey = getChunkKey(chunk);
        chunkCache.put(chunkKey, new SoftReference<>(cached));
    }

    private byte[] serializeChunkData(Chunk chunk) {
        String data = chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
        return data.getBytes();
    }
    
    private void cleanupExpiredCache() {
        Iterator<Map.Entry<String, SoftReference<CachedChunkData>>> iter = chunkCache.entrySet().iterator();
        int cleaned = 0;
        
        while (iter.hasNext()) {
            Map.Entry<String, SoftReference<CachedChunkData>> entry = iter.next();
            SoftReference<CachedChunkData> ref = entry.getValue();
            
            if (ref.get() == null) {
                iter.remove();
                cleaned++;
            } else {
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
    
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
    
    public void markChunkAsAccessed(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        recentlyAccessedChunks.add(chunkKey);
        
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            recentlyAccessedChunks.remove(chunkKey);
        }, 20 * 60 * 5);
    }
    
    public int getCachedChunkCount() {
        return chunkCache.size();
    }
    
    public double getMemoryUsage() {
        return MemoryUtils.getMemoryUsagePercentage();
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
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
