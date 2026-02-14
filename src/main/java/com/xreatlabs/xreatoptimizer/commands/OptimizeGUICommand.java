package com.xreatlabs.xreatoptimizer.commands;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;

/** GUI command executor */
public class OptimizeGUICommand implements CommandExecutor {
    private final XreatOptimizer plugin;
    
    public OptimizeGUICommand(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("xreatopt.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the optimizer GUI.");
            return true;
        }
        
        // Create and open the optimizer GUI
        createAndOpenGUI(player);
        
        return true;
    }
    
    private void createAndOpenGUI(Player player) {
        // Create an inventory for the GUI
        Inventory gui = org.bukkit.Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "XreatOptimizer Control Panel");
        
        // Add items to the GUI
        ItemStack statsItem = createItem(Material.COMPASS, ChatColor.AQUA + "Server Statistics", 
            "View TPS, Memory, and Performance Metrics");
        gui.setItem(2, statsItem);
        
        ItemStack optimizeItem = createItem(Material.REDSTONE, ChatColor.YELLOW + "Run Optimizations", 
            "Trigger immediate optimization cycle");
        gui.setItem(3, optimizeItem);
        
        ItemStack hibernateItem = createItem(Material.BARRIER, ChatColor.GOLD + "Hibernate Manager", 
            "Manage entity hibernation settings");
        gui.setItem(4, hibernateItem);
        
        ItemStack profileItem = createItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Optimization Profile", 
            "Change current optimization profile");
        gui.setItem(5, profileItem);
        
        ItemStack configItem = createItem(Material.CRAFTING_TABLE, ChatColor.WHITE + "Configuration", 
            "Edit plugin configuration");
        gui.setItem(6, configItem);
        
        // Add more items as needed for other features
        ItemStack closeItem = createItem(Material.BARRIER, ChatColor.RED + "Close", "Close this menu");
        gui.setItem(22, closeItem);
        
        // Open the inventory for the player
        player.openInventory(gui);
    }
    
    private ItemStack createItem(Material material, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        
        java.util.List<String> loreList = new java.util.ArrayList<>();
        for (String line : lore) {
            loreList.add(ChatColor.GRAY + line);
        }
        meta.setLore(loreList);
        
        item.setItemMeta(meta);
        return item;
    }
}