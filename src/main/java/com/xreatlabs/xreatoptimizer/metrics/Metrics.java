package com.xreatlabs.xreatoptimizer.metrics;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * BStats-compatible metrics for XreatOptimizer
 * Collects anonymous usage statistics to help improve the plugin
 * 
 * All data is anonymous and used only for understanding how the plugin is used.
 * No personal information, server IPs, or player data is collected.
 */
public class Metrics {
    
    private final XreatOptimizer plugin;
    private final boolean enabled;
    private final String serverUUID;
    private final ScheduledExecutorService scheduler;
    
    // BStats service ID (you would register at bstats.org to get one)
    private static final int BSTATS_PLUGIN_ID = 00000; // Replace with actual ID
    private static final String BSTATS_URL = "https://bStats.org/api/v2/data/bukkit";
    
    public Metrics(XreatOptimizer plugin) {
        this.plugin = plugin;
        
        // Check if metrics are enabled
        File configFile = new File(plugin.getDataFolder().getParentFile(), "bStats/config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        if (!config.contains("enabled")) {
            config.set("enabled", true);
            config.set("serverUuid", UUID.randomUUID().toString());
            config.set("logFailedRequests", false);
            try {
                configFile.getParentFile().mkdirs();
                config.save(configFile);
            } catch (IOException e) {
                LoggerUtils.debug("Could not save bStats config");
            }
        }
        
        this.enabled = config.getBoolean("enabled", true);
        this.serverUUID = config.getString("serverUuid", UUID.randomUUID().toString());
        
        if (enabled) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
            startSubmitting();
            LoggerUtils.debug("BStats metrics enabled");
        } else {
            this.scheduler = null;
            LoggerUtils.debug("BStats metrics disabled");
        }
    }
    
    /**
     * Start submitting metrics data
     */
    private void startSubmitting() {
        if (scheduler == null) return;
        
        // Submit every 30 minutes
        scheduler.scheduleAtFixedRate(this::submitData, 5, 30, TimeUnit.MINUTES);
    }
    
    /**
     * Submit metrics data to bStats
     */
    private void submitData() {
        if (!enabled) return;
        
        try {
            Map<String, Object> data = collectData();
            String json = mapToJson(data);
            
            sendData(json);
            
        } catch (Exception e) {
            LoggerUtils.debug("Failed to submit metrics: " + e.getMessage());
        }
    }
    
    /**
     * Collect all metrics data
     */
    private Map<String, Object> collectData() {
        Map<String, Object> data = new LinkedHashMap<>();
        
        // Plugin data
        data.put("pluginVersion", plugin.getDescription().getVersion());
        
        // Server data
        data.put("serverVersion", Bukkit.getVersion());
        data.put("bukkitVersion", Bukkit.getBukkitVersion());
        data.put("playerCount", Bukkit.getOnlinePlayers().size());
        data.put("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
        data.put("javaVersion", System.getProperty("java.version"));
        
        // XreatOptimizer specific metrics
        Map<String, Object> customCharts = new LinkedHashMap<>();
        
        // Current optimization profile
        customCharts.put("optimization_profile", createSimplePie(
            plugin.getOptimizationManager().getCurrentProfile().name()
        ));
        
        // TPS range
        double tps = TPSUtils.getTPS();
        String tpsRange;
        if (tps >= 19) tpsRange = "19-20";
        else if (tps >= 17) tpsRange = "17-19";
        else if (tps >= 14) tpsRange = "14-17";
        else if (tps >= 10) tpsRange = "10-14";
        else tpsRange = "Below 10";
        customCharts.put("tps_range", createSimplePie(tpsRange));
        
        // Memory usage range
        double mem = MemoryUtils.getMemoryUsagePercentage();
        String memRange;
        if (mem < 50) memRange = "0-50%";
        else if (mem < 70) memRange = "50-70%";
        else if (mem < 85) memRange = "70-85%";
        else memRange = "85-100%";
        customCharts.put("memory_range", createSimplePie(memRange));
        
        // Features enabled
        Map<String, Integer> featuresEnabled = new LinkedHashMap<>();
        featuresEnabled.put("Entity Limiter", plugin.getConfig().getBoolean("entity_limiter.enabled", false) ? 1 : 0);
        featuresEnabled.put("Hibernate", plugin.getConfig().getBoolean("hibernate.enabled", false) ? 1 : 0);
        featuresEnabled.put("Auto Clear", plugin.getConfig().getBoolean("auto_clear.enabled", false) ? 1 : 0);
        featuresEnabled.put("Auto Tune", plugin.getConfig().getBoolean("auto_tune", true) ? 1 : 0);
        featuresEnabled.put("Notifications", plugin.getConfig().getBoolean("notifications.enabled", false) ? 1 : 0);
        customCharts.put("features_enabled", createAdvancedPie(featuresEnabled));
        
        // Server type detection
        String serverType = detectServerType();
        customCharts.put("server_type", createSimplePie(serverType));
        
        // Entity count range
        int entities = plugin.getPerformanceMonitor().getCurrentEntityCount();
        String entityRange;
        if (entities < 1000) entityRange = "0-1000";
        else if (entities < 5000) entityRange = "1000-5000";
        else if (entities < 10000) entityRange = "5000-10000";
        else entityRange = "10000+";
        customCharts.put("entity_count_range", createSimplePie(entityRange));
        
        data.put("customCharts", customCharts);
        
        return data;
    }
    
    /**
     * Detect server type (Paper, Spigot, etc.)
     */
    private String detectServerType() {
        String version = Bukkit.getVersion().toLowerCase();
        if (version.contains("purpur")) return "Purpur";
        if (version.contains("pufferfish")) return "Pufferfish";
        if (version.contains("paper")) return "Paper";
        if (version.contains("spigot")) return "Spigot";
        if (version.contains("craftbukkit")) return "CraftBukkit";
        if (version.contains("folia")) return "Folia";
        return "Unknown";
    }
    
    /**
     * Create a simple pie chart data structure
     */
    private Map<String, Object> createSimplePie(String value) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("chartId", "simple_pie");
        chart.put("data", Map.of("value", value));
        return chart;
    }
    
    /**
     * Create an advanced pie chart data structure
     */
    private Map<String, Object> createAdvancedPie(Map<String, Integer> values) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("chartId", "advanced_pie");
        chart.put("data", Map.of("values", values));
        return chart;
    }
    
    /**
     * Convert map to JSON string
     */
    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof Map) {
                json.append(mapToJson((Map<String, Object>) value));
            } else {
                json.append("\"").append(value.toString()).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Send data to bStats
     */
    private void sendData(String json) throws Exception {
        if (BSTATS_PLUGIN_ID == 0) {
            // Plugin ID not configured, skip sending
            return;
        }
        
        URL url = new URL(BSTATS_URL);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        
        byte[] compressedData = compress(json);
        
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("Connection", "close");
        connection.addRequestProperty("Content-Encoding", "gzip");
        connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "XreatOptimizer/" + plugin.getDescription().getVersion());
        
        connection.setDoOutput(true);
        
        try (OutputStream out = connection.getOutputStream()) {
            out.write(compressedData);
        }
        
        connection.getInputStream().close();
    }
    
    /**
     * Compress data with GZIP
     */
    private byte[] compress(String data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }
    
    /**
     * Shutdown the metrics service
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
