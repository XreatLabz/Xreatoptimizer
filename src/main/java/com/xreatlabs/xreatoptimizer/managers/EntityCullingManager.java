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
            100L
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
    
    public void setEnabled(boolean enabled) {
        if (isRunning != enabled) {
            isRunning = enabled;
            if (!enabled) {
                playerVisibleEntities.clear();
            }
        }
    }
    
    public boolean isEnabled() {
        return isRunning;
    }
    
    public boolean shouldProcessEntity(Entity entity, Player viewer) {
        if (!isRunning) return true;
        
        if (entity instanceof Player || entity.getCustomName() != null) {
            return true;
        }
        
        if (!plugin.getVersionAdapter().isVersionAtLeast(1, 13)) {
            return isEntityInCullDistance(entity, viewer);
        }
        
        return isEntityInPlayerView(entity, viewer);
    }
    
    private boolean isEntityInCullDistance(Entity entity, Player player) {
        double maxDistance = plugin.getConfig().getDouble("entity_culling.max_distance", 64.0);
        return entity.getLocation().distanceSquared(player.getLocation()) <= (maxDistance * maxDistance);
    }
    
    /** Check if entity is in player's view cone */
    private boolean isEntityInPlayerView(Entity entity, Player player) {
        Location entityLoc = entity.getLocation();
        Location playerLoc = player.getLocation();
        
        double distanceSquared = entityLoc.distanceSquared(playerLoc);
        double maxDistance = plugin.getConfig().getDouble("entity_culling.max_distance", 64.0);
        
        if (distanceSquared > (maxDistance * maxDistance)) {
            return false;
        }
        
        if (distanceSquared < 25) {
            return true;
        }
        
        double viewAngle = plugin.getConfig().getDouble("entity_culling.view_angle", 90.0);
        return isInViewCone(entityLoc, playerLoc, viewAngle);
    }
    
    private boolean isInViewCone(Location entityLoc, Location playerLoc, double viewAngle) {
        double dx = entityLoc.getX() - playerLoc.getX();
        double dz = entityLoc.getZ() - playerLoc.getZ();
        
        double playerYaw = Math.toRadians(playerLoc.getYaw());
        double entityAngle = Math.atan2(dz, dx);
        
        double angleDiff = entityAngle - playerYaw;
        while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
        while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
        
        return Math.abs(angleDiff) <= Math.toRadians(viewAngle / 2.0);
    }
    
    public void processWorldVisibility(World world) {
        for (Player player : world.getPlayers()) {
            processPlayerVisibility(player);
        }
    }
    
    public void processPlayerVisibility(Player player) {
        Set<UUID> visibleEntities = new HashSet<>();
        Location playerLoc = player.getLocation();
        
        double maxDistance = plugin.getConfig().getDouble("entity_culling.max_distance", 64.0);
        double maxDistanceSquared = maxDistance * maxDistance;
        
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Player || entity.getCustomName() != null) {
                continue;
            }
            
            if (entity.getLocation().distanceSquared(playerLoc) <= maxDistanceSquared) {
                visibleEntities.add(entity.getUniqueId());
            }
        }
        
        playerVisibleEntities.put(player.getUniqueId(), visibleEntities);
    }
    
    public Set<UUID> getVisibleEntities(Player player) {
        return playerVisibleEntities.getOrDefault(player.getUniqueId(), Collections.emptySet());
    }
    
    public boolean isRunning() {
        return isRunning;
    }
}
