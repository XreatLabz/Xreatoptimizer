package com.xreatlabs.xreatoptimizer.listeners;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.EntityUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles clicks in the XreatOptimizer GUI
 */
public class GUIClickListener implements Listener {

    private final XreatOptimizer plugin;

    public GUIClickListener(XreatOptimizer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Check if it's our GUI
        if (!title.contains("XreatOptimizer")) {
            return;
        }

        event.setCancelled(true); // Prevent taking items

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        String itemName = clickedItem.getItemMeta().getDisplayName();

        // Handle different button clicks
        if (itemName.contains("Server Statistics")) {
            openStatsGUI(player);
        } else if (itemName.contains("Run Optimizations")) {
            runOptimizations(player);
        } else if (itemName.contains("Hibernate Manager")) {
            openHibernateGUI(player);
        } else if (itemName.contains("Optimization Profile")) {
            openProfileGUI(player);
        } else if (itemName.contains("Configuration")) {
            player.sendMessage(ChatColor.GOLD + "Use /xreatopt reload to reload configuration from file.");
            player.closeInventory();
        } else if (itemName.contains("Close") || itemName.contains("Back")) {
            player.closeInventory();
        } else if (itemName.contains("LIGHT Profile")) {
            setProfile(player, "LIGHT");
        } else if (itemName.contains("NORMAL Profile")) {
            setProfile(player, "NORMAL");
        } else if (itemName.contains("AGGRESSIVE Profile")) {
            setProfile(player, "AGGRESSIVE");
        } else if (itemName.contains("AUTO Profile")) {
            setProfile(player, "AUTO");
        }
    }

    private void openStatsGUI(Player player) {
        Inventory statsGUI = org.bukkit.Bukkit.createInventory(null, 27,
            ChatColor.DARK_GREEN + "XreatOptimizer Statistics");

        // TPS Display
        double tps = TPSUtils.getTPS();
        Material tpsMaterial = tps >= 19.0 ? Material.EMERALD : (tps >= 17.0 ? Material.GOLD_INGOT : Material.REDSTONE);
        ItemStack tpsItem = createItem(tpsMaterial, ChatColor.GREEN + "Server TPS",
            "Current: " + String.format("%.2f", tps),
            tps >= 19.0 ? ChatColor.GREEN + "Excellent!" : (tps >= 17.0 ? ChatColor.YELLOW + "Good" : ChatColor.RED + "Poor"));
        statsGUI.setItem(10, tpsItem);

        // Memory Display
        double memoryPercent = MemoryUtils.getMemoryUsagePercent();
        Material memMaterial = memoryPercent < 70 ? Material.EMERALD : (memoryPercent < 85 ? Material.GOLD_INGOT : Material.REDSTONE);
        ItemStack memItem = createItem(memMaterial, ChatColor.AQUA + "Memory Usage",
            String.format("%.1f%%", memoryPercent) + " used",
            MemoryUtils.getUsedMemory() + "MB / " + MemoryUtils.getMaxMemory() + "MB");
        statsGUI.setItem(12, memItem);

        // Entity Count
        int entities = EntityUtils.getTotalEntityCount();
        ItemStack entityItem = createItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Total Entities",
            entities + " entities loaded",
            "Across all worlds");
        statsGUI.setItem(14, entityItem);

        // Optimization Profile
        String profile = plugin.getOptimizationManager() != null ?
            plugin.getOptimizationManager().getCurrentProfile().name() : "UNKNOWN";
        ItemStack profileItem = createItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Current Profile",
            profile,
            "Active optimization level");
        statsGUI.setItem(16, profileItem);

        // Back button
        ItemStack backItem = createItem(Material.ARROW, ChatColor.WHITE + "Back", "Return to main menu");
        statsGUI.setItem(22, backItem);

        player.openInventory(statsGUI);
    }

    private void runOptimizations(Player player) {
        player.sendMessage(ChatColor.GREEN + "Running full optimization cycle...");

        if (plugin.getOptimizationManager() != null) {
            plugin.getOptimizationManager().forceOptimizationCycle();
        }

        if (plugin.getAutoClearTask() != null) {
            plugin.getAutoClearTask().clearEntities();
        }

        if (plugin.getMemorySaver() != null) {
            plugin.getMemorySaver().clearCache();
        }

        player.sendMessage(ChatColor.GREEN + "Optimization complete!");
        player.closeInventory();
    }

    private void openHibernateGUI(Player player) {
        Inventory hibernateGUI = org.bukkit.Bukkit.createInventory(null, 27,
            ChatColor.DARK_GREEN + "XreatOptimizer Hibernate");

        boolean hibernateEnabled = plugin.getConfig().getBoolean("hibernate.enabled", true);
        int radius = plugin.getConfig().getInt("hibernate.radius", 64);

        ItemStack statusItem = createItem(
            hibernateEnabled ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
            ChatColor.GOLD + "Hibernate Status",
            "Currently: " + (hibernateEnabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"),
            "Radius: " + radius + " blocks"
        );
        hibernateGUI.setItem(13, statusItem);

        ItemStack backItem = createItem(Material.ARROW, ChatColor.WHITE + "Back", "Return to main menu");
        hibernateGUI.setItem(22, backItem);

        player.openInventory(hibernateGUI);
    }

    private void openProfileGUI(Player player) {
        Inventory profileGUI = org.bukkit.Bukkit.createInventory(null, 27,
            ChatColor.DARK_GREEN + "XreatOptimizer Profile");

        // AUTO Profile
        ItemStack autoItem = createItem(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "AUTO Profile",
            "Automatically adjusts based on server load",
            ChatColor.GREEN + "Click to activate");
        profileGUI.setItem(10, autoItem);

        // LIGHT Profile
        ItemStack lightItem = createItem(Material.FEATHER, ChatColor.WHITE + "LIGHT Profile",
            "Minimal optimizations",
            "Best for small servers",
            ChatColor.GREEN + "Click to activate");
        profileGUI.setItem(12, lightItem);

        // NORMAL Profile
        ItemStack normalItem = createItem(Material.IRON_INGOT, ChatColor.YELLOW + "NORMAL Profile",
            "Balanced optimizations",
            "Recommended for most servers",
            ChatColor.GREEN + "Click to activate");
        profileGUI.setItem(14, normalItem);

        // AGGRESSIVE Profile
        ItemStack aggressiveItem = createItem(Material.DIAMOND, ChatColor.RED + "AGGRESSIVE Profile",
            "Maximum optimizations",
            "For high-performance needs",
            ChatColor.GREEN + "Click to activate");
        profileGUI.setItem(16, aggressiveItem);

        // Back button
        ItemStack backItem = createItem(Material.ARROW, ChatColor.WHITE + "Back", "Return to main menu");
        profileGUI.setItem(22, backItem);

        player.openInventory(profileGUI);
    }

    private void setProfile(Player player, String profile) {
        try {
            if (plugin.getOptimizationManager() != null) {
                com.xreatlabs.xreatoptimizer.managers.OptimizationManager.OptimizationProfile p =
                    com.xreatlabs.xreatoptimizer.managers.OptimizationManager.OptimizationProfile.valueOf(profile);
                plugin.getOptimizationManager().setProfile(p);
                player.sendMessage(ChatColor.GREEN + "Profile changed to: " + ChatColor.GOLD + profile);
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to change profile: " + e.getMessage());
        }
        player.closeInventory();
    }

    private ItemStack createItem(Material material, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);

        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(ChatColor.GRAY + line);
        }
        meta.setLore(loreList);

        item.setItemMeta(meta);
        return item;
    }
}
