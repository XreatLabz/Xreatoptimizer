package com.xreatlabs.xreatoptimizer.commands;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.managers.PerformanceMonitor;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Command executor for generating performance reports
 */
public class ReportCommand implements CommandExecutor {
    private final XreatOptimizer plugin;
    
    public ReportCommand(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xreatopt.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to generate reports.");
            return true;
        }
        
        if (args.length == 0) {
            // Generate current report
            return generateCurrentReport(sender);
        } else if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            // List available reports
            return listReports(sender);
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " [list]");
            return true;
        }
    }
    
    private boolean generateCurrentReport(CommandSender sender) {
        File reportsDir = new File(plugin.getDataFolder(), "reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }
        
        String timestamp = java.time.LocalDateTime.now().toString().replace(":", "-");
        File reportFile = new File(reportsDir, "manual_report_" + timestamp + ".txt");
        
        try {
            java.io.FileWriter writer = new java.io.FileWriter(reportFile);
            
            // Use StatisticsStorage for historical data if available
            if (plugin.getStatisticsStorage() != null) {
                String report = plugin.getStatisticsStorage().generateReport(1);
                writer.write(report);
            } else {
                // Fallback to current metrics
                PerformanceMonitor monitor = plugin.getPerformanceMonitor();
                writer.write("XreatOptimizer Manual Performance Report\n");
                writer.write("Generated at: " + java.time.LocalDateTime.now() + "\n");
                writer.write("========================================\n\n");
                writer.write("Current Metrics:\n");
                writer.write("- TPS: " + String.format("%.2f", monitor.getCurrentTPS()) + "\n");
                writer.write("- Memory Usage: " + String.format("%.1f", monitor.getCurrentMemoryPercentage()) + "%\n");
                writer.write("- Entities: " + monitor.getCurrentEntityCount() + "\n");
                writer.write("- Chunks: " + monitor.getCurrentChunkCount() + "\n");
                writer.write("- Players: " + org.bukkit.Bukkit.getOnlinePlayers().size() + "\n");
            }
            
            writer.close();
            
            sender.sendMessage(ChatColor.GREEN + "Report generated: " + reportFile.getName());
            sender.sendMessage(ChatColor.YELLOW + "File: " + reportFile.getAbsolutePath());
            
        } catch (Exception e) {
            LoggerUtils.error("Could not generate report", e);
            sender.sendMessage(ChatColor.RED + "Could not generate report. Check console for errors.");
        }
        
        return true;
    }
    
    private boolean listReports(CommandSender sender) {
        File reportsDir = new File(plugin.getDataFolder(), "reports");
        if (!reportsDir.exists()) {
            sender.sendMessage(ChatColor.YELLOW + "No reports directory found.");
            return true;
        }
        
        File[] reportFiles = reportsDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (reportFiles == null || reportFiles.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "No reports found.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "Available Performance Reports:");
        for (File file : reportFiles) {
            sender.sendMessage(ChatColor.WHITE + "- " + file.getName());
        }
        
        return true;
    }
}