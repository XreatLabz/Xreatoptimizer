package com.xreatlabs.xreatoptimizer.notifications;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Notification manager for Discord webhooks and other alert systems
 * Sends notifications for important server events
 */
public class NotificationManager {
    
    private final XreatOptimizer plugin;
    private final HttpClient httpClient;
    private long lastNotificationTime = 0;
    private static final long NOTIFICATION_COOLDOWN = 300000; // 5 minutes between notifications
    
    // Embed colors
    private static final int COLOR_SUCCESS = 0x10B981;  // Green
    private static final int COLOR_WARNING = 0xF59E0B;  // Amber
    private static final int COLOR_ERROR = 0xEF4444;    // Red
    private static final int COLOR_INFO = 0x3B82F6;     // Blue
    private static final int COLOR_PURPLE = 0x8B5CF6;   // Purple (brand)
    
    public NotificationManager(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Check if notifications are enabled
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("notifications.enabled", false);
    }
    
    /**
     * Get the Discord webhook URL
     */
    private String getWebhookUrl() {
        return plugin.getConfig().getString("notifications.discord_webhook", "");
    }
    
    /**
     * Check if we should send a notification (cooldown check)
     */
    private boolean shouldNotify() {
        long now = System.currentTimeMillis();
        if (now - lastNotificationTime < NOTIFICATION_COOLDOWN) {
            return false;
        }
        lastNotificationTime = now;
        return true;
    }
    
    /**
     * Send a lag spike notification
     */
    public void notifyLagSpike(double peakMs, String cause) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_lag_spike", true)) {
            return;
        }
        
        if (!shouldNotify()) {
            LoggerUtils.debug("Lag spike notification skipped (cooldown)");
            return;
        }
        
        String json = buildEmbed(
            "⚠️ Lag Spike Detected",
            String.format("Peak tick time: **%.2fms**\\nCause: %s", peakMs, cause),
            COLOR_WARNING,
            new EmbedField("TPS", String.format("%.2f", TPSUtils.getTPS()), true),
            new EmbedField("Memory", String.format("%.1f%%", MemoryUtils.getMemoryUsagePercentage()), true),
            new EmbedField("Players", String.valueOf(Bukkit.getOnlinePlayers().size()), true)
        );
        
        sendWebhook(json);
    }
    
    /**
     * Send a memory pressure notification
     */
    public void notifyMemoryPressure(double percentage) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_memory_pressure", true)) {
            return;
        }
        
        if (!shouldNotify()) {
            return;
        }
        
        String json = buildEmbed(
            "🔴 High Memory Usage",
            String.format("Memory usage has exceeded **%.1f%%**", percentage),
            COLOR_ERROR,
            new EmbedField("Used", MemoryUtils.getUsedMemoryMB() + "MB", true),
            new EmbedField("Max", MemoryUtils.getMaxMemoryMB() + "MB", true),
            new EmbedField("TPS", String.format("%.2f", TPSUtils.getTPS()), true)
        );
        
        sendWebhook(json);
    }
    
    /**
     * Send a TPS drop notification
     */
    public void notifyLowTPS(double tps) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_low_tps", true)) {
            return;
        }
        
        if (!shouldNotify()) {
            return;
        }
        
        int color = tps < 10 ? COLOR_ERROR : COLOR_WARNING;
        String severity = tps < 10 ? "🔴 Critical" : "⚠️ Warning";
        
        String json = buildEmbed(
            severity + " - Low TPS Detected",
            String.format("Server TPS has dropped to **%.2f**", tps),
            color,
            new EmbedField("Memory", String.format("%.1f%%", MemoryUtils.getMemoryUsagePercentage()), true),
            new EmbedField("Entities", String.valueOf(plugin.getPerformanceMonitor().getCurrentEntityCount()), true),
            new EmbedField("Players", String.valueOf(Bukkit.getOnlinePlayers().size()), true)
        );
        
        sendWebhook(json);
    }
    
    /**
     * Send a server start notification
     */
    public void notifyServerStart() {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_start", false)) {
            return;
        }
        
        String json = buildEmbed(
            "✅ Server Started",
            "XreatOptimizer is now active and monitoring performance.",
            COLOR_SUCCESS,
            new EmbedField("Version", plugin.getDescription().getVersion(), true),
            new EmbedField("Max Memory", MemoryUtils.getMaxMemoryMB() + "MB", true)
        );
        
        sendWebhook(json);
    }
    
    /**
     * Send a profile change notification
     */
    public void notifyProfileChange(String oldProfile, String newProfile) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_profile_change", false)) {
            return;
        }
        
        String json = buildEmbed(
            "🔄 Optimization Profile Changed",
            String.format("Profile changed from **%s** to **%s**", oldProfile, newProfile),
            COLOR_INFO,
            new EmbedField("TPS", String.format("%.2f", TPSUtils.getTPS()), true),
            new EmbedField("Memory", String.format("%.1f%%", MemoryUtils.getMemoryUsagePercentage()), true)
        );
        
        sendWebhook(json);
    }
    
    /**
     * Send a test notification
     */
    public CompletableFuture<Boolean> sendTestNotification() {
        String json = buildEmbed(
            "🧪 Test Notification",
            "This is a test notification from XreatOptimizer.",
            COLOR_PURPLE,
            new EmbedField("Status", "✅ Working", true),
            new EmbedField("Server", Bukkit.getServer().getName(), true),
            new EmbedField("Time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), true)
        );
        
        return sendWebhookAsync(json);
    }
    
    /**
     * Build a Discord embed JSON
     */
    private String buildEmbed(String title, String description, int color, EmbedField... fields) {
        StringBuilder fieldsJson = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) fieldsJson.append(",");
            fieldsJson.append(String.format(
                "{\"name\":\"%s\",\"value\":\"%s\",\"inline\":%s}",
                escapeJson(fields[i].name),
                escapeJson(fields[i].value),
                fields[i].inline
            ));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        return String.format(
            "{\"embeds\":[{" +
            "\"title\":\"%s\"," +
            "\"description\":\"%s\"," +
            "\"color\":%d," +
            "\"fields\":[%s]," +
            "\"footer\":{\"text\":\"XreatOptimizer\"}," +
            "\"timestamp\":\"%s\"" +
            "}]}",
            escapeJson(title),
            escapeJson(description),
            color,
            fieldsJson.toString(),
            timestamp
        );
    }
    
    /**
     * Send webhook synchronously
     */
    private void sendWebhook(String json) {
        sendWebhookAsync(json);
    }
    
    /**
     * Send webhook asynchronously
     */
    private CompletableFuture<Boolean> sendWebhookAsync(String json) {
        String url = getWebhookUrl();
        if (url == null || url.isEmpty()) {
            LoggerUtils.debug("Discord webhook URL not configured");
            return CompletableFuture.completedFuture(false);
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        LoggerUtils.debug("Discord notification sent successfully");
                        return true;
                    } else {
                        LoggerUtils.warn("Discord webhook failed: HTTP " + response.statusCode());
                        return false;
                    }
                })
                .exceptionally(e -> {
                    LoggerUtils.warn("Discord notification failed: " + e.getMessage());
                    return false;
                });
                
        } catch (Exception e) {
            LoggerUtils.warn("Failed to send Discord notification: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * Escape special characters for JSON
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    /**
     * Embed field helper class
     */
    private static class EmbedField {
        final String name;
        final String value;
        final boolean inline;
        
        EmbedField(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }
}
