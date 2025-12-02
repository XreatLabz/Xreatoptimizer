package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.ProtectedEntities;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages entity hibernation for performance optimization
 * 
 * IMPORTANT: This manager is DISABLED by default to prevent data loss.
 * Entity hibernation can cause issues with:
 * - Armor stands losing their items
 * - Named entities being lost
 * - Player-placed entities disappearing
 * 
 * Only enable if you understand the risks and have proper backups.
 */
public class HibernateManager {
    
    private final XreatOptimizer plugin;
    private BukkitTask hibernateTask;
    private final Map<String, HibernationData> hibernatedEntities = new ConcurrentHashMap<>();
    private final Set<String> hibernatedChunks = ConcurrentHashMap.newKeySet();
    private volatile boolean isRunning = false;
    
    // Data class to store hibernation information
    private static class HibernationData {
        long hibernationTime;
        String worldName;
        double x, y, z;
        String entityType;
        String entityNBT; // Simplified representation
        
        public HibernationData(Entity entity) {
            this.hibernationTime = System.currentTimeMillis();
            this.worldName = entity.getWorld().getName();
            Location loc = entity.getLocation();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.entityType = entity.getType().name();
            // In a real implementation, we would store essential NBT data
            this.entityNBT = "nbt_stub"; // Placeholder
        }
    }
    
    public HibernateManager(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Checks if an entity should be protected from hibernation.
     * Uses version-safe ProtectedEntities utility for cross-version compatibility.
     */
    private boolean isEntityProtected(Entity entity) {
        return ProtectedEntities.isProtected(entity);
    }
    
    /**
     * Starts the hibernate manager
     */
    public void start() {
        // DISABLED by default - hibernation can cause entity data loss
        if (plugin.getConfig().getBoolean("hibernate.enabled", false)) {
            // Run hibernation checks every 10 seconds
            hibernateTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::runHibernateCycle,
                200L,  // Initial delay (10 seconds)
                400L   // Repeat interval (20 seconds)
            );
            
            isRunning = true;
            LoggerUtils.info("Hibernate manager started.");
        } else {
            LoggerUtils.info("Hibernate manager is disabled via config.");
        }
    }
    
    /**
     * Stops the hibernate manager
     */
    public void stop() {
        isRunning = false;
        if (hibernateTask != null) {
            hibernateTask.cancel();
        }
        LoggerUtils.info("Hibernate manager stopped.");
    }
    
    /**
     * Runs a hibernation cycle
     */
    private void runHibernateCycle() {
        if (!isRunning || TPSUtils.isTPSBelow(10.0)) {
            // Don't hibernate if TPS is too low (safety check)
            return;
        }
        
        // Check all worlds for hibernation candidates
        for (World world : plugin.getServer().getWorlds()) {
            processWorldForHibernate(world);
        }
    }
    
    /**
     * Processes a world for hibernation candidates
     */
    private void processWorldForHibernate(World world) {
        // Get hibernate radius from config
        int hibernateRadius = plugin.getConfig().getInt("hibernate.radius", 64);
        
        // Find all chunks that are far from players
        Set<Chunk> chunksToHibernate = new HashSet<>();
        Set<Chunk> activeChunks = new HashSet<>();
        
        // Identify active chunks (near players)
        for (Player player : world.getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            activeChunks.add(playerChunk);
            
            // Add surrounding chunks within hibernate radius
            int radiusInChunks = (int) Math.ceil(hibernateRadius / 16.0);
            for (int x = -radiusInChunks; x <= radiusInChunks; x++) {
                for (int z = -radiusInChunks; z <= radiusInChunks; z++) {
                    Chunk nearbyChunk = world.getChunkAt(playerChunk.getX() + x, playerChunk.getZ() + z);
                    activeChunks.add(nearbyChunk);
                }
            }
        }
        
        // Identify chunks to hibernate (not active)
        for (Chunk chunk : world.getLoadedChunks()) {
            if (!activeChunks.contains(chunk)) {
                chunksToHibernate.add(chunk);
            }
        }
        
        // Hibernate entities in hibernation chunks
        for (Chunk chunk : chunksToHibernate) {
            String chunkKey = getChunkKey(chunk);
            if (!hibernatedChunks.contains(chunkKey)) {
                hibernateChunkEntities(chunk);
                hibernatedChunks.add(chunkKey);
            }
        }
        
        // Wake up entities in chunks that now have players
        Iterator<String> hibernatedChunkIter = hibernatedChunks.iterator();
        while (hibernatedChunkIter.hasNext()) {
            String chunkKey = hibernatedChunkIter.next();
            if (shouldWakeChunk(chunkKey, activeChunks)) {
                wakeChunk(chunkKey);
                hibernatedChunkIter.remove();
            }
        }
    }
    
