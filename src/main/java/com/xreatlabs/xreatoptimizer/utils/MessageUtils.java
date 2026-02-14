package com.xreatlabs.xreatoptimizer.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

import java.awt.Color;

public final class MessageUtils {

    private MessageUtils() {}

    public static final String PRIMARY_HEX = "#6366F1";
    public static final String SECONDARY_HEX = "#8B5CF6";
    public static final String ACCENT_HEX = "#EC4899";
    public static final String SUCCESS_HEX = "#10B981";
    public static final String WARNING_HEX = "#F59E0B";
    public static final String ERROR_HEX = "#EF4444";
    public static final String INFO_HEX = "#3B82F6";
    public static final String MUTED_HEX = "#6B7280";

    public static final String[] GRADIENT_PRIMARY = {"#6366F1", "#8B5CF6", "#A855F7"};
    public static final String[] GRADIENT_SUCCESS = {"#10B981", "#34D399", "#6EE7B7"};
    public static final String[] GRADIENT_WARNING = {"#F59E0B", "#FBBF24", "#FCD34D"};
    public static final String[] GRADIENT_ERROR = {"#EF4444", "#F87171", "#FCA5A5"};
    public static final String[] GRADIENT_INFO = {"#3B82F6", "#60A5FA", "#93C5FD"};
    public static final String[] GRADIENT_CYBER = {"#06B6D4", "#8B5CF6", "#EC4899"};
    public static final String[] GRADIENT_SUNSET = {"#F97316", "#EC4899", "#8B5CF6"};
    public static final String[] GRADIENT_OCEAN = {"#0EA5E9", "#6366F1", "#8B5CF6"};

    public static String getPrefix() {
        return ChatColor.GRAY + "[XreatOptimizer] " + ChatColor.RESET;
    }

    public static String getMinimalPrefix() {
        return ChatColor.GRAY + "- " + ChatColor.RESET;
    }

    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + ChatColor.GREEN + message);
    }

    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + ChatColor.RED + message);
    }

    public static void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + ChatColor.YELLOW + message);
    }

    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + ChatColor.AQUA + message);
    }

    public static void sendPrimary(CommandSender sender, String message) {
        sender.sendMessage(getPrefix() + ChatColor.WHITE + message);
    }

    public static void sendHeader(CommandSender sender, String title) {
        sender.sendMessage(ChatColor.GRAY + "------------------------------");
        sender.sendMessage(ChatColor.WHITE + title);
        sender.sendMessage(ChatColor.GRAY + "------------------------------");
    }

    public static void sendFooter(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "------------------------------");
    }

    public static void sendStat(CommandSender sender, String label, String value) {
        sender.sendMessage(ChatColor.GRAY + label + ": " + ChatColor.WHITE + value);
    }

    public static void sendStatWithStatus(CommandSender sender, String label, String value, Status status) {
        ChatColor color;
        switch (status) {
            case GOOD: color = ChatColor.GREEN; break;
            case WARNING: color = ChatColor.YELLOW; break;
            case CRITICAL: color = ChatColor.RED; break;
            case NEUTRAL: color = ChatColor.AQUA; break;
            default: color = ChatColor.GRAY; break;
        }
        sender.sendMessage(ChatColor.GRAY + label + ": " + color + value);
    }

    public static void sendProgressBar(CommandSender sender, String label, double percentage, int width) {
        int filled = (int) (width * (percentage / 100.0));
        int empty = width - filled;
        ChatColor barColor = percentage < 50 ? ChatColor.GREEN : (percentage < 80 ? ChatColor.YELLOW : ChatColor.RED);
        String bar = "|" + "#".repeat(Math.max(0, filled)) + "-".repeat(Math.max(0, empty)) + "|";
        sender.sendMessage(ChatColor.GRAY + label + ": " + barColor + bar + ChatColor.GRAY + " " + String.format("%.1f%%", percentage));
    }

    public static void sendCommandHelp(CommandSender sender, String command, String description) {
        sender.sendMessage(ChatColor.WHITE + command + ChatColor.GRAY + " - " + description);
    }
    
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
    
    private static Color hexToColor(String hex) {
        hex = hex.replace("#", "");
        return new Color(
            Integer.parseInt(hex.substring(0, 2), 16),
            Integer.parseInt(hex.substring(2, 4), 16),
            Integer.parseInt(hex.substring(4, 6), 16)
        );
    }
    
    public static String hex(String hexCode) {
        if (hexCode == null || hexCode.isEmpty()) return "";
        
        try {
            return ChatColor.of(hexCode).toString();
        } catch (Exception e) {
            return ChatColor.WHITE.toString();
        }
    }
    
    public static String centerText(String text, int lineWidth) {
        int padding = (lineWidth - stripColor(text).length()) / 2;
        if (padding <= 0) return text;
        return " ".repeat(padding) + text;
    }
    
    public static String stripColor(String text) {
        if (text == null) return "";
        text = text.replaceAll("§x(§[0-9a-fA-F]){6}", "");
        text = text.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
        return text;
    }
    
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }
    
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    public static String formatTPS(double tps) {
        String color;
        if (tps >= 19.5) color = SUCCESS_HEX;
        else if (tps >= 18.0) color = "#84CC16";
        else if (tps >= 15.0) color = WARNING_HEX;
        else if (tps >= 10.0) color = "#F97316";
        else color = ERROR_HEX;
        
        return hex(color) + String.format("%.2f", tps);
    }
    
    public static String formatMemory(double percentage) {
        String color;
        if (percentage < 50) color = SUCCESS_HEX;
        else if (percentage < 70) color = "#84CC16";
        else if (percentage < 85) color = WARNING_HEX;
        else color = ERROR_HEX;
        
        return hex(color) + String.format("%.1f%%", percentage);
    }
    
    /** Send boxed message */
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
    
    public enum Status {
        GOOD,
        WARNING,
        CRITICAL,
        NEUTRAL
    }
    
    public static Status getTpsStatus(double tps) {
        if (tps >= 19.0) return Status.GOOD;
        if (tps >= 15.0) return Status.WARNING;
        return Status.CRITICAL;
    }
    
    public static Status getMemoryStatus(double percentage) {
        if (percentage < 70) return Status.GOOD;
        if (percentage < 85) return Status.WARNING;
        return Status.CRITICAL;
    }
}
