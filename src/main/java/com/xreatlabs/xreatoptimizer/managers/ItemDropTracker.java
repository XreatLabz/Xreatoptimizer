package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks dropped items and removes them after a configurable time period
 * with countdown warnings
 */
public class ItemDropTracker {
    private final XreatOptimizer plugin;
    private final Map<UUID, Long> itemSpawnTimes = new ConcurrentHashMap<>();
    private BukkitTask checkTask;
    private BukkitTask countdownTask;
    private volatile boolean isRunning = false;

    // Configuration values (in seconds)
    private int itemLifetime = 600; // 10 minutes default
    private int warningTime = 10;   // Last 10 seconds default

    public ItemDropTracker(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Load configuration values
     */
    public void loadConfig() {
        itemLifetime = plugin.getConfig().getInt("item_removal.lifetime_seconds", 600);
        warningTime = plugin.getConfig().getInt("item_removal.warning_seconds", 10);
    }

    /**
     * Starts the item tracking system
     */
    public void start() {
        // Task to check and remove expired items (runs every second)
        checkTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::checkAndRemoveExpiredItems,
            20L,  // Initial delay (1 second)
            20L   // Repeat every 1 second
        );

        // Task to show countdown warnings (runs every second)
        countdownTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::showCountdownWarnings,
            20L,  // Initial delay (1 second)
            20L   // Repeat every 1 second
        );

        // Task to cleanup picked up items from tracking (runs every 30 seconds)
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::cleanupPickedUpItems,
            600L,  // Initial delay (30 seconds)
            600L   // Repeat every 30 seconds
        );

        isRunning = true;
        LoggerUtils.info("Item drop tracker started. Items will be removed after " + itemLifetime + " seconds.");
    }

    /**
     * Stops the item tracking system
     */
    public void stop() {
        isRunning = false;
        if (checkTask != null) {
            checkTask.cancel();
        }
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        itemSpawnTimes.clear();
        LoggerUtils.info("Item drop tracker stopped.");
    }

    /**
     * Track a newly spawned item
     */
    public void trackItem(Item item) {
        if (!isRunning) return;
        itemSpawnTimes.put(item.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Resolve an entity by UUID efficiently.
     * Uses Bukkit.getEntity() on 1.12+ with fallback to world iteration.
     */
    private org.bukkit.entity.Entity resolveEntity(UUID id) {
        // Try Bukkit.getEntity (available 1.12+)
        try {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (e != null) return e;
        } catch (NoSuchMethodError ignored) {
            // Fallback for pre-1.12
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(id)) return entity;
                }
            }
        }
        return null;
    }

    /**
     * Check and remove items that have expired
     */
    private void checkAndRemoveExpiredItems() {
        if (!isRunning) return;

        long currentTime = System.currentTimeMillis();
        int removed = 0;
        java.util.List<UUID> toRemove = new java.util.ArrayList<>();

        for (Map.Entry<UUID, Long> entry : itemSpawnTimes.entrySet()) {
            UUID itemId = entry.getKey();
            long spawnTime = entry.getValue();
            long age = (currentTime - spawnTime) / 1000;

            if (age >= itemLifetime) {
                org.bukkit.entity.Entity entity = resolveEntity(itemId);
                if (entity instanceof Item) {
                    entity.remove();
                    removed++;
                }
                toRemove.add(itemId);
            }
        }

        for (UUID id : toRemove) {
            itemSpawnTimes.remove(id);
        }

        if (removed > 0) {
            LoggerUtils.info("Removed " + removed + " expired items (" + itemLifetime + "s old).");
        }
    }

    /**
     * Cleanup items that were picked up by players (memory leak prevention)
     */
    private void cleanupPickedUpItems() {
        if (!isRunning) return;

        java.util.List<UUID> toRemove = new java.util.ArrayList<>();

        for (UUID itemId : itemSpawnTimes.keySet()) {
            org.bukkit.entity.Entity entity = resolveEntity(itemId);
            if (entity == null || !(entity instanceof Item) || entity.isDead()) {
                toRemove.add(itemId);
            }
        }

        for (UUID id : toRemove) {
            itemSpawnTimes.remove(id);
        }

        if (!toRemove.isEmpty()) {
            LoggerUtils.debug("Cleaned up " + toRemove.size() + " items from tracking.");
        }
    }

    /**
     * Show countdown warnings to nearby players
     */
    private void showCountdownWarnings() {
        if (!isRunning) return;

        long currentTime = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : itemSpawnTimes.entrySet()) {
            UUID itemId = entry.getKey();
            long spawnTime = entry.getValue();
            long age = (currentTime - spawnTime) / 1000;
            long timeRemaining = itemLifetime - age;

            if (timeRemaining > 0 && timeRemaining <= warningTime) {
                org.bukkit.entity.Entity entity = resolveEntity(itemId);
                if (entity == null || !(entity instanceof Item)) continue;
                
                Item item = (Item) entity;
                String warningMessage = ChatColor.YELLOW + "\u26A0 Items despawning in " +
                                      ChatColor.RED + timeRemaining + ChatColor.YELLOW + " seconds!";

                item.getWorld().getNearbyEntities(item.getLocation(), 20, 20, 20).stream()
                    .filter(nearby -> nearby instanceof Player)
                    .map(nearby -> (Player) nearby)
                    .forEach(player -> {
                        sendActionBar(player, warningMessage);
                        if (timeRemaining == 10 || timeRemaining == 5 ||
                            timeRemaining == 3 || timeRemaining == 2 || timeRemaining == 1) {
                            player.sendMessage(ChatColor.RED + "[XreatOptimizer] " +
                                             ChatColor.YELLOW + "Items will disappear in " +
                                             ChatColor.RED + timeRemaining +
                                             ChatColor.YELLOW + " second" +
                                             (timeRemaining == 1 ? "" : "s") + "!");
                        }
                    });
            }
        }
    }

    /**
     * Manually remove all tracked items immediately
     */
    public int removeAllItems() {
        int removed = 0;

        for (UUID itemId : itemSpawnTimes.keySet()) {
            Bukkit.getWorlds().forEach(world -> {
                world.getEntities().stream()
                    .filter(entity -> entity.getUniqueId().equals(itemId))
                    .filter(entity -> entity instanceof Item)
                    .forEach(entity -> {
                        entity.remove();
                    });
            });
            removed++;
        }

        itemSpawnTimes.clear();
        LoggerUtils.info("Manually removed " + removed + " tracked items.");
        return removed;
    }

    /**
     * Get the number of currently tracked items
     */
    public int getTrackedItemCount() {
        return itemSpawnTimes.size();
    }

    /**
     * Check if the tracker is running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Reload configuration and restart
     */
    public void reload() {
        if (isRunning) {
            stop();
        }
        loadConfig();
        start();
    }
    
    /**
     * Version-safe action bar sender.
     * Works on 1.8+ with fallback for servers without spigot() method.
     */
    private void sendActionBar(Player player, String message) {
        try {
            // Try Spigot's action bar method (1.9+)
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Fallback for 1.8 or servers without BungeeCord chat API
            // Just skip action bar on older versions - chat messages still work
        } catch (Exception e) {
            // Any other error, silently ignore
        }
    }
}
