package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.api.OptimizationEvent;
import com.xreatlabs.xreatoptimizer.api.XreatOptimizerAPI;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class LagSpikeDetector {
    
    private final XreatOptimizer plugin;
    private final Deque<TickData> tickHistory = new ConcurrentLinkedDeque<>();
    private final List<LagSpike> detectedSpikes = new ArrayList<>();
    private BukkitTask monitorTask;
    private volatile boolean isRunning = false;
    
    private final int HISTORY_SIZE = 600;
    private final double LAG_SPIKE_THRESHOLD = 100.0;
    private final double SEVERE_LAG_THRESHOLD = 200.0;
    private final int CONSECUTIVE_LAG_THRESHOLD = 3;
    
    private long lastTickTime = System.nanoTime();
    private int consecutiveLagTicks = 0;
    private boolean inLagSpike = false;
    
    private static class TickData {
        final long timestamp;
        final double tickTime; // milliseconds
        final double tps;
        final long memoryUsed;
        
        public TickData(long timestamp, double tickTime, double tps, long memoryUsed) {
            this.timestamp = timestamp;
            this.tickTime = tickTime;
            this.tps = tps;
            this.memoryUsed = memoryUsed;
        }
    }
    
    private static class LagSpike {
        final long startTime;
        long endTime;
        double peakTickTime;
        double avgTickTime;
        int duration; // ticks
        String cause;
        boolean mitigated;
        
        public LagSpike(long startTime) {
            this.startTime = startTime;
            this.endTime = startTime;
            this.peakTickTime = 0;
            this.avgTickTime = 0;
            this.duration = 0;
            this.cause = "Unknown";
            this.mitigated = false;
        }
        
        @Override
        public String toString() {
            return String.format("LagSpike[duration=%dms, peak=%.2fms/tick, avg=%.2fms/tick, cause=%s, mitigated=%s]",
                endTime - startTime, peakTickTime, avgTickTime, cause, mitigated);
        }
    }
    
    public LagSpikeDetector(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        if (!plugin.getConfig().getBoolean("lag_spike_detection.enabled", true)) {
            LoggerUtils.info("Lag spike detector is disabled in config.");
            return;
        }
        
        isRunning = true;
        lastTickTime = System.nanoTime();
        
        // Monitor task - runs every tick
        monitorTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::monitorTick,
            1L,
            1L
        );
        
        LoggerUtils.info("Lag spike detector started - monitoring for performance issues");
    }
    
    public void stop() {
        isRunning = false;
        
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        
        tickHistory.clear();
        detectedSpikes.clear();
        
        LoggerUtils.info("Lag spike detector stopped");
    }
    
    private void monitorTick() {
        if (!isRunning) return;
        
        long now = System.nanoTime();
        double tickTime = (now - lastTickTime) / 1_000_000.0; // Convert to milliseconds
        lastTickTime = now;
        
        // Get current TPS and memory
        double tps = TPSUtils.getTPS();
        long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // Store tick data
        TickData tickData = new TickData(System.currentTimeMillis(), tickTime, tps, memoryUsed);
        tickHistory.addFirst(tickData);
        
        // Maintain history size
        while (tickHistory.size() > HISTORY_SIZE) {
            tickHistory.removeLast();
        }
        
        // Detect lag spike
        if (tickTime > LAG_SPIKE_THRESHOLD) {
            consecutiveLagTicks++;
            
            if (consecutiveLagTicks >= CONSECUTIVE_LAG_THRESHOLD && !inLagSpike) {
                // Lag spike detected!
                onLagSpikeDetected(tickTime);
            } else if (inLagSpike) {
                // Update ongoing lag spike
                LagSpike currentSpike = detectedSpikes.get(detectedSpikes.size() - 1);
                currentSpike.duration++;
                currentSpike.peakTickTime = Math.max(currentSpike.peakTickTime, tickTime);
                currentSpike.endTime = System.currentTimeMillis();
            }
        } else {
            if (inLagSpike && consecutiveLagTicks == 0) {
                // Lag spike ended
                onLagSpikeEnded();
            }
            consecutiveLagTicks = Math.max(0, consecutiveLagTicks - 1);
        }
    }
    
    private void onLagSpikeDetected(double tickTime) {
        inLagSpike = true;

        LagSpike spike = new LagSpike(System.currentTimeMillis());
        spike.peakTickTime = tickTime;
        spike.duration = consecutiveLagTicks;
        spike.cause = analyzeCause(tickTime);

        detectedSpikes.add(spike);

        // Log warning
        LoggerUtils.warn(String.format(
            "LAG SPIKE DETECTED: %.2fms/tick | Cause: %s",
            tickTime, spike.cause
        ));

        // Fire lag spike event
        double currentTPS = TPSUtils.getTPS();
        OptimizationEvent.LagSpikeEvent lagSpikeEvent =
            new OptimizationEvent.LagSpikeEvent(tickTime, spike.cause, currentTPS);
        XreatOptimizerAPI.fireEvent(lagSpikeEvent);

        // Trigger JFR recording if available
        if (plugin.getJFRIntegration() != null && plugin.getJFRIntegration().isEnabled()) {
            plugin.getJFRIntegration().recordLagSpike(tickTime, spike.cause);
        }

        // Trigger mitigation
        if (tickTime > SEVERE_LAG_THRESHOLD) {
            mitigateSevereLag(spike);
        } else {
            mitigateNormalLag(spike);
        }
    }
    
    private void onLagSpikeEnded() {
        inLagSpike = false;
        
        if (!detectedSpikes.isEmpty()) {
            LagSpike spike = detectedSpikes.get(detectedSpikes.size() - 1);
            
            // Calculate average
            double sum = 0;
            int count = 0;
            for (TickData data : tickHistory) {
                if (data.timestamp >= spike.startTime && data.timestamp <= spike.endTime) {
                    sum += data.tickTime;
                    count++;
                }
            }
            spike.avgTickTime = count > 0 ? sum / count : spike.peakTickTime;
            
            LoggerUtils.info(String.format(
                "Lag spike ended: %s", spike
            ));
        }
    }
    
    private String analyzeCause(double tickTime) {
        // Check memory pressure
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
        
        if (memoryUsage > 0.9) {
            return "High memory usage (" + String.format("%.1f%%", memoryUsage * 100) + ")";
        }
        
        // Check entity count
        int entityCount = Bukkit.getWorlds().stream()
            .mapToInt(w -> w.getEntities().size())
            .sum();
        
        if (entityCount > 10000) {
            return "High entity count (" + entityCount + ")";
        }
        
        // Check chunk loading
        int loadedChunks = Bukkit.getWorlds().stream()
            .mapToInt(w -> w.getLoadedChunks().length)
            .sum();
        
        if (loadedChunks > 5000) {
            return "High chunk count (" + loadedChunks + ")";
        }
        
        // Check player count
        int playerCount = Bukkit.getOnlinePlayers().size();
        if (playerCount > 100) {
            return "High player count (" + playerCount + ")";
        }
        
        return "Unknown (possibly chunk generation or plugin)";
    }
    
    private void mitigateNormalLag(LagSpike spike) {
        spike.mitigated = true;
        
        // Pause non-critical systems temporarily
        plugin.getThreadPoolManager().executeAnalyticsTask(() -> {
            LoggerUtils.info("Applying lag mitigation: Pausing non-critical systems");
            
            // Suggestion: Temporarily reduce view distance, pause entity spawning, etc.
            // Implementation depends on server version and configuration
        });
    }
    
    /** Mitigate severe lag spike (safe operations only) */
    private void mitigateSevereLag(LagSpike spike) {
        spike.mitigated = true;
        
        LoggerUtils.warn("SEVERE LAG DETECTED - Applying safe emergency optimizations");
        
        // Emergency measures - SAFE operations only
        plugin.getThreadPoolManager().executeAnalyticsTask(() -> {
            // Suggest garbage collection (non-destructive)
            System.gc();
            LoggerUtils.info("Emergency: Suggested garbage collection due to severe lag");
            
            // NOTE: We intentionally do NOT remove dropped items here anymore.
            // Removing items was causing players to lose valuable drops unexpectedly.
            // Items are handled by ItemDropTracker with proper warnings instead.
            
            // Send notification if webhooks are configured
            if (plugin.getConfig().getBoolean("notifications.enabled", false)) {
                String webhook = plugin.getConfig().getString("notifications.discord_webhook", "");
                if (!webhook.isEmpty()) {
                    sendDiscordNotification(webhook, spike);
                }
            }
            
            // Record to web dashboard if available
            if (plugin.getWebDashboard() != null && plugin.getWebDashboard().isRunning()) {
                plugin.getWebDashboard().recordLagSpike(spike.peakTickTime, spike.cause);
            }
        });
    }
    
    /** Send Discord webhook for lag spike */
    private void sendDiscordNotification(String webhookUrl, LagSpike spike) {
        try {
            String json = String.format(
                "{\"embeds\":[{\"title\":\"⚠️ Severe Lag Spike Detected\"," +
                "\"description\":\"Peak: %.2fms/tick\\nCause: %s\"," +
                "\"color\":16711680}]}",
                spike.peakTickTime, spike.cause
            );
            
            java.net.URL url = new java.net.URL(webhookUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            
            // Read response to complete the request
            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                LoggerUtils.debug("Discord webhook returned status: " + responseCode);
            }
            conn.disconnect();
        } catch (Exception e) {
            LoggerUtils.debug("Failed to send Discord notification: " + e.getMessage());
        }
    }
    
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_spikes", detectedSpikes.size());
        stats.put("in_lag_spike", inLagSpike);
        stats.put("consecutive_lag_ticks", consecutiveLagTicks);
        
        if (!tickHistory.isEmpty()) {
            double avgTickTime = tickHistory.stream()
                .mapToDouble(t -> t.tickTime)
                .average()
                .orElse(0.0);
            stats.put("avg_tick_time_ms", String.format("%.2f", avgTickTime));
        }
        
        long mitigatedCount = detectedSpikes.stream()
            .filter(s -> s.mitigated)
            .count();
        stats.put("mitigated_spikes", mitigatedCount);
        
        return stats;
    }
    
    public List<LagSpike> getRecentSpikes(int count) {
        int size = Math.min(count, detectedSpikes.size());
        return detectedSpikes.subList(Math.max(0, detectedSpikes.size() - size), detectedSpikes.size());
    }
    
    public boolean isInLagSpike() {
        return inLagSpike;
    }
}
