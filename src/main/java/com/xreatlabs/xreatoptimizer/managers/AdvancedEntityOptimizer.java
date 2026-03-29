package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.ProtectedEntities;
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
    private final Set<UUID> throttledEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, EntityImportance> entityImportanceCache = new ConcurrentHashMap<>();
    private volatile boolean isRunning = false;
    private boolean stackFusionEnabled = true;

    private enum EntityImportance {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    public AdvancedEntityOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        stackFusionEnabled = plugin.getConfig().getBoolean("enable_stack_fusion", true);
    }

    public void start() {
        if (isRunning) {
            return;
        }

        loadConfig();
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
            optimizationTask = null;
        }

        restoreAllAI();
        throttledEntities.clear();
        entityImportanceCache.clear();

        LoggerUtils.info("Advanced entity optimizer stopped.");
    }

    public void setEnabled(boolean enabled) {
        if (enabled == isRunning) {
            return;
        }

        if (enabled) {
            start();
        } else {
            stop();
        }
    }

    public boolean isEnabled() {
        return isRunning;
    }

    private void runAdvancedOptimizations() {
        if (!isRunning) {
            return;
        }

        performEntityStackFusion();
        applyTickThrottling();
        cleanupImportanceCache();
    }

    private void performEntityStackFusion() {
        if (!stackFusionEnabled) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            mergeWorldItems(world);
            mergeWorldExperience(world);
        }
    }

    private void mergeWorldItems(World world) {
        List<Item> items = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item) {
                Item item = (Item) entity;
                if (item.getPickupDelay() > 0) {
                    continue;
                }
                items.add(item);
            }
        }

        Set<UUID> merged = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            Item base = items.get(i);
            if (!base.isValid() || merged.contains(base.getUniqueId())) {
                continue;
            }

            for (int j = i + 1; j < items.size(); j++) {
                Item candidate = items.get(j);
                if (!candidate.isValid() || merged.contains(candidate.getUniqueId())) {
                    continue;
                }

                if (!canMerge(base, candidate)) {
                    continue;
                }

                int remaining = mergeIntoBase(base, candidate);
                if (remaining <= 0) {
                    merged.add(candidate.getUniqueId());
                    candidate.remove();
                }
            }
        }
    }

    private void mergeWorldExperience(World world) {
        List<ExperienceOrb> orbs = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity instanceof ExperienceOrb) {
                orbs.add((ExperienceOrb) entity);
            }
        }

        Set<UUID> merged = new HashSet<>();
        for (int i = 0; i < orbs.size(); i++) {
            ExperienceOrb base = orbs.get(i);
            if (!base.isValid() || merged.contains(base.getUniqueId())) {
                continue;
            }

            for (int j = i + 1; j < orbs.size(); j++) {
                ExperienceOrb candidate = orbs.get(j);
                if (!candidate.isValid() || merged.contains(candidate.getUniqueId())) {
                    continue;
                }

                if (!sameWorldAndNearby(base.getLocation(), candidate.getLocation(), 2.0)) {
                    continue;
                }

                base.setExperience(Math.min(32767, base.getExperience() + candidate.getExperience()));
                merged.add(candidate.getUniqueId());
                candidate.remove();
            }
        }
    }

    private boolean canMerge(Item first, Item second) {
        if (!sameWorldAndNearby(first.getLocation(), second.getLocation(), 2.25)) {
            return false;
        }

        if (ProtectedEntities.isProtected(first) || ProtectedEntities.isProtected(second)) {
            return false;
        }

        if (first.getCustomName() != null || second.getCustomName() != null) {
            return false;
        }

        return first.getItemStack().isSimilar(second.getItemStack());
    }

    private boolean sameWorldAndNearby(Location first, Location second, double maxDistanceSquared) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld())
            && first.distanceSquared(second) <= maxDistanceSquared;
    }

    private int mergeIntoBase(Item base, Item candidate) {
        org.bukkit.inventory.ItemStack baseStack = base.getItemStack();
        org.bukkit.inventory.ItemStack candidateStack = candidate.getItemStack();

        int maxStack = baseStack.getMaxStackSize();
        int transferable = Math.min(maxStack - baseStack.getAmount(), candidateStack.getAmount());
        if (transferable <= 0) {
            return candidateStack.getAmount();
        }

        baseStack.setAmount(baseStack.getAmount() + transferable);
        candidateStack.setAmount(candidateStack.getAmount() - transferable);
        base.setItemStack(baseStack);
        candidate.setItemStack(candidateStack);
        return candidateStack.getAmount();
    }

    private void applyTickThrottling() {
        for (World world : Bukkit.getWorlds()) {
            applyTickThrottlingToWorld(world);
        }
    }

    /** Apply AI throttling to distant entities. */
    private void applyTickThrottlingToWorld(World world) {
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            restoreWorldAI(world);
            return;
        }

        double currentTPS = TPSUtils.getTPS();
        double nearDistance = getDistanceTier("near", currentTPS);
        double mediumDistance = getDistanceTier("medium", currentTPS);
        double farDistance = getDistanceTier("far", currentTPS);

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player) {
                continue;
            }

            LivingEntity living = (LivingEntity) entity;
            UUID entityId = entity.getUniqueId();
            EntityImportance importance = getEntityImportance(living);

            if (importance == EntityImportance.CRITICAL) {
                restoreAI(living, entityId);
                continue;
            }

            double closestDistanceSq = Double.MAX_VALUE;
            for (Player player : players) {
                if (!player.getWorld().equals(entity.getWorld())) {
                    continue;
                }
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
                } catch (Exception ignored) {
                }
            } else if (!shouldThrottle && isThrottled) {
                restoreAI(living, entityId);
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

    /** Get distance tier adjusted for current TPS. */
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
        EntityImportance cached = entityImportanceCache.get(entityId);
        if (cached != null) {
            return cached;
        }

        EntityImportance importance = classifyEntityImportance(entity);
        entityImportanceCache.put(entityId, importance);
        return importance;
    }

    private EntityImportance classifyEntityImportance(LivingEntity entity) {
        if (entity.getCustomName() != null || ProtectedEntities.isProtected(entity)) {
            return EntityImportance.CRITICAL;
        }

        if (entity instanceof Tameable && ((Tameable) entity).isTamed()) {
            return EntityImportance.CRITICAL;
        }

        EntityType type = entity.getType();
        if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || "WARDEN".equals(type.name())) {
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

    private void cleanupImportanceCache() {
        entityImportanceCache.entrySet().removeIf(entry -> Bukkit.getEntity(entry.getKey()) == null);
    }

    private void restoreAllAI() {
        for (World world : Bukkit.getWorlds()) {
            restoreWorldAI(world);
        }
    }

    private void restoreWorldAI(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity) {
                restoreAI((LivingEntity) entity, entity.getUniqueId());
            }
        }
    }

    private void restoreAI(LivingEntity living, UUID entityId) {
        if (!throttledEntities.contains(entityId)) {
            return;
        }

        try {
            living.setAI(true);
        } catch (Exception ignored) {
        }
        throttledEntities.remove(entityId);
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getGroupCount() {
        return 0;
    }

    public int getThrottledEntityCount() {
        return throttledEntities.size();
    }

    public void setStackFusionEnabled(boolean enabled) {
        this.stackFusionEnabled = enabled;
        LoggerUtils.info("Stack fusion " + (enabled ? "enabled" : "disabled"));
    }
}
