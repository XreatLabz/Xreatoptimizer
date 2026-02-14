package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancedEntityOptimizer {
    private final XreatOptimizer plugin;
    private BukkitTask optimizationTask;
    private final Map<UUID, EntityGroup> entityGroups = new ConcurrentHashMap<>();
    private final Set<UUID> throttledEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, EntityImportance> entityImportanceCache = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;

    private enum EntityImportance {
        CRITICAL(1.0),
        HIGH(0.75),
        MEDIUM(0.5),
        LOW(0.25);

        final double priority;
        EntityImportance(double priority) {
            this.priority = priority;
        }
    }
    
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
            if (!entityLoc.getWorld().equals(centerLocation.getWorld())) {
                return false;
            }
            return entityLoc.distanceSquared(centerLocation) <= 25.0;
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
            if (memberEntityIds.isEmpty()) {
                totalCount = 0;
            }
        }
    }

    public AdvancedEntityOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        optimizationTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::runAdvancedOptimizations,
            200L,
            200L
        );
        
        isRunning = true;
        LoggerUtils.info("Advanced entity optimizer started.");
    }
    
    public void stop() {
        isRunning = false;
        if (optimizationTask != null) {
            optimizationTask.cancel();
        }
        
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (throttledEntities.contains(entity.getUniqueId()) && entity instanceof LivingEntity) {
                    try {
                        ((LivingEntity) entity).setAI(true);
                    } catch (Exception ignored) {}
                }
            }
        }

        entityGroups.clear();
        throttledEntities.clear();
        entityImportanceCache.clear();

        LoggerUtils.info("Advanced entity optimizer stopped.");
    }
    
    public void setEnabled(boolean enabled) {
        if (isRunning != enabled) {
            isRunning = enabled;
            LoggerUtils.info("Advanced entity optimizer " + (enabled ? "enabled" : "disabled"));
        }
    }
    
    public boolean isEnabled() {
        return isRunning;
    }
    
    private void runAdvancedOptimizations() {
        if (!isRunning) return;
        
        performEntityStackFusion();
        applyTickThrottling();
        cleanupOldGroups();
    }
    
    private void performEntityStackFusion() {
        if (!plugin.getConfig().getBoolean("enable_stack_fusion", true)) {
            return;
        }
        
        for (World world : Bukkit.getWorlds()) {
            processWorldForStackFusion(world);
        }
    }
    
    private void processWorldForStackFusion(World world) {
        Map<EntityType, List<Entity>> entitiesByType = new HashMap<>();
        
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player || entity.getCustomName() != null || hasPassengers(entity)) {
                continue;
            }
            
            if (isEntityTypeFusable(entity.getType())) {
                entitiesByType.computeIfAbsent(entity.getType(), k -> new ArrayList<>()).add(entity);
            }
        }
        
        for (Map.Entry<EntityType, List<Entity>> entry : entitiesByType.entrySet()) {
            List<Entity> entities = entry.getValue();
            if (entities.size() <= 1) continue;
            
            groupNearbyEntities(entities);
        }
    }
    
    private void groupNearbyEntities(List<Entity> entities) {
        for (Entity entity : entities) {
            UUID entityId = entity.getUniqueId();
            
            if (isEntityInGroup(entityId)) {
                continue;
            }
            
            EntityGroup targetGroup = findSuitableGroup(entity);
            
            if (targetGroup == null) {
                targetGroup = new EntityGroup(entity.getType(), entity.getLocation());
                entityGroups.put(entityId, targetGroup);
                targetGroup.addEntity(entity);
            } else {
                targetGroup.addEntity(entity);
            }
        }
    }
    
    private EntityGroup findSuitableGroup(Entity entity) {
        for (EntityGroup group : entityGroups.values()) {
            if (group.entityType == entity.getType() && group.canAddToGroup(entity)) {
                return group;
            }
        }
        
        return null;
    }
    
    private boolean isEntityInGroup(UUID entityId) {
        return entityGroups.containsKey(entityId);
    }
    
    /** Check if entity type can be fused */
    private boolean isEntityTypeFusable(EntityType type) {
        switch (type) {
            case DROPPED_ITEM:
            case EXPERIENCE_ORB:
                return true;
            default:
                return false;
        }
    }
    
    private void applyTickThrottling() {
        for (World world : Bukkit.getWorlds()) {
            applyTickThrottlingToWorld(world);
        }
    }
    
    /** Apply AI throttling to distant entities */
    private void applyTickThrottlingToWorld(World world) {
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }

        double currentTPS = TPSUtils.getTPS();
        double nearDistance = getDistanceTier("near", currentTPS);
        double mediumDistance = getDistanceTier("medium", currentTPS);
        double farDistance = getDistanceTier("far", currentTPS);

        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            if (!(entity instanceof LivingEntity)) continue;

            LivingEntity living = (LivingEntity) entity;
            UUID entityId = entity.getUniqueId();

            EntityImportance importance = getEntityImportance(living);

            if (importance == EntityImportance.CRITICAL) {
                if (throttledEntities.contains(entityId)) {
                    try {
                        living.setAI(true);
                        throttledEntities.remove(entityId);
                    } catch (Exception ignored) {}
                }
                continue;
            }

            double closestDistanceSq = Double.MAX_VALUE;
            for (Player player : players) {
                if (!player.getWorld().equals(entity.getWorld())) continue;
                double distSq = entity.getLocation().distanceSquared(player.getLocation());
                if (distSq < closestDistanceSq) {
                    closestDistanceSq = distSq;
                }
            }

            boolean shouldThrottle = shouldThrottleEntity(closestDistanceSq, importance, nearDistance, mediumDistance, farDistance);
            boolean isThrottled = throttledEntities.contains(entityId);

            if (shouldThrottle && !isThrottled) {
                try {
                    living.setAI(false);
                    throttledEntities.add(entityId);
                } catch (Exception e) {
                }
            } else if (!shouldThrottle && isThrottled) {
                try {
                    living.setAI(true);
                    throttledEntities.remove(entityId);
                } catch (Exception e) {
                    throttledEntities.remove(entityId);
                }
            }
        }
    }

    private boolean shouldThrottleEntity(double distanceSq, EntityImportance importance,
                                        double nearDist, double mediumDist, double farDist) {
        double nearDistSq = nearDist * nearDist;
        double mediumDistSq = mediumDist * mediumDist;
        double farDistSq = farDist * farDist;

        switch (importance) {
            case HIGH:
                return distanceSq > farDistSq;
            case MEDIUM:
                return distanceSq > mediumDistSq;
            case LOW:
                return distanceSq > nearDistSq;
            default:
                return false;
        }
    }

    /** Get distance tier adjusted for current TPS */
    private double getDistanceTier(String tier, double tps) {
        double multiplier = 1.0;

        if (tps < 15.0) {
            multiplier = 0.5;
        } else if (tps < 17.0) {
            multiplier = 0.7;
        } else if (tps < 19.0) {
            multiplier = 0.85;
        }

        switch (tier) {
            case "near":
                return 32.0 * multiplier;
            case "medium":
                return 64.0 * multiplier;
            case "far":
                return 96.0 * multiplier;
            default:
                return 64.0 * multiplier;
        }
    }

    private EntityImportance getEntityImportance(LivingEntity entity) {
        UUID entityId = entity.getUniqueId();

        if (entityImportanceCache.containsKey(entityId)) {
            return entityImportanceCache.get(entityId);
        }

        EntityImportance importance = classifyEntityImportance(entity);
        entityImportanceCache.put(entityId, importance);
        return importance;
    }

    private EntityImportance classifyEntityImportance(LivingEntity entity) {
        if (entity.getCustomName() != null) {
            return EntityImportance.CRITICAL;
        }

        if (entity instanceof Tameable && ((Tameable) entity).isTamed()) {
            return EntityImportance.CRITICAL;
        }

        EntityType type = entity.getType();

        if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER) {
            return EntityImportance.CRITICAL;
        }

        if (entity instanceof Monster || type == EntityType.VILLAGER) {
            return EntityImportance.HIGH;
        }

        if (entity instanceof Animals) {
            return EntityImportance.MEDIUM;
        }

        if (entity instanceof Ambient || type == EntityType.BAT) {
            return EntityImportance.LOW;
        }

        return EntityImportance.MEDIUM;
    }
    
    private void cleanupOldGroups() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, EntityGroup>> iter = entityGroups.entrySet().iterator();
        
        while (iter.hasNext()) {
            Map.Entry<UUID, EntityGroup> entry = iter.next();
            EntityGroup group = entry.getValue();
            
            if (now - group.lastInteraction > 300000) {
                iter.remove();
            }
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public int getGroupCount() {
        return entityGroups.size();
    }
    
    public int getThrottledEntityCount() {
        return throttledEntities.size();
    }

    public void setStackFusionEnabled(boolean enabled) {
        LoggerUtils.info("Stack fusion " + (enabled ? "enabled" : "disabled"));
    }
    
    private boolean hasPassengers(Entity entity) {
        try {
            return !entity.getPassengers().isEmpty();
        } catch (NoSuchMethodError e) {
            try {
                Object passenger = entity.getClass().getMethod("getPassenger").invoke(entity);
                return passenger != null;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
