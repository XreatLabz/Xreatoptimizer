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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Discord webhook notification manager */
public class NotificationManager {

    private final XreatOptimizer plugin;
    private final HttpClient httpClient;
    private final Map<String, Long> lastNotificationTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long NOTIFICATION_COOLDOWN = 300000; // 5 minutes between same-type notifications

    private org.bukkit.scheduler.BukkitTask dailyReportTask;
    private org.bukkit.scheduler.BukkitTask weeklyReportTask;

    // Embed colors
    private static final int COLOR_SUCCESS = 0x10B981;  // Green
    private static final int COLOR_WARNING = 0xF59E0B;  // Amber
    private static final int COLOR_ERROR = 0xEF4444;    // Red
    private static final int COLOR_INFO = 0x3B82F6;     // Blue
    private static final int COLOR_PURPLE = 0x8B5CF6;   // Purple (brand)

    // Severity levels
    public enum Severity {
        INFO(COLOR_INFO, "ℹ️"),
        WARNING(COLOR_WARNING, "⚠️"),
        ERROR(COLOR_ERROR, "🔴"),
        CRITICAL(COLOR_ERROR, "🚨");

        final int color;
        final String emoji;

        Severity(int color, String emoji) {
            this.color = color;
            this.emoji = emoji;
        }
    }

