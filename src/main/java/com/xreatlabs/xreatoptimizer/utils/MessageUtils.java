package com.xreatlabs.xreatoptimizer.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Professional aesthetic message utilities with gradient color support
 * Provides beautiful, modern-looking messages for the plugin
 */
public final class MessageUtils {
    
    private MessageUtils() {}
    
    // Brand colors - Modern gradient palette
    public static final String PRIMARY_HEX = "#6366F1";      // Indigo
    public static final String SECONDARY_HEX = "#8B5CF6";    // Purple
    public static final String ACCENT_HEX = "#EC4899";       // Pink
    public static final String SUCCESS_HEX = "#10B981";      // Emerald
    public static final String WARNING_HEX = "#F59E0B";      // Amber
    public static final String ERROR_HEX = "#EF4444";        // Red
    public static final String INFO_HEX = "#3B82F6";         // Blue
    public static final String MUTED_HEX = "#6B7280";        // Gray
    
    // Gradient presets
    public static final String[] GRADIENT_PRIMARY = {"#6366F1", "#8B5CF6", "#A855F7"};
    public static final String[] GRADIENT_SUCCESS = {"#10B981", "#34D399", "#6EE7B7"};
    public static final String[] GRADIENT_WARNING = {"#F59E0B", "#FBBF24", "#FCD34D"};
    public static final String[] GRADIENT_ERROR = {"#EF4444", "#F87171", "#FCA5A5"};
    public static final String[] GRADIENT_INFO = {"#3B82F6", "#60A5FA", "#93C5FD"};
    public static final String[] GRADIENT_CYBER = {"#06B6D4", "#8B5CF6", "#EC4899"};
    public static final String[] GRADIENT_SUNSET = {"#F97316", "#EC4899", "#8B5CF6"};
    public static final String[] GRADIENT_OCEAN = {"#0EA5E9", "#6366F1", "#8B5CF6"};
    
    // Plugin prefix with gradient
    private static final String PREFIX_RAW = "XreatOptimizer";
    
    /**
     * Get the plugin prefix with beautiful gradient
     */
    public static String getPrefix() {
        return applyGradient(PREFIX_RAW, GRADIENT_CYBER) + " " + hex(MUTED_HEX) + "»" + ChatColor.RESET + " ";
    }
    
    /**
     * Get a minimal prefix for less intrusive messages
     */
    public static String getMinimalPrefix() {
        return hex("#8B5CF6") + "✦ " + ChatColor.RESET;
    }
    
