package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Implements entity culling to skip processing for entities outside player view cones
 */
public class EntityCullingManager {
    private final XreatOptimizer plugin;
    private final Map<UUID, Set<UUID>> playerVisibleEntities = new HashMap<>();
    private volatile boolean isRunning = false;
    private BukkitTask cullingTask;

    public EntityCullingManager(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        isRunning = true;
        cullingTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> {
                if (!isRunning) return;
                for (World world : Bukkit.getWorlds()) {
                    processWorldVisibility(world);
                }
            },
            100L,
            100L // every 5 seconds
        );
        LoggerUtils.info("Entity culling manager started.");
    }
    
    public void stop() {
        isRunning = false;
        if (cullingTask != null) {
            cullingTask.cancel();
        }
        playerVisibleEntities.clear();
        LoggerUtils.info("Entity culling manager stopped.");
    }
    
    /**
     * Enable or disable entity culling
     */
    public void setEnabled(boolean enabled) {
        isRunning = enabled;
        if (!enabled) {
            playerVisibleEntities.clear();
        }
        LoggerUtils.info("Entity culling manager " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if entity culling is enabled
     */
    public boolean isEnabled() {
        return isRunning;
    }
    
    /**
     * Determines if an entity should be processed based on player visibility
     */
    public boolean shouldProcessEntity(Entity entity, Player viewer) {
        if (!isRunning) return true; // If disabled, process everything
        
        // Don't cull players or named entities
        if (entity instanceof Player || entity.getCustomName() != null) {
            return true;
        }
        
        // Use distance-based culling for older server versions
        if (!plugin.getVersionAdapter().isVersionAtLeast(1, 13)) {
            return isEntityInCullDistance(entity, viewer);
        }
        
        // For newer versions, use more sophisticated culling
        return isEntityInPlayerView(entity, viewer);
    }
    
    /**
     * Checks if an entity is within culling distance of a player
     */
    private boolean isEntityInCullDistance(Entity entity, Player player) {
        double maxDistance = plugin.getConfig().getDouble("entity_culling.max_distance", 64.0);
        return entity.getLocation().distanceSquared(player.getLocation()) <= (maxDistance * maxDistance);
    }
    
    /**
     * Checks if an entity is in the player's view cone (more sophisticated culling)
     */
    private boolean isEntityInPlayerView(Entity entity, Player player) {
        Location entityLoc = entity.getLocation();
        Location playerLoc = player.getLocation();
        
        // First, check distance
        double distanceSquared = entityLoc.distanceSquared(playerLoc);
        double maxDistance = plugin.getConfig().getDouble("entity_culling.max_distance", 64.0);
        
        if (distanceSquared > (maxDistance * maxDistance)) {
            return false; // Too far away
        }
        
        // For very close entities, always process them
        if (distanceSquared < 25) { // 5 blocks
            return true;
        }
        
        // Check if in player's forward view cone
        double viewAngle = plugin.getConfig().getDouble("entity_culling.view_angle", 90.0); // 90 degree cone
        return isInViewCone(entityLoc, playerLoc, viewAngle);
    }
    
    /**
     * Checks if a location is within the player's view cone
     */
    private boolean isInViewCone(Location entityLoc, Location playerLoc, double viewAngle) {
        // Calculate vector from player to entity
        double dx = entityLoc.getX() - playerLoc.getX();
        double dz = entityLoc.getZ() - playerLoc.getZ();
        
        // Calculate angle between player's look direction and entity direction
        double playerYaw = Math.toRadians(playerLoc.getYaw());
        double entityAngle = Math.atan2(dz, dx);
        
        // Normalize angles to [-π, π]
        double angleDiff = entityAngle - playerYaw;
        while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
        
        // Check if entity is within view angle (half angle since angle goes both ways)
        return Math.abs(angleDiff) <= Math.toRadians(viewAngle / 2.0);
    }
    
    /**
     * Processes entity visibility for all players in a world
     */
    public void processWorldVisibility(World world) {
        for (Player player : world.getPlayers()) {
            processPlayerVisibility(player);
        }
    }
    
    /**
     * Processes entity visibility specifically for a player
     */
    public void processPlayerVisibility(Player player) {
        Set<UUID> visibleEntities = new HashSet<>();
        Location playerLoc = player.getLocation();
        
        double maxDistance = plugin.getConfig().getDouble("entity_culling.max_distance", 64.0);
        double maxDistanceSquared = maxDistance * maxDistance;
        
        for (Entity entity : player.getWorld().getEntities()) {
            // Skip players and named entities
            if (entity instanceof Player || entity.getCustomName() != null) {
                continue;
            }
            
            // Check distance
            if (entity.getLocation().distanceSquared(playerLoc) <= maxDistanceSquared) {
                // For performance, we'll just add all entities within distance
                // In a full implementation, we'd check the view cone
                visibleEntities.add(entity.getUniqueId());
            }
        }
        
        playerVisibleEntities.put(player.getUniqueId(), visibleEntities);
    }
    
    /**
     * Gets the set of entities visible to a player
     */
    public Set<UUID> getVisibleEntities(Player player) {
        return playerVisibleEntities.getOrDefault(player.getUniqueId(), Collections.emptySet());
    }
    
    /**
     * Checks if the culling manager is running
     */
    public boolean isRunning() {
        return isRunning;
    }
}