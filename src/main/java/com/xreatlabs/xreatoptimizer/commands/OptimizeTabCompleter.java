package com.xreatlabs.xreatoptimizer.commands;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer for XreatOptimizer commands
 * Provides intelligent autocomplete suggestions
 */
public class OptimizeTabCompleter implements TabCompleter {
    
    private final XreatOptimizer plugin;
    
    // Main subcommands
    private static final List<String> MAIN_COMMANDS = Arrays.asList(
        "stats", "boost", "pregen", "purge", "reload", "report", 
        "clearcache", "profile", "world", "help", "version", "notify", "dashboard"
    );
    
    // Admin-only commands
    private static final List<String> ADMIN_COMMANDS = Arrays.asList(
        "boost", "pregen", "purge", "reload", "clearcache", "profile", "world", "notify", "dashboard"
    );
    
    // Profile options
    private static final List<String> PROFILES = Arrays.asList(
        "AUTO", "LIGHT", "NORMAL", "AGGRESSIVE", "EMERGENCY"
    );
    
    // Pregen speed presets
    private static final List<String> PREGEN_SPEEDS = Arrays.asList(
        "50", "100", "200", "500"
    );
    
    // Pregen radius presets
    private static final List<String> PREGEN_RADII = Arrays.asList(
        "5", "10", "25", "50", "100"
    );
    
    // Notification subcommands
    private static final List<String> NOTIFY_COMMANDS = Arrays.asList(
        "test", "enable", "disable", "status"
    );
    
    public OptimizeTabCompleter(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument - main subcommands
            List<String> available = new ArrayList<>();
            
            // Add view commands if player has permission
            if (sender.hasPermission("xreatopt.view")) {
                available.add("stats");
                available.add("report");
                available.add("help");
                available.add("version");
            }
            
            // Add admin commands if player has permission
            if (sender.hasPermission("xreatopt.admin")) {
                available.addAll(ADMIN_COMMANDS);
            }
            
            StringUtil.copyPartialMatches(args[0], available, completions);
            
        } else if (args.length == 2) {
            // Second argument - depends on first
            switch (args[0].toLowerCase()) {
                case "pregen":
                    // World names for pregen
                    List<String> worldNames = Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[1], worldNames, completions);
                    break;
                    
                case "profile":
                    // Profile options
                    StringUtil.copyPartialMatches(args[1], PROFILES, completions);
                    break;
                    
                case "world":
                    // World names for world-specific settings
                    List<String> worlds = Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[1], worlds, completions);
                    break;
                    
                case "notify":
                    // Notification subcommands
                    StringUtil.copyPartialMatches(args[1], NOTIFY_COMMANDS, completions);
                    break;
                    
                case "report":
                    // Report time periods
                    StringUtil.copyPartialMatches(args[1], 
                        Arrays.asList("1h", "6h", "12h", "24h", "7d"), completions);
                    break;
            }
            
        } else if (args.length == 3) {
            // Third argument
            switch (args[0].toLowerCase()) {
                case "pregen":
                    // Radius suggestions
                    StringUtil.copyPartialMatches(args[2], PREGEN_RADII, completions);
                    break;
                    
                case "world":
                    // World setting options
                    StringUtil.copyPartialMatches(args[2], 
                        Arrays.asList("entity_limit", "hibernate", "view_distance"), completions);
                    break;
            }
            
        } else if (args.length == 4) {
            // Fourth argument
            switch (args[0].toLowerCase()) {
                case "pregen":
                    // Speed suggestions
                    StringUtil.copyPartialMatches(args[3], PREGEN_SPEEDS, completions);
                    break;
                    
                case "world":
                    // Values for world settings
                    if (args[2].equalsIgnoreCase("hibernate")) {
                        StringUtil.copyPartialMatches(args[3], 
                            Arrays.asList("true", "false"), completions);
                    } else if (args[2].equalsIgnoreCase("entity_limit")) {
                        StringUtil.copyPartialMatches(args[3], 
                            Arrays.asList("100", "200", "500", "1000"), completions);
                    } else if (args[2].equalsIgnoreCase("view_distance")) {
                        StringUtil.copyPartialMatches(args[3], 
                            Arrays.asList("4", "6", "8", "10", "12"), completions);
                    }
                    break;
            }
        }
        
        Collections.sort(completions);
        return completions;
    }
}
