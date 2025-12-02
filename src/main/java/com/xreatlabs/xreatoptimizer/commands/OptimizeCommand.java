package com.xreatlabs.xreatoptimizer.commands;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.*;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.MessageUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Main command executor for XreatOptimizer
 */
public class OptimizeCommand implements CommandExecutor {
    private final XreatOptimizer plugin;
    
    public OptimizeCommand(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return showHelp(sender);
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "stats":
                return executeStats(sender);
            case "boost":
                return executeBoost(sender);
            case "pregen":
                return executePregen(sender, args);
            case "purge":
                return executePurge(sender);
            case "reload":
                return executeReload(sender);
            case "report":
                return executeReport(sender);
            case "clearcache":
                return executeClearCache(sender);
            case "dashboard":
            case "web":
                return executeDashboard(sender);
            case "help":
            case "?":
                return showHelp(sender);
            default:
                sender.sendMessage(ChatColor.RED + "Unknown command. Use /xreatopt help for available commands.");
                return true;
        }
    }
    
    private boolean showHelp(CommandSender sender) {
        MessageUtils.sendHeader(sender, "XreatOptimizer");
        
        if (sender.hasPermission("xreatopt.view")) {
            MessageUtils.sendCommandHelp(sender, "/xreatopt stats", "Show server performance statistics");
            MessageUtils.sendCommandHelp(sender, "/xreatopt report", "Generate performance report");
        }
        
        if (sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendCommandHelp(sender, "/xreatopt boost", "Trigger full optimization cycle");
            MessageUtils.sendCommandHelp(sender, "/xreatopt pregen <world> <radius> <speed>", "Pre-generate chunks");
            MessageUtils.sendCommandHelp(sender, "/xreatopt purge", "Clean up unused resources");
            MessageUtils.sendCommandHelp(sender, "/xreatopt reload", "Reload configuration");
            MessageUtils.sendCommandHelp(sender, "/xreatopt clearcache", "Clear RAM caches");
            MessageUtils.sendCommandHelp(sender, "/xreatopt notify test", "Test Discord notifications");
            MessageUtils.sendCommandHelp(sender, "/xreatopt dashboard", "Show web dashboard URL");
        }
        
        MessageUtils.sendFooter(sender);
        return true;
    }
    
    private boolean executeStats(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.view")) {
            MessageUtils.sendError(sender, "You don't have permission to view statistics.");
            return true;
        }
        
        // Get performance metrics
        double tps = TPSUtils.getTPS();
        long usedMemory = MemoryUtils.getUsedMemoryMB();
        long maxMemory = MemoryUtils.getMaxMemoryMB();
        double memoryPercent = MemoryUtils.getMemoryUsagePercentage();
        int entityCount = plugin.getPerformanceMonitor().getCurrentEntityCount();
        int chunkCount = plugin.getPerformanceMonitor().getCurrentChunkCount();
        int playerCount = org.bukkit.Bukkit.getOnlinePlayers().size();
        
        MessageUtils.sendHeader(sender, "Server Statistics");
        
        // TPS with status indicator
        MessageUtils.sendStatWithStatus(sender, "TPS", 
            MessageUtils.formatTPS(tps), 
            MessageUtils.getTpsStatus(tps));
        
        // Memory with progress bar
        MessageUtils.sendProgressBar(sender, "Memory", memoryPercent, 20);
        MessageUtils.sendStat(sender, "Memory Details", 
            MessageUtils.formatNumber(usedMemory) + "MB / " + MessageUtils.formatNumber(maxMemory) + "MB");
        
        // Other stats
        MessageUtils.sendStatWithStatus(sender, "Entities", 
            MessageUtils.formatNumber(entityCount),
            entityCount < 5000 ? MessageUtils.Status.GOOD : 
            entityCount < 10000 ? MessageUtils.Status.WARNING : MessageUtils.Status.CRITICAL);
        
        MessageUtils.sendStat(sender, "Loaded Chunks", MessageUtils.formatNumber(chunkCount));
        MessageUtils.sendStat(sender, "Players Online", String.valueOf(playerCount));
        
        // Profile with color
        String profile = plugin.getOptimizationManager().getCurrentProfile().name();
        String profileColor;
        switch (profile) {
            case "LIGHT": profileColor = MessageUtils.SUCCESS_HEX; break;
            case "NORMAL": profileColor = MessageUtils.INFO_HEX; break;
            case "AGGRESSIVE": profileColor = MessageUtils.WARNING_HEX; break;
            case "EMERGENCY": profileColor = MessageUtils.ERROR_HEX; break;
            default: profileColor = MessageUtils.PRIMARY_HEX; break;
        }
        MessageUtils.sendStat(sender, "Profile", MessageUtils.hex(profileColor) + profile);
        
        // Optimization stats
        MessageUtils.sendStat(sender, "Hibernated", 
            plugin.getHibernateManager().getHibernatedChunkCount() + " chunks");
        MessageUtils.sendStat(sender, "Cached", 
            plugin.getMemorySaver().getCachedChunkCount() + " chunks");
        
        MessageUtils.sendFooter(sender);
        
        return true;
    }
    
    private boolean executeBoost(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendError(sender, "You don't have permission to run optimizations.");
            return true;
        }
        
        MessageUtils.sendInfo(sender, "Triggering full optimization cycle...");
        
        // Run immediate optimizations
        plugin.getAutoClearTask().immediateClear();
        plugin.getMemorySaver().clearCache();
        
        // Suggest GC
        System.gc();
        
        MessageUtils.sendSuccess(sender, "Optimization cycle completed successfully!");
        return true;
    }
    
    private boolean executePregen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xreatopt.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to generate chunks.");
            return true;
        }
        
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /xreatopt pregen <world> <radius> <speed>");
            return true;
        }
        
        String worldName = args[1];
        try {
            int radius = Integer.parseInt(args[2]);
            int speed = Integer.parseInt(args[3]);
            
            if (radius < 1 || radius > 1000) {
                sender.sendMessage(ChatColor.RED + "Radius must be between 1 and 1000.");
                return true;
            }
            
            if (speed < 1 || speed > 1000) {
                sender.sendMessage(ChatColor.RED + "Speed must be between 1 and 1000.");
                return true;
            }
            
            sender.sendMessage(ChatColor.GREEN + "Starting pre-generation for world '" + worldName + 
                             "' with radius " + radius + " and speed " + speed + "...");
            
            // Find a player in the world to center the generation
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "World '" + worldName + "' not found.");
                return true;
            }
            
            // Use first player in world or spawn point
            org.bukkit.Location centerLoc = world.getSpawnLocation();
            for (org.bukkit.entity.Player player : world.getPlayers()) {
                centerLoc = player.getLocation();
                break;
            }
            
            // Start async pre-generation
            plugin.getChunkPreGenerator().pregenerateWorld(
                worldName, 
                centerLoc.getBlockX() >> 4, // Convert to chunk coordinates
                centerLoc.getBlockZ() >> 4, 
                radius, 
                speed
            ).thenRun(() -> {
                // Run completion message on main thread
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GREEN + "Chunk pre-generation completed for world '" + worldName + "'");
                });
            });
            
            sender.sendMessage(ChatColor.GREEN + "Chunk pre-generation started. This will run in the background.");
            
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number format. Use: /xreatopt pregen <world> <radius> <speed>");
        }
        
        return true;
    }
    
    private boolean executePurge(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to purge chunks/entities.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "Starting purge of unused chunks and entities...");
        
        // Clear cached chunks
        plugin.getMemorySaver().clearCache();
        
        // Run immediate entity clear
        int cleared = plugin.getAutoClearTask().immediateClear();
        
        sender.sendMessage(ChatColor.GREEN + "Purge completed. Cleared " + cleared + " excess entities.");
        return true;
    }
    
    private boolean executeReload(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to reload the configuration.");
            return true;
        }
        
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
        
        // Note: Branding messages remain unchanged (as per requirements)
        sender.sendMessage(ChatColor.YELLOW + "Note: Branding messages are hardcoded and cannot be modified.");
        
        return true;
    }
    
    private boolean executeReport(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to generate reports.");
            return true;
        }
        
        // Generate a manual report by creating a new one
        sender.sendMessage(ChatColor.GREEN + "Generating performance report...");
        
        // In a real implementation, this would trigger a report generation
        File reportsDir = new File(plugin.getDataFolder(), "reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }
        
        // Create a summary of current performance for the command output
        double tps = TPSUtils.getTPS();
        double memoryPercent = MemoryUtils.getMemoryUsagePercentage();
        int entityCount = plugin.getPerformanceMonitor().getCurrentEntityCount();
        
        sender.sendMessage(ChatColor.GOLD + "=== Performance Report ===");
        sender.sendMessage(ChatColor.AQUA + "Current TPS: " + ChatColor.WHITE + String.format("%.2f", tps));
        sender.sendMessage(ChatColor.AQUA + "Memory Usage: " + ChatColor.WHITE + String.format("%.1f", memoryPercent) + "%");
        sender.sendMessage(ChatColor.AQUA + "Entity Count: " + ChatColor.WHITE + entityCount);
        sender.sendMessage(ChatColor.AQUA + "Reports Directory: " + ChatColor.WHITE + reportsDir.getAbsolutePath());
        sender.sendMessage(ChatColor.GOLD + "========================");
        
        return true;
    }
    
    private boolean executeClearCache(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to clear caches.");
            return true;
        }
        
        int cacheSize = plugin.getMemorySaver().getCachedChunkCount();
        plugin.getMemorySaver().clearCache();
        
        sender.sendMessage(ChatColor.GREEN + "Cleared RAM cache. Removed " + cacheSize + " cached chunk entries.");
        return true;
    }
    
    private boolean executeDashboard(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendError(sender, "You don't have permission to view dashboard info.");
            return true;
        }
        
        boolean enabled = plugin.getConfig().getBoolean("web_dashboard.enabled", false);
        
        if (!enabled) {
            MessageUtils.sendWarning(sender, "Web dashboard is disabled in config.yml");
            MessageUtils.sendInfo(sender, "Enable it by setting web_dashboard.enabled: true");
            return true;
        }
        
        if (plugin.getWebDashboard() == null || !plugin.getWebDashboard().isRunning()) {
            MessageUtils.sendError(sender, "Web dashboard failed to start. Check console for errors.");
            return true;
        }
        
        int port = plugin.getConfig().getInt("web_dashboard.port", 8080);
        String bindAddress = plugin.getConfig().getString("web_dashboard.bind_address", "0.0.0.0");
        
        MessageUtils.sendHeader(sender, "Web Dashboard");
        MessageUtils.sendSuccess(sender, "Dashboard is running!");
        MessageUtils.sendStat(sender, "URL", "http://your-server-ip:" + port);
        MessageUtils.sendStat(sender, "Bind Address", bindAddress);
        MessageUtils.sendStat(sender, "Port", String.valueOf(port));
        MessageUtils.sendInfo(sender, "Access via browser to view real-time stats");
        MessageUtils.sendFooter(sender);
        
        return true;
    }
}