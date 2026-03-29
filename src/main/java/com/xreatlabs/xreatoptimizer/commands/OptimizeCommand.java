package com.xreatlabs.xreatoptimizer.commands;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.OptimizationManager;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.MessageUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.File;

/** Main command executor */
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
                MessageUtils.sendError(sender, "Unknown command. Use /xreatopt help.");
                return true;
        }
    }

    private boolean showHelp(CommandSender sender) {
        MessageUtils.sendHeader(sender, "XreatOptimizer Commands");

        if (sender.hasPermission("xreatopt.view")) {
            MessageUtils.sendCommandHelp(sender, "/xreatopt stats", "Show current performance and active systems");
            MessageUtils.sendCommandHelp(sender, "/xreatopt report", "Show a quick performance summary");
            MessageUtils.sendCommandHelp(sender, "/xreatgui", "Open the control panel GUI");
        }

        if (sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendCommandHelp(sender, "/xreatopt boost", "Run a safe manual optimization pass");
            MessageUtils.sendCommandHelp(sender, "/xreatopt pregen <world> <radius> <speed>", "Pre-generate chunks around spawn or a player");
            MessageUtils.sendCommandHelp(sender, "/xreatopt purge", "Clear runtime caches and remove excess arrows if enabled");
            MessageUtils.sendCommandHelp(sender, "/xreatopt reload", "Reload config and refresh runtime systems");
            MessageUtils.sendCommandHelp(sender, "/xreatopt clearcache", "Clear cached chunk metadata");
            MessageUtils.sendCommandHelp(sender, "/xreatopt dashboard", "Show dashboard status and connection info");
        }

        MessageUtils.sendInfo(sender, "Tip: use the GUI for profile changes and quick checks.");

        MessageUtils.sendFooter(sender);
        return true;
    }

    private boolean executeStats(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.view")) {
            MessageUtils.sendError(sender, "You don't have permission to view statistics.");
            return true;
        }

        double tps = TPSUtils.getTPS();
        long usedMemory = MemoryUtils.getUsedMemoryMB();
        long maxMemory = MemoryUtils.getMaxMemoryMB();
        double memoryPercent = MemoryUtils.getMemoryUsagePercentage();
        int entityCount = plugin.getPerformanceMonitor().getCurrentEntityCount();
        int chunkCount = plugin.getPerformanceMonitor().getCurrentChunkCount();
        int playerCount = Bukkit.getOnlinePlayers().size();

        MessageUtils.sendHeader(sender, "Server Statistics");
        MessageUtils.sendStatWithStatus(sender, "TPS", MessageUtils.formatTPS(tps), MessageUtils.getTpsStatus(tps));
        MessageUtils.sendProgressBar(sender, "Memory", memoryPercent, 20);
        MessageUtils.sendStat(sender, "Memory Details", MessageUtils.formatNumber(usedMemory) + "MB / " + MessageUtils.formatNumber(maxMemory) + "MB");
        MessageUtils.sendStatWithStatus(sender, "Entities", MessageUtils.formatNumber(entityCount),
            entityCount < 5000 ? MessageUtils.Status.GOOD : entityCount < 10000 ? MessageUtils.Status.WARNING : MessageUtils.Status.CRITICAL);
        MessageUtils.sendStat(sender, "Loaded Chunks", MessageUtils.formatNumber(chunkCount));
        MessageUtils.sendStat(sender, "Players Online", String.valueOf(playerCount));

        OptimizationManager.OptimizationProfile currentProfile = plugin.getOptimizationManager().getCurrentProfile();
        OptimizationManager.OptimizationProfile effectiveProfile = plugin.getOptimizationManager().getEffectiveProfile();
        MessageUtils.sendStat(sender, "Profile", colorProfile(currentProfile.name()));
        if (currentProfile != effectiveProfile) {
            MessageUtils.sendStat(sender, "Effective Profile", colorProfile(effectiveProfile.name()));
        }

        MessageUtils.sendStat(sender, "Tracked Hibernation Chunks", String.valueOf(plugin.getHibernateManager().getHibernatedChunkCount()));
        MessageUtils.sendStat(sender, "Cached Chunks", String.valueOf(plugin.getMemorySaver().getCachedChunkCount()));
        MessageUtils.sendStat(sender, "Item Cleanup", plugin.getItemDropTracker().isEnabled() ? ChatColor.YELLOW + "Enabled" : ChatColor.GREEN + "Disabled");
        MessageUtils.sendStat(sender, "Predictive Loading", plugin.getConfig().getBoolean("predictive_loading.enabled", false) ? ChatColor.YELLOW + "Enabled" : ChatColor.GREEN + "Disabled");
        MessageUtils.sendStat(sender, "Low-Power Mode", plugin.getEmptyServerOptimizer().isInEmptyMode() ? ChatColor.YELLOW + "Active" : ChatColor.GREEN + "Standby");
        MessageUtils.sendFooter(sender);
        return true;
    }

    private boolean executeBoost(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendError(sender, "You don't have permission to run optimizations.");
            return true;
        }

        MessageUtils.sendInfo(sender, "Running a safe manual optimization pass...");
        int cleared = plugin.getAutoClearTask().immediateClear();
        plugin.getMemorySaver().clearCache();
        if (plugin.getOptimizationManager() != null) {
            plugin.getOptimizationManager().forceOptimizationCycle();
        }

        MessageUtils.sendSuccess(sender, "Optimization pass finished.");
        MessageUtils.sendStat(sender, "Arrows Cleared", String.valueOf(cleared));
        MessageUtils.sendStat(sender, "Cache State", "Chunk cache refreshed");
        return true;
    }

    private boolean executePregen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendError(sender, "You don't have permission to generate chunks.");
            return true;
        }

        if (args.length < 4) {
            MessageUtils.sendWarning(sender, "Usage: /xreatopt pregen <world> <radius> <speed>");
            return true;
        }

        String worldName = args[1];
        try {
            int radius = Integer.parseInt(args[2]);
            int speed = Integer.parseInt(args[3]);

            if (radius < 1 || radius > 1000) {
                MessageUtils.sendWarning(sender, "Radius must be between 1 and 1000.");
                return true;
            }

            if (speed < 1 || speed > 1000) {
                MessageUtils.sendWarning(sender, "Speed must be between 1 and 1000.");
                return true;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                MessageUtils.sendError(sender, "World '" + worldName + "' was not found.");
                return true;
            }

            Location centerLoc = world.getSpawnLocation();
            for (org.bukkit.entity.Player player : world.getPlayers()) {
                centerLoc = player.getLocation();
                break;
            }

            MessageUtils.sendInfo(sender, "Starting pre-generation for '" + worldName + "'...");
            MessageUtils.sendStat(sender, "Center Chunk", (centerLoc.getBlockX() >> 4) + ", " + (centerLoc.getBlockZ() >> 4));
            MessageUtils.sendStat(sender, "Radius", String.valueOf(radius));
            MessageUtils.sendStat(sender, "Speed", speed + " chunks/sec target");

            plugin.getChunkPreGenerator().pregenerateWorld(
                worldName,
                centerLoc.getBlockX() >> 4,
                centerLoc.getBlockZ() >> 4,
                radius,
                speed
            ).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                MessageUtils.sendSuccess(sender, "Chunk pre-generation completed for '" + worldName + "'.")
            )).exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    MessageUtils.sendError(sender, "Chunk pre-generation failed: " + ex.getMessage())
                );
                return null;
            });

            MessageUtils.sendInfo(sender, "Chunk pre-generation is now running in controlled batches.");
        } catch (NumberFormatException e) {
            MessageUtils.sendError(sender, "Radius and speed must both be numbers.");
        }

        return true;
    }

    private boolean executePurge(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendError(sender, "You don't have permission to purge runtime state.");
            return true;
        }

        MessageUtils.sendInfo(sender, "Refreshing runtime caches and cleanup systems...");
        plugin.getMemorySaver().clearCache();
        int cleared = plugin.getAutoClearTask().immediateClear();

        MessageUtils.sendSuccess(sender, "Purge pass complete.");
        MessageUtils.sendStat(sender, "Arrows Cleared", String.valueOf(cleared));
        MessageUtils.sendStat(sender, "Chunk Cache", "Cleared");
        return true;
    }

    private boolean executeReload(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendError(sender, "You don't have permission to reload the configuration.");
            return true;
        }

        if (plugin.getConfigReloader() != null) {
            plugin.getConfigReloader().reloadConfiguration();
        } else {
            plugin.reloadConfig();
        }

        MessageUtils.sendSuccess(sender, "Configuration reloaded.");
        MessageUtils.sendStat(sender, "Item Cleanup", plugin.getItemDropTracker().isEnabled() ? ChatColor.YELLOW + "Enabled" : ChatColor.GREEN + "Disabled");
        MessageUtils.sendStat(sender, "Predictive Loading", plugin.getConfig().getBoolean("predictive_loading.enabled", false) ? ChatColor.YELLOW + "Enabled" : ChatColor.GREEN + "Disabled");
        MessageUtils.sendStat(sender, "Dashboard", plugin.getConfig().getBoolean("web_dashboard.enabled", false) ? ChatColor.YELLOW + "Enabled" : ChatColor.GREEN + "Disabled");
        return true;
    }

    private boolean executeReport(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendError(sender, "You don't have permission to generate reports.");
            return true;
        }

        File reportsDir = new File(plugin.getDataFolder(), "reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        double tps = TPSUtils.getTPS();
        double memoryPercent = MemoryUtils.getMemoryUsagePercentage();
        int entityCount = plugin.getPerformanceMonitor().getCurrentEntityCount();
        int chunkCount = plugin.getPerformanceMonitor().getCurrentChunkCount();

        MessageUtils.sendHeader(sender, "Performance Snapshot");
        MessageUtils.sendStatWithStatus(sender, "TPS", String.format("%.2f", tps), MessageUtils.getTpsStatus(tps));
        MessageUtils.sendStatWithStatus(sender, "Memory", String.format("%.1f%%", memoryPercent), MessageUtils.getMemoryStatus(memoryPercent));
        MessageUtils.sendStat(sender, "Entities", String.valueOf(entityCount));
        MessageUtils.sendStat(sender, "Chunks", String.valueOf(chunkCount));
        MessageUtils.sendStat(sender, "Reports Folder", reportsDir.getAbsolutePath());
        MessageUtils.sendFooter(sender);
        return true;
    }

    private boolean executeClearCache(CommandSender sender) {
        if (!sender.hasPermission("xreatopt.admin")) {
            MessageUtils.sendError(sender, "You don't have permission to clear caches.");
            return true;
        }

        int cacheSize = plugin.getMemorySaver().getCachedChunkCount();
        plugin.getMemorySaver().clearCache();

        MessageUtils.sendSuccess(sender, "Chunk cache cleared.");
        MessageUtils.sendStat(sender, "Removed Entries", String.valueOf(cacheSize));
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
            MessageUtils.sendInfo(sender, "Enable web_dashboard.enabled if you want remote stats.");
            return true;
        }

        if (plugin.getWebDashboard() == null || !plugin.getWebDashboard().isRunning()) {
            MessageUtils.sendError(sender, "Web dashboard is enabled in config but not currently running.");
            return true;
        }

        int port = plugin.getConfig().getInt("web_dashboard.port", 8080);
        String bindAddress = plugin.getConfig().getString("web_dashboard.bind_address", "0.0.0.0");
        String authToken = plugin.getConfig().getString("web_dashboard.auth_token", "");

        MessageUtils.sendHeader(sender, "Web Dashboard");
        MessageUtils.sendSuccess(sender, "Dashboard is running.");
        MessageUtils.sendStat(sender, "Address", bindAddress + ":" + port);
        MessageUtils.sendStat(sender, "Suggested URL", dashboardUrlFor(sender, bindAddress, port));
        MessageUtils.sendStat(sender, "Authentication", authToken.isEmpty() ? ChatColor.RED + "Disabled" : ChatColor.GREEN + "Enabled");
        MessageUtils.sendFooter(sender);
        return true;
    }

    private String dashboardUrlFor(CommandSender sender, String bindAddress, int port) {
        if (sender instanceof org.bukkit.entity.Player) {
            String host = ((org.bukkit.entity.Player) sender).getAddress() != null
                ? ((org.bukkit.entity.Player) sender).getAddress().getAddress().getHostAddress()
                : null;
            if (host != null && !host.isEmpty()) {
                return "http://" + host + ":" + port;
            }
        }

        if (sender instanceof ConsoleCommandSender) {
            return "http://127.0.0.1:" + port;
        }

        if ("0.0.0.0".equals(bindAddress)) {
            return "http://your-server-ip:" + port;
        }

        return "http://" + bindAddress + ":" + port;
    }

    private String colorProfile(String profile) {
        switch (profile) {
            case "LIGHT":
                return MessageUtils.hex(MessageUtils.SUCCESS_HEX) + profile;
            case "NORMAL":
                return MessageUtils.hex(MessageUtils.INFO_HEX) + profile;
            case "AGGRESSIVE":
                return MessageUtils.hex(MessageUtils.WARNING_HEX) + profile;
            case "EMERGENCY":
                return MessageUtils.hex(MessageUtils.ERROR_HEX) + profile;
            default:
                return MessageUtils.hex(MessageUtils.PRIMARY_HEX) + profile;
        }
    }
}
