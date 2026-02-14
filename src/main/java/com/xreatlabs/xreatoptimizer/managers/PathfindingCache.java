package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PathfindingCache {
    
    private final XreatOptimizer plugin;
    private final Map<PathKey, CachedPath> pathCache = new ConcurrentHashMap<>();
    private final Map<String, Long> worldModifications = new ConcurrentHashMap<>();
    
    private final int MAX_CACHE_SIZE = 10000;
    private final long CACHE_EXPIRY_TIME = 30000;
    
    private static class PathKey {
        final String worldName;
        final Vector start;
        final Vector end;
        final int hash;
        
        public PathKey(Location start, Location end) {
            this.worldName = start.getWorld().getName();
            this.start = new Vector(
                Math.floor(start.getX() / 2) * 2,
                Math.floor(start.getY()),
                Math.floor(start.getZ() / 2) * 2
            );
            this.end = new Vector(
                Math.floor(end.getX() / 2) * 2,
                Math.floor(end.getY()),
                Math.floor(end.getZ() / 2) * 2
            );
            this.hash = Objects.hash(worldName, this.start, this.end);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathKey)) return false;
            PathKey pathKey = (PathKey) o;
            return worldName.equals(pathKey.worldName) &&
                   start.equals(pathKey.start) &&
                   end.equals(pathKey.end);
        }
        
        @Override
        public int hashCode() {
            return hash;
        }
    }
    
    private static class CachedPath {
        final List<Vector> waypoints;
        final double pathLength;
        final long cachedTime;
        final long worldModificationTime;
        int hits = 0;
        
        public CachedPath(List<Vector> waypoints, double pathLength, long worldModificationTime) {
            this.waypoints = new ArrayList<>(waypoints);
            this.pathLength = pathLength;
            this.cachedTime = System.currentTimeMillis();
            this.worldModificationTime = worldModificationTime;
        }
        
        public boolean isValid(long currentWorldModTime, long currentTime) {
            if (currentWorldModTime > worldModificationTime) {
                return false;
            }
            
            return currentTime - cachedTime < 30000;
        }
    }
    
    public PathfindingCache(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        if (!plugin.getConfig().getBoolean("pathfinding_cache.enabled", true)) {
            LoggerUtils.info("Pathfinding cache is disabled in config.");
            return;
        }
        
        org.bukkit.Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::cleanupCache,
            1200L,
            1200L
        );
        
        LoggerUtils.info("Pathfinding cache started - reducing pathfinding CPU usage");
    }
    
    public void stop() {
        pathCache.clear();
        worldModifications.clear();
        LoggerUtils.info("Pathfinding cache stopped");
    }
    
    public List<Vector> getCachedPath(Location start, Location end) {
        PathKey key = new PathKey(start, end);
        String worldName = start.getWorld().getName();
        
        long worldModTime = worldModifications.getOrDefault(worldName, 0L);
        long currentTime = System.currentTimeMillis();
        
        CachedPath cached = pathCache.get(key);
        if (cached != null && cached.isValid(worldModTime, currentTime)) {
            cached.hits++;
            return cached.waypoints;
        }
        
        return null;
    }
    
    public void cachePath(Location start, Location end, List<Vector> waypoints) {
        if (pathCache.size() >= MAX_CACHE_SIZE) {
            evictOldest(MAX_CACHE_SIZE / 10);
        }
        
        PathKey key = new PathKey(start, end);
        String worldName = start.getWorld().getName();
        long worldModTime = worldModifications.getOrDefault(worldName, 0L);
        
        double pathLength = 0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            pathLength += waypoints.get(i).distance(waypoints.get(i + 1));
        }
        
        CachedPath cached = new CachedPath(waypoints, pathLength, worldModTime);
        pathCache.put(key, cached);
    }
    
    public void onWorldModification(String worldName) {
        worldModifications.put(worldName, System.currentTimeMillis());
    }
    
    public void invalidateArea(Location location, double radius) {
        String worldName = location.getWorld().getName();
        Vector center = location.toVector();
        
        pathCache.entrySet().removeIf(entry -> {
            PathKey key = entry.getKey();
            if (!key.worldName.equals(worldName)) return false;
            
            return key.start.distance(center) < radius || 
                   key.end.distance(center) < radius;
        });
    }
    
    private void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        
        pathCache.entrySet().removeIf(entry -> {
            CachedPath path = entry.getValue();
            long worldModTime = worldModifications.getOrDefault(entry.getKey().worldName, 0L);
            return !path.isValid(worldModTime, currentTime);
        });
    }
    
    private void evictOldest(int count) {
        pathCache.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> e.getValue().cachedTime))
            .limit(count)
            .map(Map.Entry::getKey)
            .forEach(pathCache::remove);
    }
    
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cache_size", pathCache.size());
        stats.put("max_cache_size", MAX_CACHE_SIZE);
        
        long totalHits = pathCache.values().stream()
            .mapToLong(p -> p.hits)
            .sum();
        stats.put("total_hits", totalHits);
        
        double avgHits = pathCache.isEmpty() ? 0 : 
            (double) totalHits / pathCache.size();
        stats.put("avg_hits_per_path", String.format("%.2f", avgHits));
        
        double hitRate = pathCache.isEmpty() ? 0 :
            (double) totalHits / (totalHits + pathCache.size()) * 100;
        stats.put("hit_rate_percent", String.format("%.2f", hitRate));
        
        return stats;
    }
    
    public void clearCache() {
        int size = pathCache.size();
        pathCache.clear();
        LoggerUtils.info("Cleared " + size + " cached paths");
    }
    
    public int getCacheSize() {
        return pathCache.size();
    }
}