    /**
     * Send a success message with gradient styling
     */
    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + applyGradient(message, GRADIENT_SUCCESS));
    }
    
    /**
     * Send an error message with gradient styling
     */
    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + applyGradient(message, GRADIENT_ERROR));
    }
    
    /**
     * Send a warning message with gradient styling
     */
    public static void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + applyGradient(message, GRADIENT_WARNING));
    }
    
    /**
     * Send an info message with gradient styling
     */
    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + applyGradient(message, GRADIENT_INFO));
    }
    
    /**
     * Send a primary styled message
     */
    public static void sendPrimary(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + applyGradient(message, GRADIENT_PRIMARY));
    }
    
    /**
     * Send a header/title with beautiful styling
     */
    public static void sendHeader(CommandSender sender, String title) {
        String line = "━".repeat(20);
        sender.sendMessage("");
        sender.sendMessage(applyGradient(line, GRADIENT_CYBER));
        sender.sendMessage(centerText(applyGradient("  " + title + "  ", GRADIENT_CYBER), 50));
        sender.sendMessage(applyGradient(line, GRADIENT_CYBER));
    }
    
    /**
     * Send a footer line
     */
    public static void sendFooter(CommandSender sender) {
        String line = "━".repeat(40);
        sender.sendMessage(applyGradient(line, GRADIENT_CYBER));
        sender.sendMessage("");
    }
    
    /**
     * Send a stats line with label and value
     */
    public static void sendStat(CommandSender sender, String label, String value) {
        sender.sendMessage(
            hex(MUTED_HEX) + "  ◆ " + 
            hex("#A5B4FC") + label + hex(MUTED_HEX) + ": " + 
            hex("#FFFFFF") + value
        );
    }
    
    /**
     * Send a stats line with label, value, and status indicator
     */
    public static void sendStatWithStatus(CommandSender sender, String label, String value, Status status) {
        String statusColor;
        switch (status) {
            case GOOD: statusColor = SUCCESS_HEX; break;
            case WARNING: statusColor = WARNING_HEX; break;
            case CRITICAL: statusColor = ERROR_HEX; break;
            case NEUTRAL: statusColor = INFO_HEX; break;
            default: statusColor = MUTED_HEX; break;
        }
        String statusIcon;
        switch (status) {
            case GOOD: statusIcon = "●"; break;
            case WARNING: statusIcon = "●"; break;
            case CRITICAL: statusIcon = "●"; break;
            case NEUTRAL: statusIcon = "○"; break;
            default: statusIcon = "○"; break;
        }
        
        sender.sendMessage(
            hex(MUTED_HEX) + "  " + hex(statusColor) + statusIcon + " " +
            hex("#A5B4FC") + label + hex(MUTED_HEX) + ": " + 
            hex(statusColor) + value
        );
    }
    
    /**
     * Send a progress bar
     */
    public static void sendProgressBar(CommandSender sender, String label, double percentage, int width) {
        int filled = (int) (width * (percentage / 100.0));
        int empty = width - filled;
        
        String barColor = percentage < 50 ? SUCCESS_HEX : (percentage < 80 ? WARNING_HEX : ERROR_HEX);
        
        StringBuilder bar = new StringBuilder();
        bar.append(hex(barColor));
        bar.append("█".repeat(Math.max(0, filled)));
        bar.append(hex(MUTED_HEX));
        bar.append("░".repeat(Math.max(0, empty)));
        
        sender.sendMessage(
            hex(MUTED_HEX) + "  ◆ " +
            hex("#A5B4FC") + label + hex(MUTED_HEX) + ": " +
            bar.toString() + " " +
            hex(barColor) + String.format("%.1f%%", percentage)
        );
    }
    
    /**
     * Send a command help entry
     */
    public static void sendCommandHelp(CommandSender sender, String command, String description) {
        sender.sendMessage(
            hex(MUTED_HEX) + "  " +
            applyGradient(command, GRADIENT_PRIMARY) + 
            hex(MUTED_HEX) + " - " +
            hex("#D1D5DB") + description
        );
    }
    
    /**
     * Apply a gradient to text using hex colors
     */
    public static String applyGradient(String text, String[] hexColors) {
        if (text == null || text.isEmpty()) return "";
        if (hexColors == null || hexColors.length == 0) return text;
        
        StringBuilder result = new StringBuilder();
        int length = text.length();
        
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                result.append(c);
                continue;
            }
            
            float ratio = (float) i / (float) Math.max(1, length - 1);
            String color = interpolateColors(hexColors, ratio);
            result.append(hex(color)).append(c);
        }
        
        return result.toString();
    }
    
    /**
     * Interpolate between multiple colors based on ratio
     */
    private static String interpolateColors(String[] hexColors, float ratio) {
        if (hexColors.length == 1) return hexColors[0];
        
        float segment = 1.0f / (hexColors.length - 1);
        int index = (int) (ratio / segment);
        if (index >= hexColors.length - 1) index = hexColors.length - 2;
        
        float localRatio = (ratio - (index * segment)) / segment;
        
        Color c1 = hexToColor(hexColors[index]);
        Color c2 = hexToColor(hexColors[index + 1]);
        
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * localRatio);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * localRatio);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * localRatio);
        
        return String.format("#%02X%02X%02X", r, g, b);
    }
    
    /**
     * Convert hex string to Color
     */
    private static Color hexToColor(String hex) {
        hex = hex.replace("#", "");
        return new Color(
            Integer.parseInt(hex.substring(0, 2), 16),
            Integer.parseInt(hex.substring(2, 4), 16),
            Integer.parseInt(hex.substring(4, 6), 16)
        );
    }
    
    /**
     * Create a hex color code for Minecraft
     */
    public static String hex(String hexCode) {
        if (hexCode == null || hexCode.isEmpty()) return "";
        
        try {
            return ChatColor.of(hexCode).toString();
        } catch (Exception e) {
            // Fallback for older versions
            return ChatColor.WHITE.toString();
        }
    }
    
    /**
     * Center text for chat display
     */
    public static String centerText(String text, int lineWidth) {
        int padding = (lineWidth - stripColor(text).length()) / 2;
        if (padding <= 0) return text;
        return " ".repeat(padding) + text;
    }
    
    /**
     * Strip color codes from text
     */
    public static String stripColor(String text) {
        if (text == null) return "";
        // Remove hex color codes
        text = text.replaceAll("§x(§[0-9a-fA-F]){6}", "");
        // Remove standard color codes
        text = text.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
        return text;
    }
    
    /**
     * Format a number with commas
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }
    
    /**
     * Format bytes to human readable
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    /**
     * Format TPS with color based on value
     */
    public static String formatTPS(double tps) {
        String color;
        if (tps >= 19.5) color = SUCCESS_HEX;
        else if (tps >= 18.0) color = "#84CC16"; // Lime
        else if (tps >= 15.0) color = WARNING_HEX;
        else if (tps >= 10.0) color = "#F97316"; // Orange
        else color = ERROR_HEX;
        
        return hex(color) + String.format("%.2f", tps);
    }
    
    /**
     * Format memory percentage with color
     */
    public static String formatMemory(double percentage) {
        String color;
        if (percentage < 50) color = SUCCESS_HEX;
        else if (percentage < 70) color = "#84CC16";
        else if (percentage < 85) color = WARNING_HEX;
        else color = ERROR_HEX;
        
        return hex(color) + String.format("%.1f%%", percentage);
    }
    
    /**
     * Create a beautiful box around text
     */
    public static void sendBox(CommandSender sender, String title, String... lines) {
        sender.sendMessage("");
        sender.sendMessage(applyGradient("╔══════════════════════════════════════╗", GRADIENT_CYBER));
        sender.sendMessage(hex(PRIMARY_HEX) + "║ " + applyGradient(title, GRADIENT_CYBER) + 
                          " ".repeat(Math.max(0, 36 - stripColor(title).length())) + hex(PRIMARY_HEX) + " ║");
        sender.sendMessage(applyGradient("╠══════════════════════════════════════╣", GRADIENT_CYBER));
        
        for (String line : lines) {
            int padding = 38 - stripColor(line).length();
            sender.sendMessage(hex(PRIMARY_HEX) + "║ " + line + " ".repeat(Math.max(0, padding)) + hex(PRIMARY_HEX) + " ║");
        }
        
        sender.sendMessage(applyGradient("╚══════════════════════════════════════╝", GRADIENT_CYBER));
        sender.sendMessage("");
    }
    
    /**
     * Status enum for colored indicators
     */
    public enum Status {
        GOOD,
        WARNING,
        CRITICAL,
        NEUTRAL
    }
    
    /**
     * Get status based on TPS
     */
    public static Status getTpsStatus(double tps) {
        if (tps >= 19.0) return Status.GOOD;
        if (tps >= 15.0) return Status.WARNING;
        return Status.CRITICAL;
    }
    
    /**
     * Get status based on memory percentage
     */
    public static Status getMemoryStatus(double percentage) {
        if (percentage < 70) return Status.GOOD;
        if (percentage < 85) return Status.WARNING;
        return Status.CRITICAL;
    }
}
