package com.xreatlabs.xreatoptimizer.hooks;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for XreatOptimizer
 * Provides placeholders for server performance metrics
 * 
 * Available placeholders:
 * - %xreatopt_tps% - Current TPS
 * - %xreatopt_tps_color% - TPS with color coding
 * - %xreatopt_memory% - Memory usage percentage
 * - %xreatopt_memory_used% - Used memory in MB
 * - %xreatopt_memory_max% - Max memory in MB
 * - %xreatopt_entities% - Total entity count
 * - %xreatopt_chunks% - Loaded chunk count
 * - %xreatopt_profile% - Current optimization profile
 * - %xreatopt_players% - Online player count
 * - %xreatopt_hibernated_chunks% - Hibernated chunk count
 * - %xreatopt_hibernated_entities% - Hibernated entity count
 * - %xreatopt_lag_spikes% - Number of lag spikes detected
 * - %xreatopt_lag_score% - Lag severity score (0-100)
 * - %xreatopt_status% - Overall server status (Good/Warning/Critical)
 * - %xreatopt_predicted_tps% - Predicted TPS (30s ahead)
 * - %xreatopt_anomaly_detected% - Whether anomaly is detected
 */
public class XreatPlaceholderExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
    
    private final XreatOptimizer plugin;
    
    public XreatPlaceholderExpansion(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "xreatopt";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return "XreatLabs";
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true; // Don't unload when PlaceholderAPI reloads
    }
    
    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // TPS placeholders
        if (params.equalsIgnoreCase("tps")) {
            return String.format("%.2f", TPSUtils.getTPS());
        }
        
        if (params.equalsIgnoreCase("tps_color")) {
            double tps = TPSUtils.getTPS();
            String color;
            if (tps >= 19.0) color = "&a";
            else if (tps >= 17.0) color = "&e";
            else if (tps >= 14.0) color = "&6";
            else color = "&c";
            return color + String.format("%.2f", tps);
        }
        
        if (params.equalsIgnoreCase("tps_1m")) {
            return String.format("%.2f", TPSUtils.getTPS()); // 1-minute average
        }
        
        if (params.equalsIgnoreCase("tps_5m")) {
            return String.format("%.2f", TPSUtils.getTPS()); // 5-minute average (simplified)
        }
        
        // Memory placeholders
        if (params.equalsIgnoreCase("memory")) {
            return String.format("%.1f", MemoryUtils.getMemoryUsagePercentage());
        }
        
        if (params.equalsIgnoreCase("memory_color")) {
            double mem = MemoryUtils.getMemoryUsagePercentage();
            String color;
            if (mem < 60) color = "&a";
            else if (mem < 80) color = "&e";
            else color = "&c";
            return color + String.format("%.1f%%", mem);
        }
        
        if (params.equalsIgnoreCase("memory_used")) {
            return String.valueOf(MemoryUtils.getUsedMemoryMB());
        }
        
        if (params.equalsIgnoreCase("memory_max")) {
            return String.valueOf(MemoryUtils.getMaxMemoryMB());
        }
        
        if (params.equalsIgnoreCase("memory_free")) {
            return String.valueOf(MemoryUtils.getMaxMemoryMB() - MemoryUtils.getUsedMemoryMB());
        }
        
        if (params.equalsIgnoreCase("memory_bar")) {
            double mem = MemoryUtils.getMemoryUsagePercentage();
            int filled = (int) (mem / 10);
            int empty = 10 - filled;
            String color = mem < 60 ? "&a" : (mem < 80 ? "&e" : "&c");
            return color + "█".repeat(filled) + "&7" + "░".repeat(empty);
        }
        
        // Entity placeholders
        if (params.equalsIgnoreCase("entities")) {
            return String.valueOf(plugin.getPerformanceMonitor().getCurrentEntityCount());
        }
        
        if (params.equalsIgnoreCase("entities_formatted")) {
            return String.format("%,d", plugin.getPerformanceMonitor().getCurrentEntityCount());
        }
        
        // Chunk placeholders
        if (params.equalsIgnoreCase("chunks")) {
            return String.valueOf(plugin.getPerformanceMonitor().getCurrentChunkCount());
        }
        
        if (params.equalsIgnoreCase("chunks_formatted")) {
            return String.format("%,d", plugin.getPerformanceMonitor().getCurrentChunkCount());
        }
        
        // Profile placeholder
        if (params.equalsIgnoreCase("profile")) {
            return plugin.getOptimizationManager().getCurrentProfile().name();
        }
        
        if (params.equalsIgnoreCase("profile_color")) {
            String profile = plugin.getOptimizationManager().getCurrentProfile().name();
            String color;
            switch (profile) {
                case "LIGHT": color = "&a"; break;
                case "NORMAL": color = "&e"; break;
                case "AGGRESSIVE": color = "&6"; break;
                case "EMERGENCY": color = "&c"; break;
                default: color = "&b"; break;
            }
            return color + profile;
        }
        
        // Player count
        if (params.equalsIgnoreCase("players")) {
            return String.valueOf(org.bukkit.Bukkit.getOnlinePlayers().size());
        }
        
        // Hibernation stats
        if (params.equalsIgnoreCase("hibernated_chunks")) {
            return String.valueOf(plugin.getHibernateManager().getHibernatedChunkCount());
        }
        
        if (params.equalsIgnoreCase("hibernated_entities")) {
            return String.valueOf(plugin.getHibernateManager().getHibernatedEntityCount());
        }
        
        // Lag spike stats
        if (params.equalsIgnoreCase("lag_spikes")) {
            if (plugin.getLagSpikeDetector() != null) {
                return String.valueOf(plugin.getLagSpikeDetector().getStats().get("total_spikes"));
            }
            return "0";
        }
        
        if (params.equalsIgnoreCase("in_lag_spike")) {
            if (plugin.getLagSpikeDetector() != null) {
                return plugin.getLagSpikeDetector().isInLagSpike() ? "&cYes" : "&aNo";
            }
            return "&aNo";
        }
        
        // Overall status
        if (params.equalsIgnoreCase("status")) {
            double tps = TPSUtils.getTPS();
            double mem = MemoryUtils.getMemoryUsagePercentage();
            
            if (tps >= 19.0 && mem < 70) return "&a● Excellent";
            if (tps >= 17.0 && mem < 80) return "&e● Good";
            if (tps >= 14.0 && mem < 90) return "&6● Warning";
            return "&c● Critical";
        }
        
        if (params.equalsIgnoreCase("status_icon")) {
            double tps = TPSUtils.getTPS();
            double mem = MemoryUtils.getMemoryUsagePercentage();
            
            if (tps >= 19.0 && mem < 70) return "&a●";
            if (tps >= 17.0 && mem < 80) return "&e●";
            if (tps >= 14.0 && mem < 90) return "&6●";
            return "&c●";
        }
        
        // Tick time
        if (params.equalsIgnoreCase("mspt")) {
            return String.format("%.2f", TPSUtils.getAverageTickTime());
        }
        
        if (params.equalsIgnoreCase("mspt_color")) {
            double mspt = TPSUtils.getAverageTickTime();
            String color;
            if (mspt <= 40) color = "&a";
            else if (mspt <= 50) color = "&e";
            else color = "&c";
            return color + String.format("%.2fms", mspt);
        }

        // Lag score (0-100, higher = worse)
        if (params.equalsIgnoreCase("lag_score")) {
            double tps = TPSUtils.getTPS();
            double mem = MemoryUtils.getMemoryUsagePercentage();

            // TPS component: 0-50 (worse TPS = higher score)
            double tpsScore = Math.max(0, (20.0 - tps) / 20.0 * 50.0);

            // Memory component: 0-50 (higher memory = higher score)
            double memScore = mem / 100.0 * 50.0;

            int lagScore = (int) Math.min(100, tpsScore + memScore);
            return String.valueOf(lagScore);
        }

        if (params.equalsIgnoreCase("lag_score_color")) {
            double tps = TPSUtils.getTPS();
            double mem = MemoryUtils.getMemoryUsagePercentage();
            double tpsScore = Math.max(0, (20.0 - tps) / 20.0 * 50.0);
            double memScore = mem / 100.0 * 50.0;
            int lagScore = (int) Math.min(100, tpsScore + memScore);

            String color;
            if (lagScore < 20) color = "&a";
            else if (lagScore < 40) color = "&e";
            else if (lagScore < 60) color = "&6";
            else color = "&c";

            return color + lagScore;
        }

        // Predicted TPS (from PredictiveEngine)
        if (params.equalsIgnoreCase("predicted_tps")) {
            if (plugin.getPredictiveEngine() != null && plugin.getPredictiveEngine().isRunning()) {
                try {
                    com.xreatlabs.xreatoptimizer.ai.PredictiveEngine.Prediction prediction =
                        plugin.getPredictiveEngine().predictFuture(30);
                    return String.format("%.2f", prediction.predictedTps);
                } catch (Exception e) {
                    return "N/A";
                }
            }
            return "N/A";
        }

        if (params.equalsIgnoreCase("predicted_tps_color")) {
            if (plugin.getPredictiveEngine() != null && plugin.getPredictiveEngine().isRunning()) {
                try {
                    com.xreatlabs.xreatoptimizer.ai.PredictiveEngine.Prediction prediction =
                        plugin.getPredictiveEngine().predictFuture(30);
                    double tps = prediction.predictedTps;
                    String color;
                    if (tps >= 19.0) color = "&a";
                    else if (tps >= 17.0) color = "&e";
                    else if (tps >= 14.0) color = "&6";
                    else color = "&c";
                    return color + String.format("%.2f", tps);
                } catch (Exception e) {
                    return "&7N/A";
                }
            }
            return "&7N/A";
        }

        // Anomaly detection status
        if (params.equalsIgnoreCase("anomaly_detected")) {
            if (plugin.getAnomalyDetector() != null && plugin.getAnomalyDetector().isRunning()) {
                // Check if current conditions indicate anomaly
                double tps = TPSUtils.getTPS();
                double mem = MemoryUtils.getMemoryUsagePercentage();

                boolean anomaly = tps < 15.0 || mem > 85.0;
                return anomaly ? "&cYes" : "&aNo";
            }
            return "&7N/A";
        }

        if (params.equalsIgnoreCase("prediction_confidence")) {
            if (plugin.getPredictiveEngine() != null && plugin.getPredictiveEngine().isRunning()) {
                try {
                    com.xreatlabs.xreatoptimizer.ai.PredictiveEngine.Prediction prediction =
                        plugin.getPredictiveEngine().predictFuture(30);
                    return String.format("%.0f%%", prediction.confidence * 100);
                } catch (Exception e) {
                    return "N/A";
                }
            }
            return "N/A";
        }

        return null; // Unknown placeholder
    }
}
