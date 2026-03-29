package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
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

    private int predictionDistance = 5;
    private double minVelocity = 0.1;
    private int preloadCapPerTick = 2;

    private static class PlayerMovementData {
        final UUID playerId;
        final Deque<Location> locationHistory = new LinkedList<>();
        Vector velocity = new Vector(0, 0, 0);
        Vector avgDirection = new Vector(0, 0, 0);
        long lastUpdate = System.currentTimeMillis();
        int chunksPreloaded = 0;

        PlayerMovementData(UUID playerId) {
            this.playerId = playerId;
        }

        void addLocation(Location loc) {
            locationHistory.addFirst(loc.clone());
            if (locationHistory.size() > 10) {
                locationHistory.removeLast();
            }
            lastUpdate = System.currentTimeMillis();
        }

        Vector calculateVelocity() {
            if (locationHistory.size() < 2) {
                return new Vector(0, 0, 0);
            }

            Iterator<Location> it = locationHistory.iterator();
            Location current = it.next();
            Location previous = it.next();
            return current.toVector().subtract(previous.toVector());
        }

        Vector calculateAverageDirection() {
            if (locationHistory.size() < 3) {
                return velocity.lengthSquared() > 0.01 ? velocity.clone().normalize() : new Vector(0, 0, 0);
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

            return count > 0 ? sum.multiply(1.0 / count).normalize() : new Vector(0, 0, 0);
        }
    }

    private static class ChunkCoord {
        final int x;
        final int z;
        final String worldName;

        ChunkCoord(int x, int z, String worldName) {
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
        loadConfig();
    }

    private void loadConfig() {
        predictionDistance = Math.max(2, plugin.getConfig().getInt("predictive_loading.prediction_distance", 5));
        minVelocity = Math.max(0.05, plugin.getConfig().getDouble("predictive_loading.min_velocity", 0.1));
        preloadCapPerTick = Math.max(1, plugin.getConfig().getInt("predictive_loading.max_preloads_per_tick", 2));
    }

    public void start() {
        loadConfig();
        if (!plugin.getConfig().getBoolean("predictive_loading.enabled", false)) {
            LoggerUtils.info("Predictive chunk loading is disabled in config.");
            return;
        }

        if (isRunning) {
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
            predictionTask = null;
        }

        HandlerList.unregisterAll(this);
        playerMovement.clear();
        preloadedChunks.clear();

        LoggerUtils.info("Predictive chunk loader stopped");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isRunning) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        PlayerMovementData data = playerMovement.computeIfAbsent(
            player.getUniqueId(),
            k -> new PlayerMovementData(player.getUniqueId())
        );

        data.addLocation(to);
        data.velocity = data.calculateVelocity();
        data.avgDirection = data.calculateAverageDirection();
    }

    private void processPredictions() {
        if (!isRunning) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerMovementData data = playerMovement.get(player.getUniqueId());
            if (data == null) {
                continue;
            }

            if (data.velocity.lengthSquared() < minVelocity * minVelocity) {
                continue;
            }

            predictAndPreload(player, data);
        }

        cleanupPreloadedChunks();
    }

    private void predictAndPreload(Player player, PlayerMovementData data) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) {
            return;
        }

        Vector direction = data.avgDirection;
        if (direction.lengthSquared() < 0.01) {
            return;
        }

        Set<ChunkCoord> toPreload = new LinkedHashSet<>();
        for (int dist = 1; dist <= predictionDistance; dist++) {
            Location predictedLoc = playerLoc.clone().add(direction.clone().multiply(dist * 16));
            int chunkX = predictedLoc.getBlockX() >> 4;
            int chunkZ = predictedLoc.getBlockZ() >> 4;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    toPreload.add(new ChunkCoord(chunkX + dx, chunkZ + dz, world.getName()));
                }
            }
        }

        String worldName = world.getName();
        Set<ChunkCoord> worldPreloaded = preloadedChunks.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());

        int preloadCount = 0;
        for (ChunkCoord coord : toPreload) {
            if (worldPreloaded.contains(coord)) {
                continue;
            }

            preloadChunk(world, coord);
            worldPreloaded.add(coord);
            preloadCount++;
            if (preloadCount >= preloadCapPerTick) {
                break;
            }
        }

        if (preloadCount > 0) {
            data.chunksPreloaded += preloadCount;
        }
    }

    private void preloadChunk(World world, ChunkCoord coord) {
        if (!world.isChunkLoaded(coord.x, coord.z)) {
            world.loadChunk(coord.x, coord.z, false);
        }
    }

    private void cleanupPreloadedChunks() {
        for (String worldName : new HashSet<>(preloadedChunks.keySet())) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                preloadedChunks.remove(worldName);
                continue;
            }

            Set<ChunkCoord> worldChunks = preloadedChunks.get(worldName);
            if (worldChunks == null) {
                continue;
            }

            worldChunks.removeIf(coord -> {
                for (Player player : world.getPlayers()) {
                    int playerChunkX = player.getLocation().getBlockX() >> 4;
                    int playerChunkZ = player.getLocation().getBlockZ() >> 4;
                    int distance = Math.max(Math.abs(playerChunkX - coord.x), Math.abs(playerChunkZ - coord.z));
                    if (distance <= predictionDistance + 2) {
                        return false;
                    }
                }
                return true;
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
        return preloadedChunks.values().stream().mapToInt(Set::size).sum();
    }
}
