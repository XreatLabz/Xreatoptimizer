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
     * Check and remove items that have expired
     */
    private void checkAndRemoveExpiredItems() {
        if (!isRunning) return;

        long currentTime = System.currentTimeMillis();
        int removed = 0;

        // Create a list to track items to remove
        java.util.List<UUID> toRemove = new java.util.ArrayList<>();

        // Iterate through tracked items
        for (Map.Entry<UUID, Long> entry : itemSpawnTimes.entrySet()) {
            UUID itemId = entry.getKey();
            long spawnTime = entry.getValue();
            long age = (currentTime - spawnTime) / 1000; // Age in seconds

            // Check if item has exceeded lifetime
            if (age >= itemLifetime) {
                // Find and remove the item entity
                boolean found = false;
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    for (org.bukkit.entity.Entity entity : world.getEntities()) {
                        if (entity.getUniqueId().equals(itemId) && entity instanceof Item) {
                            entity.remove();
                            removed++;
                            found = true;
                            break;
                        }
                    }
                    if (found) break;
                }
                toRemove.add(itemId);
            }
        }

        // Remove tracked items that were deleted or no longer exist
        for (UUID id : toRemove) {
            itemSpawnTimes.remove(id);
        }

        if (removed > 0) {
            LoggerUtils.info("Removed " + removed + " expired items (10 minutes old).");
        }
    }

    /**
     * Cleanup items that were picked up by players (memory leak prevention)
     */
    private void cleanupPickedUpItems() {
        if (!isRunning) return;

        java.util.List<UUID> toRemove = new java.util.ArrayList<>();

        // Check if tracked items still exist in the world
        for (UUID itemId : itemSpawnTimes.keySet()) {
            boolean exists = false;

            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(itemId) && entity instanceof Item) {
                        exists = true;
                        break;
                    }
                }
                if (exists) break;
            }

            // If item doesn't exist anymore (picked up or despawned naturally), stop tracking it
            if (!exists) {
                toRemove.add(itemId);
            }
        }

        for (UUID id : toRemove) {
            itemSpawnTimes.remove(id);
        }

        if (!toRemove.isEmpty()) {
            LoggerUtils.debug("Cleaned up " + toRemove.size() + " items from tracking (picked up or naturally despawned).");
        }
    }

    /**
     * Show countdown warnings to nearby players
     */
    private void showCountdownWarnings() {
        if (!isRunning) return;

        long currentTime = System.currentTimeMillis();

        // Check each tracked item
        for (Map.Entry<UUID, Long> entry : itemSpawnTimes.entrySet()) {
            UUID itemId = entry.getKey();
            long spawnTime = entry.getValue();
            long age = (currentTime - spawnTime) / 1000; // Age in seconds
            long timeRemaining = itemLifetime - age;

            // Show countdown only in the last warning_seconds
            if (timeRemaining > 0 && timeRemaining <= warningTime) {
                // Find the item entity
                Bukkit.getWorlds().forEach(world -> {
                    world.getEntities().stream()
                        .filter(entity -> entity.getUniqueId().equals(itemId))
                        .filter(entity -> entity instanceof Item)
                        .forEach(entity -> {
                            Item item = (Item) entity;

                            // Show countdown message to nearby players
                            String itemName = item.getItemStack().getType().name().replace("_", " ").toLowerCase();
                            String warningMessage = ChatColor.YELLOW + "⚠ Items despawning in " +
                                                  ChatColor.RED + timeRemaining + ChatColor.YELLOW +
                                                  " seconds!";

                            // Send message to players within 20 blocks
                            world.getNearbyEntities(item.getLocation(), 20, 20, 20).stream()
                                .filter(nearby -> nearby instanceof Player)
                                .map(nearby -> (Player) nearby)
                                .forEach(player -> {
                                    // Send action bar message (compatible with Spigot)
                                    sendActionBar(player, warningMessage);

                                    // Send chat message at specific intervals (10, 5, 3, 2, 1 seconds)
                                    if (timeRemaining == 10 || timeRemaining == 5 ||
                                        timeRemaining == 3 || timeRemaining == 2 || timeRemaining == 1) {
                                        player.sendMessage(ChatColor.RED + "[XreatOptimizer] " +
                                                         ChatColor.YELLOW + "Items will disappear in " +
                                                         ChatColor.RED + timeRemaining +
                                                         ChatColor.YELLOW + " second" +
                                                         (timeRemaining == 1 ? "" : "s") + "!");
                                    }
                                });
                        });
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
