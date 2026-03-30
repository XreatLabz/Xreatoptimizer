package com.xreatlabs.xreatoptimizer.config;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.ChatColor;

/** Dynamic configuration reloader */
public class ConfigReloader {

    private final XreatOptimizer plugin;

    public ConfigReloader(XreatOptimizer plugin) {
        this.plugin = plugin;
    }

    public void reloadConfiguration() {
        plugin.getLogger().info("Reloading configuration...");
        plugin.reloadConfig();

        if (plugin.getOptimizationManager() != null) {
            plugin.getOptimizationManager().reloadConfig();
        }

        if (plugin.getHibernateManager() != null) {
            boolean hibernateEnabled = plugin.getConfig().getBoolean("hibernate.enabled", false);
            int radius = plugin.getConfig().getInt("hibernate.radius", 64);
            plugin.getHibernateManager().setRadius(radius);
            plugin.getHibernateManager().setEnabled(hibernateEnabled);
        }

        if (plugin.getEmptyServerOptimizer() != null) {
            plugin.getEmptyServerOptimizer().reloadConfig();
        }

        if (plugin.getAutoClearTask() != null) {
            plugin.getAutoClearTask().reloadConfig();
        }

        if (plugin.getItemDropTracker() != null) {
            plugin.getItemDropTracker().reload();
        }

        if (plugin.getChunkPreGenerator() != null) {
            int maxThreads = plugin.getConfig().getInt("pregen.max_threads", 2);
            int defaultSpeed = plugin.getConfig().getInt("pregen.default_speed", 100);
            plugin.getChunkPreGenerator().setMaxThreads(maxThreads);
            plugin.getChunkPreGenerator().setDefaultSpeed(defaultSpeed);
        }

        if (plugin.getDynamicViewDistance() != null) {
            plugin.getDynamicViewDistance().reloadConfig();
        }

        if (plugin.getAdvancedEntityOptimizer() != null) {
            boolean stackFusion = plugin.getConfig().getBoolean("enable_stack_fusion", true);
            plugin.getAdvancedEntityOptimizer().setStackFusionEnabled(stackFusion);
        }

        if (plugin.getMemorySaver() != null) {
            plugin.getMemorySaver().reloadConfig();
        }

        if (plugin.getWorldConfig() != null) {
            plugin.getWorldConfig().reload();
        }

        if (plugin.getNetworkOptimizer() != null) {
            plugin.getNetworkOptimizer().reloadConfig();
        }

        if (plugin.getSmartTickDistributor() != null) {
            plugin.getSmartTickDistributor().reloadConfig();
        }

        if (plugin.getPredictiveChunkLoader() != null) {
            plugin.getPredictiveChunkLoader().stop();
            plugin.getPredictiveChunkLoader().start();
        }

        plugin.getLogger().info(ChatColor.GREEN + "Configuration reloaded successfully.");
        plugin.getLogger().info("Feature summary: item_removal=" + plugin.getConfig().getBoolean("item_removal.enabled", false)
            + ", predictive_loading=" + plugin.getConfig().getBoolean("predictive_loading.enabled", false)
            + ", dashboard=" + plugin.getConfig().getBoolean("web_dashboard.enabled", false)
            + ", hibernate=" + plugin.getConfig().getBoolean("hibernate.enabled", false));
    }

    public boolean validateConfig() {
        boolean valid = true;

        double light = plugin.getConfig().getDouble("optimization.tps_thresholds.light", 19.5);
        double normal = plugin.getConfig().getDouble("optimization.tps_thresholds.normal", 18.0);
        double aggressive = plugin.getConfig().getDouble("optimization.tps_thresholds.aggressive", 16.0);

        if (light <= normal || normal <= aggressive) {
            plugin.getLogger().warning("Invalid TPS thresholds: light > normal > aggressive required!");
            valid = false;
        }

        int passive = plugin.getConfig().getInt("optimization.entity_limits.passive", 200);
        int hostile = plugin.getConfig().getInt("optimization.entity_limits.hostile", 150);
        int item = plugin.getConfig().getInt("optimization.entity_limits.item", 1000);

        if (passive < 0 || hostile < 0 || item < 0) {
            plugin.getLogger().warning("Entity limits must be positive numbers!");
            valid = false;
        }

        int radius = plugin.getConfig().getInt("hibernate.radius", 64);
        if (radius < 16 || radius > 512) {
            plugin.getLogger().warning("Hibernate radius should be between 16 and 512 blocks!");
            valid = false;
        }

        int delay = plugin.getConfig().getInt("empty_server.delay_seconds", 30);
        if (delay < 5 || delay > 600) {
            plugin.getLogger().warning("Empty server delay should be between 5 and 600 seconds!");
            valid = false;
        }

        int minView = plugin.getConfig().getInt("dynamic_view_distance.min", 4);
        int maxView = plugin.getConfig().getInt("dynamic_view_distance.max", 12);
        if (minView < 2 || maxView < minView) {
            plugin.getLogger().warning("Dynamic view distance config is invalid: max must be >= min and min must be >= 2.");
            valid = false;
        }

        return valid;
    }

    public void resetToDefaults() {
        plugin.getLogger().info("Resetting configuration to defaults...");
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        reloadConfiguration();
    }
}