    public NotificationManager(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        // Schedule reports if enabled
        scheduleReports();
    }
    
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("notifications.enabled", false);
    }

    private void scheduleReports() {
        if (!isEnabled()) return;

        // Daily report at configured time (default: 00:00)
        if (plugin.getConfig().getBoolean("notifications.daily_report.enabled", false)) {
            int hour = plugin.getConfig().getInt("notifications.daily_report.hour", 0);
            scheduleDailyReport(hour);
        }

        // Weekly report on configured day (default: Monday at 00:00)
        if (plugin.getConfig().getBoolean("notifications.weekly_report.enabled", false)) {
            String day = plugin.getConfig().getString("notifications.weekly_report.day", "MONDAY");
            int hour = plugin.getConfig().getInt("notifications.weekly_report.hour", 0);
            scheduleWeeklyReport(day, hour);
        }
    }

    private void scheduleDailyReport(int targetHour) {
        // Calculate delay until next target hour
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(targetHour).withMinute(0).withSecond(0);
        if (now.getHour() >= targetHour) {
            next = next.plusDays(1);
        }

        long delaySeconds = java.time.Duration.between(now, next).getSeconds();
        long delayTicks = delaySeconds * 20L;

        dailyReportTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::sendDailyReport,
            delayTicks,
            24 * 60 * 60 * 20L // 24 hours in ticks
        );

        LoggerUtils.info("Daily report scheduled for " + targetHour + ":00");
    }

    private void scheduleWeeklyReport(String targetDay, int targetHour) {
        // Calculate delay until next target day/hour
        LocalDateTime now = LocalDateTime.now();
        java.time.DayOfWeek targetDayOfWeek = java.time.DayOfWeek.valueOf(targetDay.toUpperCase());

        LocalDateTime next = now.withHour(targetHour).withMinute(0).withSecond(0);
        while (next.getDayOfWeek() != targetDayOfWeek || next.isBefore(now)) {
            next = next.plusDays(1);
        }

        long delaySeconds = java.time.Duration.between(now, next).getSeconds();
        long delayTicks = delaySeconds * 20L;

        weeklyReportTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::sendWeeklyReport,
            delayTicks,
            7 * 24 * 60 * 60 * 20L // 7 days in ticks
        );

        LoggerUtils.info("Weekly report scheduled for " + targetDay + " at " + targetHour + ":00");
    }

    private void sendDailyReport() {
        if (!isEnabled()) return;

        com.xreatlabs.xreatoptimizer.managers.PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        if (monitor == null) return;

        double avgTps = (monitor.getMinTps() + monitor.getMaxTps()) / 2.0;
        double currentMemory = monitor.getCurrentMemoryPercentage();
        int entities = monitor.getCurrentEntityCount();
        int chunks = monitor.getCurrentChunkCount();

        String description = "**Daily Performance Summary**\n" +
            "Here's how your server performed in the last 24 hours.";

        String json = buildRichEmbed(
            "📊 Daily Performance Report",
            description,
            COLOR_INFO,
            Severity.INFO,
            new EmbedField("Average TPS", String.format("%.2f", avgTps), true),
            new EmbedField("Min TPS", String.format("%.2f", monitor.getMinTps()), true),
            new EmbedField("Max TPS", String.format("%.2f", monitor.getMaxTps()), true),
            new EmbedField("Current Memory", String.format("%.1f%%", currentMemory), true),
            new EmbedField("Entities", String.valueOf(entities), true),
            new EmbedField("Chunks", String.valueOf(chunks), true),
            new EmbedField("Profile", plugin.getOptimizationManager().getCurrentProfile().name(), true),
            new EmbedField("Uptime", formatUptime(), true)
        );

        sendWebhook(json);
    }

    private void sendWeeklyReport() {
        if (!isEnabled()) return;

        com.xreatlabs.xreatoptimizer.managers.PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        if (monitor == null) return;

        String description = "**Weekly Performance Summary**\n" +
            "A comprehensive overview of your server's performance this week.";

        String json = buildRichEmbed(
            "📈 Weekly Performance Report",
            description,
            COLOR_PURPLE,
            Severity.INFO,
            new EmbedField("Average TPS", String.format("%.2f", (monitor.getMinTps() + monitor.getMaxTps()) / 2.0), true),
            new EmbedField("Peak Players", String.valueOf(Bukkit.getMaxPlayers()), true),
            new EmbedField("Total Optimizations", "N/A", true),
            new EmbedField("Profile Changes", "N/A", true),
            new EmbedField("Lag Spikes Detected", "N/A", true),
            new EmbedField("Memory Pressure Events", "N/A", true)
        );

        sendWebhook(json);
    }

    private String formatUptime() {
        long uptimeMs = System.currentTimeMillis() - plugin.getStartTime();
        long days = uptimeMs / (24 * 60 * 60 * 1000);
        long hours = (uptimeMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (uptimeMs % (60 * 60 * 1000)) / (60 * 1000);

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    public void shutdown() {
        if (dailyReportTask != null) {
            dailyReportTask.cancel();
        }
        if (weeklyReportTask != null) {
            weeklyReportTask.cancel();
        }
    }
    
    private String getWebhookUrl() {
        return plugin.getConfig().getString("notifications.discord_webhook", "");
    }
    
    private boolean shouldNotify(String eventType) {
        long now = System.currentTimeMillis();
        Long lastTime = lastNotificationTimes.get(eventType);
        if (lastTime != null && now - lastTime < NOTIFICATION_COOLDOWN) {
            return false;
        }
        lastNotificationTimes.put(eventType, now);
        return true;
    }
    
    public void notifyLagSpike(double peakMs, String cause) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_lag_spike", true)) {
            return;
        }

        // Check configurable threshold
        double threshold = plugin.getConfig().getDouble("notifications.lag_spike_threshold_ms", 100.0);
        if (peakMs < threshold) {
            return;
        }

        if (!shouldNotify("lag_spike")) {
            LoggerUtils.debug("Lag spike notification skipped (cooldown)");
            return;
        }

        Severity severity = peakMs > 200 ? Severity.CRITICAL : Severity.WARNING;

        String json = buildRichEmbed(
            "Lag Spike Detected",
            String.format("Peak tick time: **%.2fms**\nCause: %s", peakMs, cause),
            severity.color,
            severity,
            new EmbedField("TPS", String.format("%.2f", TPSUtils.getTPS()), true),
            new EmbedField("Memory", String.format("%.1f%%", MemoryUtils.getMemoryUsagePercentage()), true),
            new EmbedField("Players", String.valueOf(Bukkit.getOnlinePlayers().size()), true)
        );

        sendWebhook(json);
    }

    public void notifyMemoryPressure(double percentage) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_memory_pressure", true)) {
            return;
        }

        // Check configurable threshold
        double threshold = plugin.getConfig().getDouble("notifications.memory_threshold_percent", 80.0);
        if (percentage < threshold) {
            return;
        }

        if (!shouldNotify("memory_pressure")) {
            return;
        }

        Severity severity = percentage > 90 ? Severity.CRITICAL : Severity.ERROR;

        String json = buildRichEmbed(
            "High Memory Usage",
            String.format("Memory usage has exceeded **%.1f%%**", percentage),
            severity.color,
            severity,
            new EmbedField("Used", MemoryUtils.getUsedMemoryMB() + "MB", true),
            new EmbedField("Max", MemoryUtils.getMaxMemoryMB() + "MB", true),
            new EmbedField("TPS", String.format("%.2f", TPSUtils.getTPS()), true)
        );

        sendWebhook(json);
    }

    public void notifyLowTPS(double tps) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_low_tps", true)) {
            return;
        }

        // Check configurable threshold
        double threshold = plugin.getConfig().getDouble("notifications.tps_threshold", 15.0);
        if (tps > threshold) {
            return;
        }

        if (!shouldNotify("low_tps")) {
            return;
        }

        Severity severity = tps < 10 ? Severity.CRITICAL : Severity.WARNING;

        String json = buildRichEmbed(
            "Low TPS Detected",
            String.format("Server TPS has dropped to **%.2f**", tps),
            severity.color,
            severity,
            new EmbedField("Memory", String.format("%.1f%%", MemoryUtils.getMemoryUsagePercentage()), true),
            new EmbedField("Entities", String.valueOf(plugin.getPerformanceMonitor().getCurrentEntityCount()), true),
            new EmbedField("Players", String.valueOf(Bukkit.getOnlinePlayers().size()), true)
        );

        sendWebhook(json);
    }
    
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

    public void notifyAnomaly(String type, String description, String recommendation) {
        if (!isEnabled()) {
            return;
        }

        if (!shouldNotify("anomaly_" + type)) {
            return;
        }

        String json = buildEmbed(
            "🔍 Anomaly Detected: " + type,
            description,
            COLOR_WARNING,
            new EmbedField("Type", type, true),
            new EmbedField("TPS", String.format("%.2f", TPSUtils.getTPS()), true),
            new EmbedField("Memory", String.format("%.1f%%", MemoryUtils.getMemoryUsagePercentage()), true),
            new EmbedField("Recommendation", recommendation, false)
        );

        sendWebhook(json);
    }

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
    
    private String buildRichEmbed(String title, String description, int color, Severity severity, EmbedField... fields) {
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
        String titleWithEmoji = severity.emoji + " " + title;

        return String.format(
            "{\"embeds\":[{" +
            "\"title\":\"%s\"," +
            "\"description\":\"%s\"," +
            "\"color\":%d," +
            "\"fields\":[%s]," +
            "\"footer\":{\"text\":\"XreatOptimizer • %s\"}," +
            "\"timestamp\":\"%s\"" +
            "}]}",
            escapeJson(titleWithEmoji),
            escapeJson(description),
            color,
            fieldsJson.toString(),
            severity.name(),
            timestamp
        );
    }

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
    
    private void sendWebhook(String json) {
        sendWebhookAsync(json);
    }
    
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
    
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
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