    /**
     * Hibernate all entities in a chunk
     * 
     * IMPORTANT: This method now skips ALL protected entity types to prevent
     * data loss and gameplay issues. Only hostile mobs far from players
     * will be hibernated.
     */
    private void hibernateChunkEntities(Chunk chunk) {
        Entity[] entities = chunk.getEntities();
        int hibernatedCount = 0;
        int skippedCount = 0;
        
        for (Entity entity : entities) {
            // Don't hibernate players
            if (entity instanceof Player) {
                continue;
            }
            
            // Don't hibernate protected entities (armor stands, pets, farm animals, etc.)
            if (isEntityProtected(entity)) {
                skippedCount++;
                continue;
            }
            
            // Store entity data before removing
            HibernationData data = new HibernationData(entity);
            String entityKey = getEntityKey(entity);
            hibernatedEntities.put(entityKey, data);
            
            // Remove the entity from the world
            entity.remove();
            hibernatedCount++;
        }
        
        if (hibernatedCount > 0 || skippedCount > 0) {
            LoggerUtils.debug("Hibernated " + hibernatedCount + " entities (skipped " + skippedCount + 
                             " protected) in chunk " + chunk.getX() + "," + chunk.getZ() + 
                             " of world " + chunk.getWorld().getName());
        }
    }
    
    /**
     * Wake up entities in a chunk
     */
    private void wakeChunk(String chunkKey) {
        // Find and recreate entities that were hibernated in this chunk
        List<HibernationData> entitiesToWake = new ArrayList<>();
        
        Iterator<Map.Entry<String, HibernationData>> iter = hibernatedEntities.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, HibernationData> entry = iter.next();
            String[] parts = entry.getKey().split(":");
            if (parts.length >= 3 && (parts[0] + ":" + parts[1] + ":" + parts[2]).equals(chunkKey)) {
                entitiesToWake.add(entry.getValue());
                iter.remove();
            }
        }
        
        // Recreate the entities in the world
        for (HibernationData data : entitiesToWake) {
            World world = org.bukkit.Bukkit.getWorld(data.worldName);
            if (world != null) {
                try {
                    Location loc = new Location(world, data.x, data.y, data.z);
                    EntityType type = EntityType.valueOf(data.entityType);
                    
                    // Attempt to spawn the entity
                    Entity entity = world.spawnEntity(loc, type);
                    
                    LoggerUtils.debug("Restored entity " + data.entityType + " at " + 
                                     loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                } catch (Exception e) {
                    LoggerUtils.error("Could not restore hibernated entity: " + data.entityType, e);
                }
            }
        }
        
        LoggerUtils.debug("Woke chunk " + chunkKey + ", restored " + entitiesToWake.size() + " entities");
    }
    
    /**
     * Checks if a chunk should be woken up
     */
    private boolean shouldWakeChunk(String chunkKey, Set<Chunk> activeChunks) {
        // Parse chunk key to get world, x, z
        String[] parts = chunkKey.split(":");
        if (parts.length < 3) return false;
        
        String worldName = parts[0];
        int x = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) return false;
        
        Chunk chunk = world.getChunkAt(x, z);
        return activeChunks.contains(chunk);
    }
    
    /**
     * Gets a unique key for a chunk
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
    
    /**
     * Gets a unique key for an entity
     */
    private String getEntityKey(Entity entity) {
        Location loc = entity.getLocation();
        return entity.getWorld().getName() + ":" + 
               loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ() + ":" + 
               entity.getUniqueId().toString();
    }
    
    /**
     * Gets the number of hibernated chunks
     */
    public int getHibernatedChunkCount() {
        return hibernatedChunks.size();
    }
    
    /**
     * Gets the number of hibernated entities
     */
    public int getHibernatedEntityCount() {
        return hibernatedEntities.size();
    }
    
    /**
     * Checks if the hibernate manager is running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Set whether the hibernate manager is enabled
     */
    public void setEnabled(boolean enabled) {
        if (enabled && !isRunning) {
            start();
        } else if (!enabled && isRunning) {
            stop();
        }
    }

    /**
     * Set the hibernate radius
     */
    public void setRadius(int radius) {
        plugin.getConfig().set("hibernate.radius", radius);
        LoggerUtils.info("Hibernate radius set to: " + radius);
    }
}