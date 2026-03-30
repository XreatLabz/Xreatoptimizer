package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.WeakHashMap;

public class NetworkOptimizer {
    private final XreatOptimizer plugin;
    private BukkitTask optimizationTask;
    private final Map<Player, PlayerNetworkStats> playerStats = new WeakHashMap<>();
    private volatile boolean isRunning = false;
    private boolean featureEnabled = true;

    private static class PlayerNetworkStats {
        long lastTabUpdate = 0;
        long lastScoreboardUpdate = 0;
        long packetCount = 0;
        long compressedPacketCount = 0;
        double compressionRatio = 1.0;
    }

    public NetworkOptimizer(XreatOptimizer plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        featureEnabled = plugin.getConfig().getBoolean("network_optimizer.enabled", true);
    }

    public void start() {
        loadConfig();
        if (!featureEnabled) {
            LoggerUtils.info("Network optimizer is disabled in config.");
            return;
        }

        if (optimizationTask != null) {
            return;
        }

        optimizationTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::runNetworkOptimizations,
            100L,
            100L
        );

        isRunning = true;
        LoggerUtils.info("Network optimizer started.");
    }

    public void stop() {
        isRunning = false;
        if (optimizationTask != null) {
            optimizationTask.cancel();
            optimizationTask = null;
        }

        playerStats.clear();
        LoggerUtils.info("Network optimizer stopped.");
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            if (!isRunning) {
                start();
            }
        } else if (isRunning) {
            stop();
        }
    }

    public boolean isEnabled() {
        return isRunning;
    }

    private void runNetworkOptimizations() {
        if (!isRunning) {
            return;
        }

        double tps = TPSUtils.getTPS();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerStats.computeIfAbsent(player, k -> new PlayerNetworkStats());
        }

        if (System.currentTimeMillis() % 60000 < 5000) {
            LoggerUtils.debug("Network optimization: " + Bukkit.getOnlinePlayers().size() +
                " players, TPS=" + String.format("%.1f", tps));
        }
    }

    public boolean shouldThrottleTabUpdate(Player player) {
        if (!plugin.getVersionAdapter().isVersionAtLeast(1, 7)) {
            return true;
        }

        PlayerNetworkStats stats = playerStats.computeIfAbsent(player, k -> new PlayerNetworkStats());
        long minInterval = getOptimalUpdateInterval();
        boolean shouldUpdate = System.currentTimeMillis() - stats.lastTabUpdate > minInterval;

        if (shouldUpdate) {
            stats.lastTabUpdate = System.currentTimeMillis();
        }

        return !shouldUpdate;
    }

    public boolean shouldThrottleScoreboardUpdate(Player player) {
        if (!plugin.getVersionAdapter().isVersionAtLeast(1, 7)) {
            return true;
        }

        PlayerNetworkStats stats = playerStats.computeIfAbsent(player, k -> new PlayerNetworkStats());
        long minInterval = getOptimalUpdateInterval();
        boolean shouldUpdate = System.currentTimeMillis() - stats.lastScoreboardUpdate > minInterval;

        if (shouldUpdate) {
            stats.lastScoreboardUpdate = System.currentTimeMillis();
        }

        return !shouldUpdate;
    }

    private long getOptimalUpdateInterval() {
        double tps = TPSUtils.getTPS();

        if (tps > 19.5) {
            return 1000;
        } else if (tps > 18.0) {
            return 2000;
        } else if (tps > 15.0) {
            return 3000;
        } else {
            return 5000;
        }
    }

    public void recordPacket(Player player, boolean wasCompressed) {
        PlayerNetworkStats stats = playerStats.computeIfAbsent(player, k -> new PlayerNetworkStats());
        stats.packetCount++;

        if (wasCompressed) {
            stats.compressedPacketCount++;
        }

        if (stats.packetCount > 0) {
            stats.compressionRatio = (double) stats.compressedPacketCount / stats.packetCount;
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public Object getPlayerStats(Player player) {
        return playerStats.get(player);
    }

    public void reloadConfig() {
        boolean wasRunning = isRunning;
        if (wasRunning) {
            stop();
        } else {
            loadConfig();
        }

        if (plugin.getConfig().getBoolean("network_optimizer.enabled", true)) {
            start();
        }
    }
}
