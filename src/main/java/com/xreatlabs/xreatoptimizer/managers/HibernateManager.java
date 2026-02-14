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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Entity hibernation manager (disabled by default - can cause data loss) */
public class HibernateManager {
    
    private final XreatOptimizer plugin;
    private BukkitTask hibernateTask;
    private final Map<String, HibernationData> hibernatedEntities = new ConcurrentHashMap<>();
    private final Set<String> hibernatedChunks = ConcurrentHashMap.newKeySet();
    private volatile boolean isRunning = false;
    
    private static class HibernationData {
        long hibernationTime;
        String worldName;
        double x, y, z;
        String entityType;
        String entityNBT;
        
        public HibernationData(Entity entity) {
            this.hibernationTime = System.currentTimeMillis();
            this.worldName = entity.getWorld().getName();
            Location loc = entity.getLocation();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.entityType = entity.getType().name();
            this.entityNBT = "stored";
        }
    }
    
    public HibernateManager(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /** Check if entity should be protected */
    private boolean isEntityProtected(Entity entity) {
        return ProtectedEntities.isProtected(entity);
    }
    
    public void start() {
        if (plugin.getConfig().getBoolean("hibernate.enabled", false)) {
            hibernateTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::runHibernateCycle,
                200L,
                400L
            );
            
            isRunning = true;
            LoggerUtils.info("Hibernate manager started.");
        } else {
            LoggerUtils.info("Hibernate manager is disabled via config.");
        }
    }
    
    public void stop() {
        isRunning = false;
        if (hibernateTask != null) {
            hibernateTask.cancel();
        }
        LoggerUtils.info("Hibernate manager stopped.");
    }
    
    private void runHibernateCycle() {
        if (!isRunning || TPSUtils.isTPSBelow(10.0)) {
            return;
        }
        
        for (World world : plugin.getServer().getWorlds()) {
            processWorldForHibernate(world);
        }
    }
    
    private void processWorldForHibernate(World world) {
        int hibernateRadius = plugin.getConfig().getInt("hibernate.radius", 64);
        
        Set<Chunk> chunksToHibernate = new HashSet<>();
        Set<Chunk> activeChunks = new HashSet<>();
        
        for (Player player : world.getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            activeChunks.add(playerChunk);
            
            int radiusInChunks = (int) Math.ceil(hibernateRadius / 16.0);
            for (int x = -radiusInChunks; x <= radiusInChunks; x++) {
                for (int z = -radiusInChunks; z <= radiusInChunks; z++) {
                    Chunk nearbyChunk = world.getChunkAt(playerChunk.getX() + x, playerChunk.getZ() + z);
                    activeChunks.add(nearbyChunk);
                }
            }
        }
        
        for (Chunk chunk : world.getLoadedChunks()) {
            if (!activeChunks.contains(chunk)) {
                chunksToHibernate.add(chunk);
            }
        }
        
        for (Chunk chunk : chunksToHibernate) {
            String chunkKey = getChunkKey(chunk);
            if (!hibernatedChunks.contains(chunkKey)) {
                hibernateChunkEntities(chunk);
                hibernatedChunks.add(chunkKey);
            }
        }
        
        Iterator<String> hibernatedChunkIter = hibernatedChunks.iterator();
        while (hibernatedChunkIter.hasNext()) {
            String chunkKey = hibernatedChunkIter.next();
            if (shouldWakeChunk(chunkKey, activeChunks)) {
                wakeChunk(chunkKey);
                hibernatedChunkIter.remove();
            }
        }
    }
    
    /** Hibernate entities in chunk (skips protected types) */
    private void hibernateChunkEntities(Chunk chunk) {
        Entity[] entities = chunk.getEntities();
        int hibernatedCount = 0;
        int skippedCount = 0;
        
        for (Entity entity : entities) {
            if (entity instanceof Player) {
                continue;
            }
            
            if (isEntityProtected(entity)) {
                skippedCount++;
                continue;
            }
            
            HibernationData data = new HibernationData(entity);
            String entityKey = getEntityKey(entity);
            hibernatedEntities.put(entityKey, data);
            
            entity.remove();
            hibernatedCount++;
        }
        
        if (hibernatedCount > 0 || skippedCount > 0) {
            LoggerUtils.debug("Hibernated " + hibernatedCount + " entities (skipped " + skippedCount + 
                             " protected) in chunk " + chunk.getX() + "," + chunk.getZ() + 
                             " of world " + chunk.getWorld().getName());
        }
    }
    
    private void wakeChunk(String chunkKey) {
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
        
        for (HibernationData data : entitiesToWake) {
            World world = org.bukkit.Bukkit.getWorld(data.worldName);
            if (world != null) {
                try {
                    Location loc = new Location(world, data.x, data.y, data.z);
                    EntityType type = EntityType.valueOf(data.entityType);
                    
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
    
    private boolean shouldWakeChunk(String chunkKey, Set<Chunk> activeChunks) {
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
    
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
    
    private String getEntityKey(Entity entity) {
        Location loc = entity.getLocation();
        return entity.getWorld().getName() + ":" + 
               loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ() + ":" + 
               entity.getUniqueId().toString();
    }
    
    public int getHibernatedChunkCount() {
        return hibernatedChunks.size();
    }
    
    public int getHibernatedEntityCount() {
        return hibernatedEntities.size();
    }
    
    public boolean isRunning() {
        return isRunning;
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !isRunning) {
            start();
        } else if (!enabled && isRunning) {
            stop();
        }
    }

    public void setRadius(int radius) {
        plugin.getConfig().set("hibernate.radius", radius);
        LoggerUtils.info("Hibernate radius set to: " + radius);
    }
}
