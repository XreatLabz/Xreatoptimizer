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
            InetSocketAddress address = new InetSocketAddress(bindAddress, port);
            httpServer = HttpServer.create(address, 0);
            httpServer.createContext("/metrics", new MetricsHandler());
            httpServer.createContext("/", new MetricsHomeHandler());
            httpServer.setExecutor(null);
            httpServer.start();

            LoggerUtils.info("Prometheus metrics exporter started on " + bindAddress + ":" + port);
            LoggerUtils.info("Metrics available at http://" + bindAddress + ":" + port + "/metrics");

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
            double tps = plugin.getPerformanceMonitor().getCurrentTPS();
            metricsRegistry.updateTps(tps);

            long usedMemory = (long) plugin.getPerformanceMonitor().getMetric("used_memory_mb");
            long maxMemory = (long) plugin.getPerformanceMonitor().getMetric("max_memory_mb");
            double memoryPercentage = plugin.getPerformanceMonitor().getCurrentMemoryPercentage();
            metricsRegistry.updateMemory(usedMemory, maxMemory, memoryPercentage);

            int entityCount = plugin.getPerformanceMonitor().getCurrentEntityCount();
            metricsRegistry.updateEntityCount(entityCount);

            int chunkCount = plugin.getPerformanceMonitor().getCurrentChunkCount();
            metricsRegistry.updateChunkCount(chunkCount);

            int playerCount = plugin.getPerformanceMonitor().getCurrentPlayerCount();
            metricsRegistry.updatePlayerCount(playerCount);

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
                String response = "Method not allowed";
                exchange.sendResponseHeaders(405, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            try {
                String metrics = metricsRegistry.scrape();
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

    private class MetricsHomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                String response = "Method not allowed";
                exchange.sendResponseHeaders(405, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String html = generateMetricsHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, html.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private String generateMetricsHtml() {
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>XreatOptimizer Metrics</title>" +
            "<style>" +
            "body{margin:0;font-family:Inter,Segoe UI,Arial,sans-serif;background:#0b1220;color:#eef4ff}" +
            ".wrap{max-width:1100px;margin:0 auto;padding:32px 20px 48px}.hero{display:flex;justify-content:space-between;gap:20px;align-items:end;margin-bottom:20px}" +
            ".title{font-size:32px;font-weight:800}.sub{color:#8ea3c7;margin-top:8px}.card{background:#121b2e;border:1px solid #24324d;border-radius:18px;padding:20px;box-shadow:0 12px 40px rgba(0,0,0,.25)}" +
            ".pill{display:inline-block;padding:8px 12px;border-radius:999px;background:rgba(96,165,250,.08);border:1px solid #24324d;color:#cfe2ff;font-size:13px}" +
            ".actions{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}.btn{display:inline-block;padding:10px 14px;border-radius:12px;text-decoration:none;color:#eef4ff;background:#2563eb;border:1px solid #3b82f6;font-weight:600}" +
            ".btn.secondary{background:#121b2e}.section{margin-top:18px}.mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;background:#0a1322;border-radius:14px;padding:18px;border:1px solid #24324d;max-height:520px;overflow:auto}" +
            "</style></head><body><div class='wrap'><div class='hero'><div><div class='title'>Prometheus Metrics</div><div class='sub'>Readable entry page for exporters, dashboards, and quick operator checks.</div></div><div class='pill'>/metrics endpoint active</div></div>" +
            "<div class='card'><div><strong>Exporter status:</strong> running on <span class='mono'>" + escapeHtml(bindAddress + ":" + port) + "</span></div>" +
            "<div class='actions'><a class='btn' href='/metrics'>Open raw metrics</a><a class='btn secondary' href='https://prometheus.io/docs/instrumenting/exposition_formats/' target='_blank' rel='noreferrer'>Prometheus format reference</a></div>" +
            "<div class='section'><div class='sub'>Preview of the current metrics output:</div><div class='mono'>" + escapeHtml(metricsRegistry.scrape()) + "</div></div></div></div></body></html>";
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean isEnabled() {
        return enabled;
    }
}
