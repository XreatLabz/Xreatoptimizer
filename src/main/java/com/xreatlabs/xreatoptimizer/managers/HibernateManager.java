package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.ProtectedEntities;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conservative hibernation manager.
 *
 * This version no longer removes entities from the world. Instead, it only tracks
 * distant idle chunks so higher level systems can treat them as hibernation candidates
 * without risking entity data loss.
 */
public class HibernateManager {

    private final XreatOptimizer plugin;
    private BukkitTask hibernateTask;
    private final Set<String> hibernatedChunks = ConcurrentHashMap.newKeySet();
    private volatile boolean isRunning = false;
    private int configuredRadius = 64;
    private Integer runtimeRadiusOverride = null;

    public HibernateManager(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        configuredRadius = Math.max(16, plugin.getConfig().getInt("hibernate.radius", 64));
        if (runtimeRadiusOverride == null) {
            runtimeRadiusOverride = configuredRadius;
        }
    }

    /** Check if entity should be protected. */
    private boolean isEntityProtected(Entity entity) {
        return ProtectedEntities.isProtected(entity);
    }

    public void start() {
        loadConfig();
        if (!plugin.getConfig().getBoolean("hibernate.enabled", false)) {
            LoggerUtils.info("Hibernate manager is disabled via config.");
            return;
        }

        if (isRunning) {
            return;
        }

        hibernateTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::runHibernateCycle,
            200L,
            400L
        );

        isRunning = true;
        LoggerUtils.info("Hibernate manager started in safe tracking mode.");
    }

    public void stop() {
        isRunning = false;
        if (hibernateTask != null) {
            hibernateTask.cancel();
            hibernateTask = null;
        }
        hibernatedChunks.clear();
        runtimeRadiusOverride = configuredRadius;
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
        int hibernateRadius = getActiveRadius();

        Set<Chunk> activeChunks = new HashSet<>();
        for (Player player : world.getPlayers()) {
            Chunk playerChunk = player.getLocation().getChunk();
            activeChunks.add(playerChunk);

            int radiusInChunks = (int) Math.ceil(hibernateRadius / 16.0);
            for (int x = -radiusInChunks; x <= radiusInChunks; x++) {
                for (int z = -radiusInChunks; z <= radiusInChunks; z++) {
                    activeChunks.add(world.getChunkAt(playerChunk.getX() + x, playerChunk.getZ() + z));
                }
            }
        }

        Set<String> trackedThisCycle = new HashSet<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            if (!activeChunks.contains(chunk) && shouldMarkChunk(chunk)) {
                String chunkKey = getChunkKey(chunk);
                trackedThisCycle.add(chunkKey);
                hibernatedChunks.add(chunkKey);
            }
        }

        Iterator<String> iterator = hibernatedChunks.iterator();
        while (iterator.hasNext()) {
            String chunkKey = iterator.next();
            if (!chunkKey.startsWith(world.getName() + ":")) {
                continue;
            }
            if (!trackedThisCycle.contains(chunkKey) && shouldWakeChunk(chunkKey, activeChunks)) {
                iterator.remove();
            }
        }
    }

    private boolean shouldMarkChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                return false;
            }

            if (!isEntityProtected(entity)) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldWakeChunk(String chunkKey, Set<Chunk> activeChunks) {
        String[] parts = chunkKey.split(":");
        if (parts.length < 3) {
            return false;
        }

        String worldName = parts[0];
        int x = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);

        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return true;
        }

        Chunk chunk = world.getChunkAt(x, z);
        return activeChunks.contains(chunk);
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public int getHibernatedChunkCount() {
        return hibernatedChunks.size();
    }

    public int getHibernatedEntityCount() {
        int count = 0;
        for (String chunkKey : hibernatedChunks) {
            String[] parts = chunkKey.split(":");
            if (parts.length < 3) {
                continue;
            }

            World world = org.bukkit.Bukkit.getWorld(parts[0]);
            if (world == null) {
                continue;
            }

            Chunk chunk = world.getChunkAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof Player) && !ProtectedEntities.isProtected(entity)) {
                    count++;
                }
            }
        }
        return count;
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
        configuredRadius = Math.max(16, radius);
        if (runtimeRadiusOverride == null) {
            runtimeRadiusOverride = configuredRadius;
        }
        LoggerUtils.info("Hibernate radius set to: " + configuredRadius);
    }

    public void setRuntimeRadius(int radius) {
        runtimeRadiusOverride = Math.max(16, radius);
        LoggerUtils.debug("Hibernate runtime radius set to: " + runtimeRadiusOverride);
    }

    public void resetRuntimeRadius() {
        runtimeRadiusOverride = configuredRadius;
        LoggerUtils.debug("Hibernate runtime radius reset to configured value: " + configuredRadius);
    }

    public int getActiveRadius() {
        return runtimeRadiusOverride != null ? runtimeRadiusOverride : configuredRadius;
    }
}
