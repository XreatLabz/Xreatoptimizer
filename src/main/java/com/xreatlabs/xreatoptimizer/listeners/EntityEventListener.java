package com.xreatlabs.xreatoptimizer.listeners;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.ProtectedEntities;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Listener for entity-related events
 * Manages entity limits and stack fusion opportunities
 * 
 * IMPORTANT: This listener is designed to NEVER interfere with normal player gameplay.
 * All player-placed entities, projectiles, and important gameplay entities are exempt.
 * 
 * Compatible with Minecraft 1.8 - 1.21.10
 */
public class EntityEventListener implements Listener {

    private final XreatOptimizer plugin;

    public EntityEventListener(XreatOptimizer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntitySpawn(EntitySpawnEvent event) {
        // Track entity spawns for performance monitoring only
        if (plugin.getPerformanceMonitor() != null) {
            plugin.getPerformanceMonitor().incrementEntityCount();
        }

        // Check if entity spawn limiting is enabled at all
        if (!plugin.getConfig().getBoolean("entity_limiter.enabled", false)) {
            return; // Disabled by default - don't interfere with gameplay
        }

        EntityType type = event.getEntityType();
        String typeName = type.name();

        // NEVER block dropped items - they are managed by ItemDropTracker
        if (typeName.equals("DROPPED_ITEM") || typeName.equals("ITEM")) {
            return;
        }

        // NEVER block experience orbs
        if (typeName.equals("EXPERIENCE_ORB")) {
            return;
        }

        // NEVER block protected entity types (player-placed, vehicles, bosses, etc.)
        if (ProtectedEntities.isProtectedType(type)) {
            return;
        }

        // NEVER block projectiles (would break bows, crossbows, tridents, snowballs, eggs, etc.)
        if (ProtectedEntities.isProjectile(type)) {
            return;
        }

        // NEVER block entities with custom names (player-named pets, etc.)
        if (event.getEntity().getCustomName() != null) {
            return;
        }

        // Only apply limits to natural mob spawns when entity count exceeds limit
        if (plugin.getOptimizationManager() != null) {
            int currentEntities = event.getLocation().getWorld().getEntities().size();
            int maxEntities = plugin.getConfig().getInt("entity_limiter.default_entities_per_world", 500);

            // Only cancel spawn if world is over its entity limit
            if (currentEntities >= maxEntities) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemSpawn(ItemSpawnEvent event) {
        // Track item spawn time for timed removal system (only tracking, not blocking)
        if (plugin.getItemDropTracker() != null) {
            plugin.getItemDropTracker().trackItem(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Check if creature spawn limiting is enabled
        if (!plugin.getConfig().getBoolean("entity_limiter.limit_creature_spawns", false)) {
            return; // Disabled by default - don't interfere with gameplay
        }

        EntityType type = event.getEntityType();

        // NEVER block protected creatures
        if (ProtectedEntities.isProtectedType(type)) {
            return;
        }

        // NEVER block named creatures (pets, named mobs)
        if (event.getEntity().getCustomName() != null) {
            return;
        }

        // NEVER block spawner spawns, breeding, or player-triggered spawns
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (isPlayerTriggeredSpawn(reason)) {
            return;
        }

        // Only limit NATURAL spawns when enabled and world entity count exceeds limit
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL && plugin.getOptimizationManager() != null) {
            int currentEntities = event.getLocation().getWorld().getEntities().size();
            int maxEntities = plugin.getConfig().getInt("entity_limiter.default_entities_per_world", 500);

            // Only cancel spawn if world is over its entity limit
            if (currentEntities >= maxEntities) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Checks if a spawn reason is player-triggered and should never be blocked.
     * Uses version-safe string comparison for compatibility.
     */
    private boolean isPlayerTriggeredSpawn(CreatureSpawnEvent.SpawnReason reason) {
        if (reason == null) return false;
        
        String name = reason.name();
        return name.equals("SPAWNER") ||
               name.equals("BREEDING") ||
               name.equals("EGG") ||
               name.equals("SPAWNER_EGG") ||
               name.equals("SPAWN_EGG") ||
               name.equals("BUILD_IRONGOLEM") ||
               name.equals("BUILD_SNOWMAN") ||
               name.equals("BUILD_WITHER") ||
               name.equals("CURED") ||
               name.equals("DISPENSE_EGG") ||
               name.equals("COMMAND") ||
               name.equals("CUSTOM");
    }
}
