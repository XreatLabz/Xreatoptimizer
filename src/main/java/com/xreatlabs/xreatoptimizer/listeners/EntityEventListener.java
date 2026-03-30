package com.xreatlabs.xreatoptimizer.listeners;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.ProtectedEntities;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

/** Entity spawn event handler */
public class EntityEventListener implements Listener {

    private final XreatOptimizer plugin;

    public EntityEventListener(XreatOptimizer plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!plugin.getConfig().getBoolean("entity_limiter.enabled", false)) {
            return;
        }

        EntityType type = event.getEntityType();
        String typeName = type.name();

        if (typeName.equals("DROPPED_ITEM") || typeName.equals("ITEM")) {
            return;
        }

        if (typeName.equals("EXPERIENCE_ORB")) {
            return;
        }

        if (ProtectedEntities.isProtectedType(type) || ProtectedEntities.isProjectile(type)) {
            return;
        }

        if (event.getEntity().getCustomName() != null) {
            return;
        }

        int currentEntities = event.getLocation().getWorld().getEntities().size();
        String worldName = event.getLocation().getWorld().getName();
        int maxEntities = plugin.getWorldConfig() != null
            ? plugin.getWorldConfig().getMaxEntities(worldName)
            : plugin.getConfig().getInt("entity_limiter.default_entities_per_world", 500);

        if (currentEntities >= maxEntities) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (plugin.getItemDropTracker() != null) {
            plugin.getItemDropTracker().trackItem(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("entity_limiter.limit_creature_spawns", false)) {
            return;
        }

        EntityType type = event.getEntityType();
        if (ProtectedEntities.isProtectedType(type)) {
            return;
        }

        if (event.getEntity().getCustomName() != null) {
            return;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (isPlayerTriggeredSpawn(reason)) {
            return;
        }

        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL) {
            int currentEntities = event.getLocation().getWorld().getEntities().size();
            String worldName = event.getLocation().getWorld().getName();
            int maxEntities = plugin.getWorldConfig() != null
                ? plugin.getWorldConfig().getMaxEntities(worldName)
                : plugin.getConfig().getInt("entity_limiter.default_entities_per_world", 500);

            if (currentEntities >= maxEntities) {
                event.setCancelled(true);
            }
        }
    }

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
