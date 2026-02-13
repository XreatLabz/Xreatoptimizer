package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.Constants;
import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles the branding announcements that cannot be disabled
 */
public class AnnouncementSystem {
    private final XreatOptimizer plugin;
    private BukkitTask announcementTask;
    
    public AnnouncementSystem(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Starts the announcement system
     */
    public void start() {
        // Schedule announcements every 30 minutes (configurable)
        int intervalTicks = plugin.getConfig().getInt("general.broadcast_interval_minutes", 30) * 1200; // 30 * 60 * 20 / 20 = 18000 ticks = 30 min
        
        announcementTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::broadcastAnnouncements,
            intervalTicks, // Initial delay
            intervalTicks  // Repeat interval
        );
        
        LoggerUtils.info("Announcement system started. Will broadcast every " + 
                        plugin.getConfig().getInt("general.broadcast_interval_minutes", 30) + " minutes.");
    }
    
    /**
     * Stops the announcement system
     */
    public void stop() {
        if (announcementTask != null) {
            announcementTask.cancel();
            LoggerUtils.info("Announcement system stopped.");
        }
    }
    
    /**
     * Broadcasts the fixed announcement messages
     */
    @SuppressWarnings("deprecation")
    private void broadcastAnnouncements() {
        // Broadcast announcement (XreatLabs)
        TextComponent xreatLabsMessage = new TextComponent(Constants.ANNOUNCE_LINE);
        xreatLabsMessage.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("§bVisit XreatLabs\n§7Premium Minecraft Plugins & Tools\n§8» Click to visit website").create()
        ));
        xreatLabsMessage.setClickEvent(new ClickEvent(
            ClickEvent.Action.OPEN_URL,
            Constants.XREATLABS_URL
        ));
        
        // Send to all online players
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.spigot().sendMessage(xreatLabsMessage);
        });
        
        // Also log to console
        LoggerUtils.info("Brand announcement broadcast at " + java.time.LocalDateTime.now());
        LoggerUtils.info("XreatLabs: " + Constants.ANNOUNCE_LINE);
    }
    
    /**
     * Forces an immediate announcement broadcast (for testing or admin commands)
     */
    public void forceBroadcast() {
        broadcastAnnouncements();
    }

    public void setBroadcastInterval(int intervalMinutes) {
        if (announcementTask != null) {
            announcementTask.cancel();
        }
        int intervalTicks = intervalMinutes * 1200;
        announcementTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::broadcastAnnouncements,
            intervalTicks,
            intervalTicks
        );
        LoggerUtils.info("Announcement broadcast interval set to: " + intervalMinutes + " minutes");
    }
}