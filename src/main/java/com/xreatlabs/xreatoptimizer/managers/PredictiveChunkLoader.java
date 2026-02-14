package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PredictiveChunkLoader implements Listener {
    
    private final XreatOptimizer plugin;
    private final Map<UUID, PlayerMovementData> playerMovement = new ConcurrentHashMap<>();
    private final Map<String, Set<ChunkCoord>> preloadedChunks = new ConcurrentHashMap<>();
    private BukkitTask predictionTask;
    private volatile boolean isRunning = false;
    
    private final int PREDICTION_DISTANCE = 5;
    private final double MIN_VELOCITY = 0.1;
    
    private static class PlayerMovementData {
        final UUID playerId;
        final Deque<Location> locationHistory = new LinkedList<>();
        Vector velocity = new Vector(0, 0, 0);
        Vector avgDirection = new Vector(0, 0, 0);
        long lastUpdate = System.currentTimeMillis();
        int chunksPreloaded = 0;
        
        public PlayerMovementData(UUID playerId) {
            this.playerId = playerId;
        }
        
        public void addLocation(Location loc) {
            locationHistory.addFirst(loc.clone());
            if (locationHistory.size() > 10) {
                locationHistory.removeLast();
            }
            lastUpdate = System.currentTimeMillis();
        }
        
        public Vector calculateVelocity() {
            if (locationHistory.size() < 2) {
                return new Vector(0, 0, 0);
            }

            Iterator<Location> it = locationHistory.iterator();
            Location current = it.next();
            Location previous = it.next();

            return current.toVector().subtract(previous.toVector());
        }
        
        public Vector calculateAverageDirection() {
            if (locationHistory.size() < 3) {
                return velocity.clone().normalize();
            }

            Vector sum = new Vector(0, 0, 0);
            int count = 0;

            List<Location> history = new ArrayList<>(locationHistory);
            for (int i = 0; i < Math.min(5, history.size() - 1); i++) {
                Location curr = history.get(i);
                Location prev = history.get(i + 1);
                Vector dir = curr.toVector().subtract(prev.toVector());
                if (dir.lengthSquared() > 0.01) {
                    sum.add(dir.normalize());
                    count++;
                }
            }

            return count > 0 ? sum.multiply(1.0 / count).normalize() : velocity.clone().normalize();
        }
    }
    
    private static class ChunkCoord {
        final int x;
        final int z;
        final String worldName;
        
        public ChunkCoord(int x, int z, String worldName) {
            this.x = x;
            this.z = z;
            this.worldName = worldName;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkCoord)) return false;
            ChunkCoord that = (ChunkCoord) o;
            return x == that.x && z == that.z && worldName.equals(that.worldName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, z, worldName);
        }
    }
    
    public PredictiveChunkLoader(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        if (!plugin.getConfig().getBoolean("predictive_loading.enabled", true)) {
            LoggerUtils.info("Predictive chunk loading is disabled in config.");
            return;
        }
        
        isRunning = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        predictionTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::processPredictions,
            20L,
            10L
        );
        
        LoggerUtils.info("Predictive chunk loader started - will preload chunks based on player movement");
    }
    
    public void stop() {
        isRunning = false;
        
        if (predictionTask != null) {
            predictionTask.cancel();
        }
        
        playerMovement.clear();
        preloadedChunks.clear();
        
        LoggerUtils.info("Predictive chunk loader stopped");
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isRunning) return;
        
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        if (to == null) return;
        
        Location from = event.getFrom();
        if (from.distanceSquared(to) < 0.01) return;
        
        PlayerMovementData data = playerMovement.computeIfAbsent(
            player.getUniqueId(),
            k -> new PlayerMovementData(player.getUniqueId())
        );
        
        data.addLocation(to);
        data.velocity = data.calculateVelocity();
        data.avgDirection = data.calculateAverageDirection();
    }
    
    private void processPredictions() {
        if (!isRunning) return;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerMovementData data = playerMovement.get(player.getUniqueId());
            if (data == null) continue;
            
            if (data.velocity.lengthSquared() < MIN_VELOCITY * MIN_VELOCITY) {
                continue;
            }
            
            predictAndPreload(player, data);
        }
        
        cleanupPreloadedChunks();
    }
    
    private void predictAndPreload(Player player, PlayerMovementData data) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;
        
        Vector direction = data.avgDirection;
        if (direction.lengthSquared() < 0.01) return;
        
        Set<ChunkCoord> toPreload = new HashSet<>();
        
        for (int dist = 1; dist <= PREDICTION_DISTANCE; dist++) {
            Location predictedLoc = playerLoc.clone().add(direction.clone().multiply(dist * 16));
            
            int chunkX = predictedLoc.getBlockX() >> 4;
            int chunkZ = predictedLoc.getBlockZ() >> 4;
            
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkCoord coord = new ChunkCoord(chunkX + dx, chunkZ + dz, world.getName());
                    toPreload.add(coord);
                }
            }
        }
        
        String worldName = world.getName();
        Set<ChunkCoord> worldPreloaded = preloadedChunks.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());
        
        int preloadCount = 0;
        for (ChunkCoord coord : toPreload) {
            if (!worldPreloaded.contains(coord)) {
                preloadChunk(world, coord);
                worldPreloaded.add(coord);
                preloadCount++;
                
                if (preloadCount >= 3) break;
            }
        }
        
        if (preloadCount > 0) {
            data.chunksPreloaded += preloadCount;
        }
    }
    
    private void preloadChunk(World world, ChunkCoord coord) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!world.isChunkLoaded(coord.x, coord.z)) {
                world.loadChunk(coord.x, coord.z, false);
            }
        });
    }
    
    private void cleanupPreloadedChunks() {
        for (String worldName : preloadedChunks.keySet()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                preloadedChunks.remove(worldName);
                continue;
            }
            
            Set<ChunkCoord> worldChunks = preloadedChunks.get(worldName);
            worldChunks.removeIf(coord -> {
                boolean nearPlayer = false;
                for (Player player : world.getPlayers()) {
                    int playerChunkX = player.getLocation().getBlockX() >> 4;
                    int playerChunkZ = player.getLocation().getBlockZ() >> 4;
                    
                    int distance = Math.max(
                        Math.abs(playerChunkX - coord.x),
                        Math.abs(playerChunkZ - coord.z)
                    );
                    
                    if (distance <= PREDICTION_DISTANCE + 2) {
                        nearPlayer = true;
                        break;
                    }
                }
                
                return !nearPlayer;
            });
        }
    }
    
    public Map<String, Object> getPlayerStats(UUID playerId) {
        Map<String, Object> stats = new HashMap<>();
        PlayerMovementData data = playerMovement.get(playerId);
        
        if (data != null) {
            stats.put("chunks_preloaded", data.chunksPreloaded);
            stats.put("velocity", data.velocity.length());
            stats.put("direction", data.avgDirection);
            stats.put("history_size", data.locationHistory.size());
        }
        
        return stats;
    }
    
    public int getTotalPreloadedChunks() {
        return preloadedChunks.values().stream()
            .mapToInt(Set::size)
            .sum();
    }
}
