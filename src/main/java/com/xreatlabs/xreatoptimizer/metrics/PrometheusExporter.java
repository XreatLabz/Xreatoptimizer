package com.xreatlabs.xreatoptimizer.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Prometheus metrics exporter */
public class PrometheusExporter {

    private final XreatOptimizer plugin;
    private final MetricsRegistry metricsRegistry;
    private HttpServer httpServer;
    private BukkitTask updateTask;
    private boolean enabled;
    private int port;
    private String bindAddress;

    public PrometheusExporter(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.metricsRegistry = new MetricsRegistry(plugin);

        // Load configuration
        this.enabled = plugin.getConfig().getBoolean("metrics.prometheus.enabled", false);
        this.port = plugin.getConfig().getInt("metrics.prometheus.port", 9090);
        this.bindAddress = plugin.getConfig().getString("metrics.prometheus.bind_address", "127.0.0.1");
    }

    public void start() {
        if (!enabled) {
            LoggerUtils.info("Prometheus metrics exporter is disabled");
            return;
        }

        try {
            // Create HTTP server
            InetSocketAddress address = new InetSocketAddress(bindAddress, port);
            httpServer = HttpServer.create(address, 0);

            // Register /metrics endpoint
            httpServer.createContext("/metrics", new MetricsHandler());

            // Start server in a separate thread
            httpServer.setExecutor(null); // Use default executor
            httpServer.start();

            LoggerUtils.info("Prometheus metrics exporter started on " + bindAddress + ":" + port);
            LoggerUtils.info("Metrics available at http://" + bindAddress + ":" + port + "/metrics");

            // Start periodic metrics update task (every 5 seconds = 100 ticks)
            updateTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::updateMetrics,
                100L,
                100L
            );

        } catch (IOException e) {
            LoggerUtils.error("Failed to start Prometheus exporter on " + bindAddress + ":" + port, e);
            LoggerUtils.error("Make sure the port is not already in use and the bind address is valid");
        }
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            LoggerUtils.info("Prometheus metrics exporter stopped");
        }
    }

    private void updateMetrics() {
        try {
            // Update TPS
            double tps = plugin.getPerformanceMonitor().getCurrentTPS();
            metricsRegistry.updateTps(tps);

            // Update memory
            long usedMemory = (long) plugin.getPerformanceMonitor().getMetric("used_memory_mb");
            long maxMemory = (long) plugin.getPerformanceMonitor().getMetric("max_memory_mb");
            double memoryPercentage = plugin.getPerformanceMonitor().getCurrentMemoryPercentage();
            metricsRegistry.updateMemory(usedMemory, maxMemory, memoryPercentage);

            // Update entity count
            int entityCount = plugin.getPerformanceMonitor().getCurrentEntityCount();
            metricsRegistry.updateEntityCount(entityCount);

            // Update chunk count
            int chunkCount = plugin.getPerformanceMonitor().getCurrentChunkCount();
            metricsRegistry.updateChunkCount(chunkCount);

            // Update player count
            int playerCount = Bukkit.getOnlinePlayers().size();
            metricsRegistry.updatePlayerCount(playerCount);

            // Update thread pool metrics if available
            if (plugin.getThreadPoolManager() != null) {
                int activeThreads = plugin.getThreadPoolManager().getActiveThreadCount();
                int queuedTasks = plugin.getThreadPoolManager().getQueuedTaskCount();
                metricsRegistry.updateThreadPool(activeThreads, queuedTasks);
            }

        } catch (Exception e) {
            LoggerUtils.debug("Error updating Prometheus metrics: " + e.getMessage());
        }
    }

    public MetricsRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                // Only accept GET requests
                String response = "Method not allowed";
                exchange.sendResponseHeaders(405, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            try {
                // Get metrics in Prometheus format
                String metrics = metricsRegistry.scrape();

                // Send response
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                exchange.sendResponseHeaders(200, metrics.length());

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(metrics.getBytes(StandardCharsets.UTF_8));
                }

            } catch (Exception e) {
                LoggerUtils.error("Error serving metrics", e);

                String errorResponse = "Internal server error";
                exchange.sendResponseHeaders(500, errorResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
