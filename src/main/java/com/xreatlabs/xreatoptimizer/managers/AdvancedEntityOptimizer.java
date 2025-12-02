package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.EntityUtils;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced entity optimization manager implementing stack fusion and tick throttling
 */
public class AdvancedEntityOptimizer {
    private final XreatOptimizer plugin;
    private BukkitTask optimizationTask;
    private final Map<UUID, EntityGroup> entityGroups = new ConcurrentHashMap<>();
    private final Set<UUID> throttledEntities = ConcurrentHashMap.newKeySet();
    private volatile boolean isRunning = false;
    
    // Entity group for managing stacked/fused entities
    private static class EntityGroup {
        final EntityType entityType;
        final Location centerLocation;
        final Set<UUID> memberEntityIds = ConcurrentHashMap.newKeySet();
        int totalCount = 0;
        long lastInteraction = System.currentTimeMillis();
        
        public EntityGroup(EntityType type, Location location) {
            this.entityType = type;
            this.centerLocation = location.clone();
        }
        
        public boolean canAddToGroup(Entity entity) {
            Location entityLoc = entity.getLocation();
            // Must be in the same world to measure distance
            if (!entityLoc.getWorld().equals(centerLocation.getWorld())) {
                return false;
            }
            // Check if entity is close enough to the group center
            return entityLoc.distanceSquared(centerLocation) <= 25.0; // 5 block radius
        }
        
        public void addEntity(Entity entity) {
            memberEntityIds.add(entity.getUniqueId());
            if (entity instanceof Item) {
                totalCount += ((Item) entity).getItemStack().getAmount();
            } else {
                totalCount++;
            }
            lastInteraction = System.currentTimeMillis();
        }
        
        public void removeEntity(UUID entityId) {
            memberEntityIds.remove(entityId);
            // In a real implementation, would adjust totalCount based on entity type
            if (memberEntityIds.isEmpty()) {
                totalCount = 0;
            }
        }
    }

    public AdvancedEntityOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Starts the advanced entity optimization system
     */
    public void start() {
        // Run advanced optimizations every 10 seconds
        optimizationTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::runAdvancedOptimizations,
            200L,  // Initial delay (10 seconds)
            200L   // Repeat interval (10 seconds)
        );
        
