package com.xreatlabs.xreatoptimizer.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class EntityUtils {
    
    public static Map<EntityType, Integer> countEntities() {
        Map<EntityType, Integer> entityCounts = new HashMap<>();
        
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                EntityType type = entity.getType();
                entityCounts.put(type, entityCounts.getOrDefault(type, 0) + 1);
            }
        }
        
        return entityCounts;
    }
    
    public static Map<EntityType, Integer> countEntitiesInWorld(World world) {
        Map<EntityType, Integer> entityCounts = new HashMap<>();
        
        for (Entity entity : world.getEntities()) {
            EntityType type = entity.getType();
            entityCounts.put(type, entityCounts.getOrDefault(type, 0) + 1);
        }
        
        return entityCounts;
    }
    
    public static int getTotalEntityCount() {
        try {
            if (Bukkit.isPrimaryThread()) {
                int count = 0;
                for (World world : Bukkit.getWorlds()) {
                    count += world.getEntities().size();
                }
                return count;
            } else {
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
    
    public static Map<EntityType, Integer> getCountsForTypes(EntityType... types) {
        Map<EntityType, Integer> counts = new HashMap<>();
        Map<EntityType, Integer> allCounts = countEntities();
        
        for (EntityType type : types) {
            counts.put(type, allCounts.getOrDefault(type, 0));
        }
        
        return counts;
    }
    
    /** Remove excess entities of type (protected entities skipped) */
    public static int removeExcessEntities(World world, EntityType type, int maxCount) {
        if (ProtectedEntities.isProtectedType(type)) {
            return 0;
        }
        
        int removed = 0;
        int currentCount = 0;
        
        for (Entity entity : world.getEntities()) {
            if (entity.getType() == type && !ProtectedEntities.isProtected(entity)) {
                currentCount++;
            }
        }
        
        if (currentCount > maxCount) {
            int toRemove = currentCount - maxCount;
            
            for (Entity entity : world.getEntities()) {
                if (toRemove <= 0) break;
                
                if (entity.getType() == type && !ProtectedEntities.isProtected(entity)) {
                    entity.remove();
                    removed++;
                    toRemove--;
                }
            }
        }
        
        return removed;
    }
    
    public static boolean hasExcessiveEntities(World world, EntityType type, int threshold) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity.getType() == type) {
                count++;
                if (count > threshold) {
                    return true;
                }
            }
        }
        return false;
    }
}
