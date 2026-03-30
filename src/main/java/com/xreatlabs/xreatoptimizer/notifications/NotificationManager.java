package com.xreatlabs.xreatoptimizer.notifications;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

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
    private static final long NOTIFICATION_COOLDOWN = 300000;

    private BukkitTask dailyReportTask;
    private BukkitTask weeklyReportTask;

    private static final int COLOR_SUCCESS = 0x10B981;
    private static final int COLOR_WARNING = 0xF59E0B;
    private static final int COLOR_ERROR = 0xEF4444;
    private static final int COLOR_INFO = 0x3B82F6;
    private static final int COLOR_PURPLE = 0x8B5CF6;

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
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        scheduleReports();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("notifications.enabled", false);
    }

    private void scheduleReports() {
        if (!isEnabled()) return;

        if (plugin.getConfig().getBoolean("notifications.daily_report.enabled", false)) {
            int hour = plugin.getConfig().getInt("notifications.daily_report.hour", 0);
            scheduleDailyReport(hour);
        }

        if (plugin.getConfig().getBoolean("notifications.weekly_report.enabled", false)) {
            String day = plugin.getConfig().getString("notifications.weekly_report.day", "MONDAY");
            int hour = plugin.getConfig().getInt("notifications.weekly_report.hour", 0);
            scheduleWeeklyReport(day, hour);
        }
    }

    private void scheduleDailyReport(int targetHour) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }

        long delayTicks = java.time.Duration.between(now, next).getSeconds() * 20L;
        dailyReportTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sendDailyReport, delayTicks, 24 * 60 * 60 * 20L);
        LoggerUtils.info("Daily report scheduled for " + targetHour + ":00");
    }

    private void scheduleWeeklyReport(String targetDay, int targetHour) {
        LocalDateTime now = LocalDateTime.now();
        java.time.DayOfWeek targetDayOfWeek = java.time.DayOfWeek.valueOf(targetDay.toUpperCase());

        LocalDateTime next = now.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);
        while (next.getDayOfWeek() != targetDayOfWeek || !next.isAfter(now)) {
            next = next.plusDays(1);
        }

        long delayTicks = java.time.Duration.between(now, next).getSeconds() * 20L;
        weeklyReportTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sendWeeklyReport, delayTicks, 7 * 24 * 60 * 60 * 20L);
        LoggerUtils.info("Weekly report scheduled for " + targetDay + " at " + targetHour + ":00");
    }

    private void sendDailyReport() {
        if (!isEnabled()) return;

        com.xreatlabs.xreatoptimizer.managers.PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        if (monitor == null) return;

        String json = buildRichEmbed(
            "📊 Daily Performance Report",
            "**Daily Performance Summary**\nHere's how your server performed in the last 24 hours.",
            COLOR_INFO,
            Severity.INFO,
            new EmbedField("Average TPS", String.format("%.2f", (monitor.getMinTps() + monitor.getMaxTps()) / 2.0), true),
            new EmbedField("Min TPS", String.format("%.2f", monitor.getMinTps()), true),
            new EmbedField("Max TPS", String.format("%.2f", monitor.getMaxTps()), true),
            new EmbedField("Current Memory", String.format("%.1f%%", monitor.getCurrentMemoryPercentage()), true),
            new EmbedField("Entities", String.valueOf(monitor.getCurrentEntityCount()), true),
            new EmbedField("Chunks", String.valueOf(monitor.getCurrentChunkCount()), true),
            new EmbedField("Profile", plugin.getOptimizationManager().getCurrentProfile().name(), true),
            new EmbedField("Uptime", formatUptime(), true)
        );

        sendWebhook(json);
    }

    private void sendWeeklyReport() {
        if (!isEnabled()) return;

        com.xreatlabs.xreatoptimizer.managers.PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        if (monitor == null) return;

        String json = buildRichEmbed(
            "📈 Weekly Performance Report",
            "**Weekly Performance Summary**\nA compact overview of your server's weekly trend.",
            COLOR_PURPLE,
            Severity.INFO,
            new EmbedField("Average TPS", String.format("%.2f", (monitor.getMinTps() + monitor.getMaxTps()) / 2.0), true),
            new EmbedField("Peak Players", String.valueOf(Bukkit.getMaxPlayers()), true),
            new EmbedField("Current Memory", String.format("%.1f%%", monitor.getCurrentMemoryPercentage()), true)
        );

        sendWebhook(json);
    }

    private String formatUptime() {
        long uptimeMs = System.currentTimeMillis() - plugin.getStartTime();
        long days = uptimeMs / (24 * 60 * 60 * 1000);
        long hours = (uptimeMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (uptimeMs % (60 * 60 * 1000)) / (60 * 1000);

        if (days > 0) return String.format("%dd %dh %dm", days, hours, minutes);
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        return String.format("%dm", minutes);
    }

    public void shutdown() {
        if (dailyReportTask != null) dailyReportTask.cancel();
        if (weeklyReportTask != null) weeklyReportTask.cancel();
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
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_lag_spike", true)) return;
        double threshold = plugin.getConfig().getDouble("notifications.lag_spike_threshold_ms", 100.0);
        if (peakMs < threshold || !shouldNotify("lag_spike")) return;

        Severity severity = peakMs > 200 ? Severity.CRITICAL : Severity.WARNING;
        String json = buildRichEmbed(
            "Lag Spike Detected",
            String.format("Peak tick time: **%.2fms**\nCause: %s", peakMs, cause),
            severity.color,
            severity,
            new EmbedField("TPS", String.format("%.2f", TPSUtils.getTPS()), true),
            new EmbedField("Memory", String.format("%.1f%%", MemoryUtils.getMemoryUsagePercentage()), true),
            new EmbedField("Players", String.valueOf(plugin.getPerformanceMonitor().getCurrentPlayerCount()), true)
        );
        sendWebhook(json);
    }

    public void notifyMemoryPressure(double percentage) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_memory_pressure", true)) return;
        double threshold = plugin.getConfig().getDouble("notifications.memory_threshold_percent", 80.0);
        if (percentage < threshold || !shouldNotify("memory_pressure")) return;

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
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_low_tps", true)) return;
        double threshold = plugin.getConfig().getDouble("notifications.tps_threshold", 15.0);
        if (tps > threshold || !shouldNotify("low_tps")) return;

        Severity severity = tps < 10 ? Severity.CRITICAL : Severity.WARNING;
        String json = buildRichEmbed(
            "Low TPS Detected",
            String.format("Server TPS has dropped to **%.2f**", tps),
            severity.color,
            severity,
            new EmbedField("Memory", String.format("%.1f%%", MemoryUtils.getMemoryUsagePercentage()), true),
            new EmbedField("Entities", String.valueOf(plugin.getPerformanceMonitor().getCurrentEntityCount()), true),
            new EmbedField("Players", String.valueOf(plugin.getPerformanceMonitor().getCurrentPlayerCount()), true)
        );
        sendWebhook(json);
    }

    public void notifyServerStart() {
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_start", false)) return;

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
        if (!isEnabled() || !plugin.getConfig().getBoolean("notifications.notify_on_profile_change", false)) return;

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
        if (!isEnabled() || !shouldNotify("anomaly_" + type)) return;

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
                    }
                    LoggerUtils.warn("Discord webhook failed: HTTP " + response.statusCode());
                    return false;
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
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
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
