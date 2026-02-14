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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks and removes old dropped items with warnings */
public class ItemDropTracker {
    private final XreatOptimizer plugin;
    private final Map<UUID, Long> itemSpawnTimes = new ConcurrentHashMap<>();
    private BukkitTask checkTask;
    private BukkitTask countdownTask;
    private volatile boolean isRunning = false;

    private int itemLifetime = 600;
    private int warningTime = 10;

    public ItemDropTracker(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        itemLifetime = plugin.getConfig().getInt("item_removal.lifetime_seconds", 600);
        warningTime = plugin.getConfig().getInt("item_removal.warning_seconds", 10);
    }

    public void start() {
        checkTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::checkAndRemoveExpiredItems,
            20L,
            20L
        );

        countdownTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::showCountdownWarnings,
            20L,
            20L
        );

        Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::cleanupPickedUpItems,
            600L,
            600L
        );

        isRunning = true;
        LoggerUtils.info("Item drop tracker started. Items will be removed after " + itemLifetime + " seconds.");
    }

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

    public void trackItem(Item item) {
        if (!isRunning) return;
        itemSpawnTimes.put(item.getUniqueId(), System.currentTimeMillis());
    }

    /** Resolve entity by UUID */
    private org.bukkit.entity.Entity resolveEntity(UUID id) {
        try {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (e != null) return e;
        } catch (NoSuchMethodError ignored) {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (entity.getUniqueId().equals(id)) return entity;
                }
            }
        }
        return null;
    }

    private void checkAndRemoveExpiredItems() {
        if (!isRunning) return;

        long currentTime = System.currentTimeMillis();
        int removed = 0;
        List<UUID> toRemove = new ArrayList<>();

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

    private void cleanupPickedUpItems() {
        if (!isRunning) return;

        List<UUID> toRemove = new ArrayList<>();

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

    public int getTrackedItemCount() {
        return itemSpawnTimes.size();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void reload() {
        if (isRunning) {
            stop();
        }
        loadConfig();
        start();
    }
    
    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
        } catch (Exception e) {
        }
    }
}