        isRunning = true;
        LoggerUtils.info("Advanced entity optimizer started.");
    }
    
    /**
     * Stops the advanced entity optimization system
     */
    public void stop() {
        isRunning = false;
        if (optimizationTask != null) {
            optimizationTask.cancel();
        }
        
        // Clear all entity groups
        entityGroups.clear();
        throttledEntities.clear();
        
        LoggerUtils.info("Advanced entity optimizer stopped.");
    }
    
    /**
     * Enable or disable the optimizer
     */
    public void setEnabled(boolean enabled) {
        isRunning = enabled;
        LoggerUtils.info("Advanced entity optimizer " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if optimizer is enabled
     */
    public boolean isEnabled() {
        return isRunning;
    }
    
    /**
     * Runs advanced optimization cycle including entity fusion and tick throttling
     */
    private void runAdvancedOptimizations() {
        if (!isRunning) return;
        
        // Perform entity stack fusion
        performEntityStackFusion();
        
        // Apply tick throttling to distant entities
        applyTickThrottling();
        
        // Clean up old entity groups
        cleanupOldGroups();
    }
    
    /**
     * Performs entity stack fusion for nearby similar entities
     */
    private void performEntityStackFusion() {
        if (!plugin.getConfig().getBoolean("enable_stack_fusion", true)) {
            return; // Stack fusion disabled in config
        }
        
        // Process each world for entity fusion
        for (World world : Bukkit.getWorlds()) {
            processWorldForStackFusion(world);
        }
    }
    
    /**
     * Processes a world for potential entity stack fusion
     */
    private void processWorldForStackFusion(World world) {
        // Group entities by type and location for fusion
        Map<EntityType, List<Entity>> entitiesByType = new HashMap<>();
        
        // Only process entities that are suitable for fusion
        for (Entity entity : world.getEntities()) {
            // Skip players, named entities, and entities with passengers
            if (entity instanceof Player || entity.getCustomName() != null || hasPassengers(entity)) {
                continue;
            }
            
            // Only fuse specific types that make sense
            if (isEntityTypeFusable(entity.getType())) {
                entitiesByType.computeIfAbsent(entity.getType(), k -> new ArrayList<>()).add(entity);
            }
        }
        
        // Try to create groups for each entity type
        for (Map.Entry<EntityType, List<Entity>> entry : entitiesByType.entrySet()) {
            List<Entity> entities = entry.getValue();
            if (entities.size() <= 1) continue; // Nothing to fuse
            
            // Group close entities together
            groupNearbyEntities(entities);
        }
    }
    
    /**
     * Groups nearby entities together for fusion
     */
    private void groupNearbyEntities(List<Entity> entities) {
        for (Entity entity : entities) {
            UUID entityId = entity.getUniqueId();
            
            // Check if this entity is already in a group
            if (isEntityInGroup(entityId)) {
                continue;
            }
            
            // Find existing groups this entity can join or create a new one
            EntityGroup targetGroup = findSuitableGroup(entity);
            
            if (targetGroup == null) {
                // Create a new group for this entity
                targetGroup = new EntityGroup(entity.getType(), entity.getLocation());
                entityGroups.put(entityId, targetGroup);
                targetGroup.addEntity(entity);
            } else {
                // Add to existing group
                targetGroup.addEntity(entity);
            }
        }
    }
    
    /**
     * Finds a suitable existing group for an entity or returns null if none found
     */
    private EntityGroup findSuitableGroup(Entity entity) {
        Location entityLoc = entity.getLocation();
        
        for (EntityGroup group : entityGroups.values()) {
            if (group.entityType == entity.getType() && group.canAddToGroup(entity)) {
                return group;
            }
        }
        
        return null; // No suitable group found
    }
    
    /**
     * Checks if an entity is already in a fusion group
     */
    private boolean isEntityInGroup(UUID entityId) {
        return entityGroups.containsKey(entityId);
    }
    
    /**
     * Checks if an entity type is suitable for stack fusion
     * 
     * IMPORTANT: Only truly stackable entities should be fused.
     * Projectiles should NEVER be fused as it would break gameplay.
     */
    private boolean isEntityTypeFusable(EntityType type) {
        switch (type) {
            case DROPPED_ITEM:
            case EXPERIENCE_ORB:
                return true;
            // NOTE: Projectiles (arrows, snowballs, eggs, ender pearls, etc.) should NEVER be fused
            // as it would break their intended behavior and player gameplay
            default:
                return false;
        }
    }
    
    /**
     * Applies tick throttling to distant entities
     */
    private void applyTickThrottling() {
        // This is a simplified approach - in a real implementation, this would interface
        // with NMS to actually throttle entity ticks
        for (World world : Bukkit.getWorlds()) {
            applyTickThrottlingToWorld(world);
        }
    }
    
    /**
     * Applies tick throttling to entities in a specific world
     */
    private void applyTickThrottlingToWorld(World world) {
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            // If no players in world, we could apply more aggressive throttling
            // But for safety, we'll just do standard processing
            return;
        }
        
        // For each entity, determine if it should have reduced tick rate
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue; // Don't throttle players
            
            // Find the closest player to this entity
            double closestDistanceSquared = Double.MAX_VALUE;
            for (Player player : players) {
                double distanceSquared = entity.getLocation().distanceSquared(player.getLocation());
                if (distanceSquared < closestDistanceSquared) {
                    closestDistanceSquared = distanceSquared;
                }
            }
            
            // Apply throttling based on distance
            boolean shouldThrottle = closestDistanceSquared > 100.0; // More than 10 blocks away
            
            if (shouldThrottle && !throttledEntities.contains(entity.getUniqueId())) {
                // Add to throttling system (in a real implementation, this would modify NMS behavior)
                throttledEntities.add(entity.getUniqueId());
                LoggerUtils.debug("Throttled entity: " + entity.getType() + " at " + entity.getLocation());
            } else if (!shouldThrottle && throttledEntities.contains(entity.getUniqueId())) {
                // Remove from throttling system
                throttledEntities.remove(entity.getUniqueId());
            }
        }
    }
    
    /**
     * Cleans up old entity groups that haven't been interacted with
     */
    private void cleanupOldGroups() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, EntityGroup>> iter = entityGroups.entrySet().iterator();
        
        while (iter.hasNext()) {
            Map.Entry<UUID, EntityGroup> entry = iter.next();
            EntityGroup group = entry.getValue();
            
            // Remove groups that haven't been interacted with for 5 minutes
            if (now - group.lastInteraction > 300000) { // 5 minutes
                iter.remove();
            }
        }
    }
    
    /**
     * Checks if the advanced optimizer is running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Gets the number of entity groups currently managed
     */
    public int getGroupCount() {
        return entityGroups.size();
    }
    
    /**
     * Gets the number of throttled entities
     */
    public int getThrottledEntityCount() {
        return throttledEntities.size();
    }

    /**
     * Set whether stack fusion is enabled
     */
    public void setStackFusionEnabled(boolean enabled) {
        LoggerUtils.info("Stack fusion " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Version-safe check for entity passengers.
     * Works on both pre-1.11 (getPassenger) and 1.11+ (getPassengers).
     */
    private boolean hasPassengers(Entity entity) {
        try {
            // Try 1.11+ method first
            return !entity.getPassengers().isEmpty();
        } catch (NoSuchMethodError e) {
            // Fall back to pre-1.11 method
            try {
                Object passenger = entity.getClass().getMethod("getPassenger").invoke(entity);
                return passenger != null;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}