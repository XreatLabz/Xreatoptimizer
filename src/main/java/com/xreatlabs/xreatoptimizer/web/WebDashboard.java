package com.xreatlabs.xreatoptimizer.web;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import com.xreatlabs.xreatoptimizer.utils.MemoryUtils;
import com.xreatlabs.xreatoptimizer.utils.TPSUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.concurrent.Executors;

public class WebDashboard {

    private final XreatOptimizer plugin;
    private HttpServer server;
    private boolean running = false;

    private final LinkedList<DataPoint> recentHistory = new LinkedList<>();
    private static final int MAX_RECENT_HISTORY = 300;

    private final LinkedList<DataPoint> hourlyHistory = new LinkedList<>();
    private static final int MAX_HOURLY_HISTORY = 1440;

    private final LinkedList<DataPoint> dailyHistory = new LinkedList<>();
    private static final int MAX_DAILY_HISTORY = 720;

    private final LinkedList<LagSpikeRecord> lagSpikes = new LinkedList<>();
    private static final int MAX_LAG_SPIKES = 100;

    private final LinkedList<LogEntry> logEntries = new LinkedList<>();
    private static final int MAX_LOG_ENTRIES = 100;

    private volatile String cachedSystemJson = "{}";
    private String authToken = "";
    private boolean authEnabled = false;

    private final java.util.concurrent.ConcurrentHashMap<String, long[]> rateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int RATE_LIMIT_MAX = 60;
    private static final long RATE_LIMIT_WINDOW = 60_000;

    private BukkitTask recentHistoryTask;
    private BukkitTask hourlyAggregationTask;
    private BukkitTask dailyAggregationTask;
    private BukkitTask cachedSystemTask;

    public WebDashboard(XreatOptimizer plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("web_dashboard.enabled", false)) {
            LoggerUtils.debug("Web dashboard is disabled in config");
            return;
        }

        int port = plugin.getConfig().getInt("web_dashboard.port", 8080);
        String bindAddress = plugin.getConfig().getString("web_dashboard.bind_address", "127.0.0.1");

        authToken = plugin.getConfig().getString("web_dashboard.auth_token", "");
        authEnabled = !authToken.isEmpty();
        if (authEnabled) {
            LoggerUtils.info("Web dashboard authentication is enabled");
        } else {
            LoggerUtils.warn("Web dashboard has NO authentication. Set web_dashboard.auth_token in config.yml!");
        }

        try {
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            server.createContext("/", new DashboardHandler());
            server.createContext("/api/stats", new StatsApiHandler());
            server.createContext("/api/history", new HistoryApiHandler());
            server.createContext("/api/config", new ConfigApiHandler());
            server.createContext("/api/system", new SystemApiHandler());
            server.createContext("/api/logs", new LogsApiHandler());
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();

            running = true;
            cachedSystemJson = buildSystemJsonSync();
            startDataCollection();

            LoggerUtils.info("Web dashboard started on http://" + bindAddress + ":" + port);
        } catch (IOException e) {
            LoggerUtils.error("Failed to start web dashboard on port " + port, e);
        }
    }

    public void stop() {
        running = false;

        if (recentHistoryTask != null) recentHistoryTask.cancel();
        if (hourlyAggregationTask != null) hourlyAggregationTask.cancel();
        if (dailyAggregationTask != null) dailyAggregationTask.cancel();
        if (cachedSystemTask != null) cachedSystemTask.cancel();
        recentHistoryTask = null;
        hourlyAggregationTask = null;
        dailyAggregationTask = null;
        cachedSystemTask = null;

        if (server != null) {
            server.stop(0);
            server = null;
            LoggerUtils.info("Web dashboard stopped");
        }
    }

    private void startDataCollection() {
        recentHistoryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) return;

            DataPoint point = new DataPoint();
            point.timestamp = System.currentTimeMillis();
            point.tps = TPSUtils.getTPS();
            point.memoryPercent = MemoryUtils.getMemoryUsagePercentage();
            point.memoryUsed = MemoryUtils.getUsedMemoryMB();
            point.memoryMax = MemoryUtils.getMaxMemoryMB();
            point.entities = plugin.getPerformanceMonitor() != null ? plugin.getPerformanceMonitor().getCurrentEntityCount() : 0;
            point.chunks = plugin.getPerformanceMonitor() != null ? plugin.getPerformanceMonitor().getCurrentChunkCount() : 0;
            point.players = plugin.getPerformanceMonitor() != null ? plugin.getPerformanceMonitor().getCurrentPlayerCount() : Bukkit.getOnlinePlayers().size();

            synchronized (recentHistory) {
                recentHistory.addLast(point);
                while (recentHistory.size() > MAX_RECENT_HISTORY) {
                    recentHistory.removeFirst();
                }
            }
        }, 20L, 20L);

        hourlyAggregationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) return;
            aggregateToHourlyHistory();
        }, 1200L, 1200L);

        dailyAggregationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) return;
            aggregateToDailyHistory();
        }, 72000L, 72000L);

        cachedSystemTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) return;
            cachedSystemJson = buildSystemJsonSync();
        }, 20L, 40L);
    }

    private void aggregateToHourlyHistory() {
        synchronized (recentHistory) {
            if (recentHistory.isEmpty()) return;

            double avgTps = 0, avgMemory = 0;
            long avgMemoryUsed = 0, avgMemoryMax = 0;
            int avgEntities = 0, avgChunks = 0, avgPlayers = 0;
            int count = Math.min(60, recentHistory.size());

            for (int i = recentHistory.size() - count; i < recentHistory.size(); i++) {
                DataPoint p = recentHistory.get(i);
                avgTps += p.tps;
                avgMemory += p.memoryPercent;
                avgMemoryUsed += p.memoryUsed;
                avgMemoryMax += p.memoryMax;
                avgEntities += p.entities;
                avgChunks += p.chunks;
                avgPlayers += p.players;
            }

            DataPoint aggregated = new DataPoint();
            aggregated.timestamp = System.currentTimeMillis();
            aggregated.tps = avgTps / count;
            aggregated.memoryPercent = avgMemory / count;
            aggregated.memoryUsed = avgMemoryUsed / count;
            aggregated.memoryMax = avgMemoryMax / count;
            aggregated.entities = avgEntities / count;
            aggregated.chunks = avgChunks / count;
            aggregated.players = avgPlayers / count;

            synchronized (hourlyHistory) {
                hourlyHistory.addLast(aggregated);
                while (hourlyHistory.size() > MAX_HOURLY_HISTORY) {
                    hourlyHistory.removeFirst();
                }
            }
        }
    }

    private void aggregateToDailyHistory() {
        synchronized (hourlyHistory) {
            if (hourlyHistory.isEmpty()) return;

            double avgTps = 0, avgMemory = 0;
            long avgMemoryUsed = 0, avgMemoryMax = 0;
            int avgEntities = 0, avgChunks = 0, avgPlayers = 0;
            int count = Math.min(60, hourlyHistory.size());

            for (int i = hourlyHistory.size() - count; i < hourlyHistory.size(); i++) {
                DataPoint p = hourlyHistory.get(i);
                avgTps += p.tps;
                avgMemory += p.memoryPercent;
                avgMemoryUsed += p.memoryUsed;
                avgMemoryMax += p.memoryMax;
                avgEntities += p.entities;
                avgChunks += p.chunks;
                avgPlayers += p.players;
            }

            DataPoint aggregated = new DataPoint();
            aggregated.timestamp = System.currentTimeMillis();
            aggregated.tps = avgTps / count;
            aggregated.memoryPercent = avgMemory / count;
            aggregated.memoryUsed = avgMemoryUsed / count;
            aggregated.memoryMax = avgMemoryMax / count;
            aggregated.entities = avgEntities / count;
            aggregated.chunks = avgChunks / count;
            aggregated.players = avgPlayers / count;

            synchronized (dailyHistory) {
                dailyHistory.addLast(aggregated);
                while (dailyHistory.size() > MAX_DAILY_HISTORY) {
                    dailyHistory.removeFirst();
                }
            }
        }
    }

    public void recordLagSpike(double peakMs, String cause) {
        LagSpikeRecord record = new LagSpikeRecord();
        record.timestamp = System.currentTimeMillis();
        record.peakMs = peakMs;
        record.cause = cause;
        record.tps = TPSUtils.getTPS();

        synchronized (lagSpikes) {
            lagSpikes.addFirst(record);
            while (lagSpikes.size() > MAX_LAG_SPIKES) {
                lagSpikes.removeLast();
            }
        }
    }

    public void addLogEntry(String level, String message) {
        LogEntry entry = new LogEntry();
        entry.timestamp = System.currentTimeMillis();
        entry.level = level;
        entry.message = message;

        synchronized (logEntries) {
            logEntries.addFirst(entry);
            while (logEntries.size() > MAX_LOG_ENTRIES) {
                logEntries.removeLast();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange) || !checkRateLimit(exchange)) return;
            String response = generateDashboardHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class StatsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange) || !checkRateLimit(exchange)) return;
            String response = generateStatsJson();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class HistoryApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange) || !checkRateLimit(exchange)) return;
            String range = getQueryParam(exchange.getRequestURI().getQuery(), "range", "recent");
            String response = generateHistoryJson(range);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class ConfigApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange) || !checkRateLimit(exchange)) return;
            String response = generateConfigJson();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class SystemApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange) || !checkRateLimit(exchange)) return;
            String response = generateSystemJson();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class LogsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkAuth(exchange) || !checkRateLimit(exchange)) return;
            String response = generateLogsJson();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private boolean checkAuth(HttpExchange exchange) throws IOException {
        if (!authEnabled) return true;

        String token = null;
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        }
        if (token == null) {
            token = getQueryParam(exchange.getRequestURI().getQuery(), "token", null);
        }

        if (!authToken.equals(token)) {
            String body = "Unauthorized";
            exchange.sendResponseHeaders(401, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return false;
        }
        return true;
    }

    private boolean checkRateLimit(HttpExchange exchange) throws IOException {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        long[] bucket = rateLimitMap.computeIfAbsent(ip, k -> new long[]{now, 0});

        if (now - bucket[0] > RATE_LIMIT_WINDOW) {
            bucket[0] = now;
            bucket[1] = 1;
            return true;
        }

        bucket[1]++;
        if (bucket[1] > RATE_LIMIT_MAX) {
            String body = "{\"error\":\"Rate limit exceeded\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return false;
        }
        return true;
    }

    private String generateStatsJson() {
        double tps = TPSUtils.getTPS();
        double memory = MemoryUtils.getMemoryUsagePercentage();
        long memoryUsed = MemoryUtils.getUsedMemoryMB();
        long memoryMax = MemoryUtils.getMaxMemoryMB();
        int entities = plugin.getPerformanceMonitor() != null ? plugin.getPerformanceMonitor().getCurrentEntityCount() : 0;
        int chunks = plugin.getPerformanceMonitor() != null ? plugin.getPerformanceMonitor().getCurrentChunkCount() : 0;
        int players = plugin.getPerformanceMonitor() != null ? plugin.getPerformanceMonitor().getCurrentPlayerCount() : Bukkit.getOnlinePlayers().size();
        String profile = plugin.getOptimizationManager() != null ? plugin.getOptimizationManager().getCurrentProfile().name() : "AUTO";

        return String.format(
            "{\"tps\":%.2f,\"memory\":%.1f,\"memoryUsed\":%d,\"memoryMax\":%d," +
            "\"entities\":%d,\"chunks\":%d,\"players\":%d,\"profile\":\"%s\",\"timestamp\":%d}",
            tps, memory, memoryUsed, memoryMax, entities, chunks, players, profile, System.currentTimeMillis()
        );
    }

    private String generateHistoryJson(String range) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"history\":[");

        LinkedList<DataPoint> dataSource;
        synchronized (recentHistory) {
            switch (range) {
                case "hourly":
                    synchronized (hourlyHistory) {
                        dataSource = new LinkedList<>(hourlyHistory);
                    }
                    break;
                case "daily":
                    synchronized (dailyHistory) {
                        dataSource = new LinkedList<>(dailyHistory);
                    }
                    break;
                case "recent":
                default:
                    dataSource = new LinkedList<>(recentHistory);
                    break;
            }
        }

        boolean first = true;
        for (DataPoint point : dataSource) {
            if (!first) sb.append(",");
            first = false;
            sb.append(String.format("{\"t\":%d,\"tps\":%.2f,\"mem\":%.1f,\"ent\":%d,\"chunks\":%d,\"players\":%d}",
                point.timestamp, point.tps, point.memoryPercent, point.entities, point.chunks, point.players));
        }

        sb.append("],\"lagSpikes\":[");
        synchronized (lagSpikes) {
            first = true;
            for (LagSpikeRecord spike : lagSpikes) {
                if (!first) sb.append(",");
                first = false;
                sb.append(String.format("{\"t\":%d,\"peak\":%.1f,\"cause\":\"%s\",\"tps\":%.2f}",
                    spike.timestamp, spike.peakMs, escapeJson(spike.cause), spike.tps));
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String generateConfigJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"features\":{");
        sb.append("\"entity_limiter\":").append(plugin.getConfig().getBoolean("entity_limiter.enabled", false)).append(",");
        sb.append("\"auto_clear\":").append(plugin.getConfig().getBoolean("auto_clear.enabled", false)).append(",");
        sb.append("\"hibernate\":").append(plugin.getConfig().getBoolean("hibernate.enabled", false)).append(",");
        sb.append("\"redstone_hopper\":").append(plugin.getConfig().getBoolean("redstone_hopper_optimization.enabled", false)).append(",");
        sb.append("\"empty_server\":").append(plugin.getConfig().getBoolean("empty_server.enabled", true)).append(",");
        sb.append("\"stack_fusion\":").append(plugin.getConfig().getBoolean("enable_stack_fusion", true)).append(",");
        sb.append("\"notifications\":").append(plugin.getConfig().getBoolean("notifications.enabled", false)).append(",");
        sb.append("\"web_dashboard\":").append(plugin.getConfig().getBoolean("web_dashboard.enabled", false));
        sb.append("},\"thresholds\":{");
        sb.append("\"tps_light\":").append(plugin.getConfig().getDouble("optimization.tps_thresholds.light", 19.5)).append(",");
        sb.append("\"tps_normal\":").append(plugin.getConfig().getDouble("optimization.tps_thresholds.normal", 18.0)).append(",");
        sb.append("\"tps_aggressive\":").append(plugin.getConfig().getDouble("optimization.tps_thresholds.aggressive", 16.0)).append(",");
        sb.append("\"memory_reclaim\":").append(plugin.getConfig().getInt("memory_reclaim_threshold_percent", 80));
        sb.append("},\"limits\":{");
        sb.append("\"passive\":").append(plugin.getConfig().getInt("optimization.entity_limits.passive", 200)).append(",");
        sb.append("\"hostile\":").append(plugin.getConfig().getInt("optimization.entity_limits.hostile", 150)).append(",");
        sb.append("\"item\":").append(plugin.getConfig().getInt("optimization.entity_limits.item", 1000));
        sb.append("}};");
        return sb.toString().replace("};", "}");
    }

    private String generateSystemJson() {
        return cachedSystemJson;
    }

    private String buildSystemJsonSync() {
        Runtime runtime = Runtime.getRuntime();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"java_version\":\"").append(escapeJson(System.getProperty("java.version"))).append("\",");
        sb.append("\"java_vendor\":\"").append(escapeJson(System.getProperty("java.vendor"))).append("\",");
        sb.append("\"os\":\"").append(escapeJson(System.getProperty("os.name"))).append("\",");
        sb.append("\"os_arch\":\"").append(escapeJson(System.getProperty("os.arch"))).append("\",");
        sb.append("\"processors\":").append(runtime.availableProcessors()).append(",");
        sb.append("\"server_version\":\"").append(escapeJson(Bukkit.getVersion())).append("\",");
        sb.append("\"bukkit_version\":\"").append(escapeJson(Bukkit.getBukkitVersion())).append("\",");
        sb.append("\"server_name\":\"").append(escapeJson(Bukkit.getServer().getName())).append("\",");
        sb.append("\"max_players\":").append(Bukkit.getMaxPlayers()).append(",");
        sb.append("\"worlds\":[");
        boolean first = true;
        for (World world : Bukkit.getWorlds()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(escapeJson(world.getName())).append("\",");
            sb.append("\"entities\":").append(world.getEntities().size()).append(",");
            sb.append("\"chunks\":").append(world.getLoadedChunks().length).append(",");
            sb.append("\"players\":").append(world.getPlayers().size()).append("}");
        }
        sb.append("],\"plugins\":[");
        first = true;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(escapeJson(p.getName())).append("\",");
            sb.append("\"version\":\"").append(escapeJson(p.getDescription().getVersion())).append("\",");
            sb.append("\"enabled\":").append(p.isEnabled()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String generateLogsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"logs\":[");
        synchronized (logEntries) {
            boolean first = true;
            for (LogEntry entry : logEntries) {
                if (!first) sb.append(",");
                first = false;
                sb.append(String.format("{\"t\":%d,\"level\":\"%s\",\"msg\":\"%s\"}",
                    entry.timestamp, entry.level, escapeJson(entry.message)));
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String generateDashboardHtml() {
        String version = plugin.getDescription().getVersion();
        String serverName = escapeJson(Bukkit.getServer().getName());
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>XreatOptimizer Dashboard</title>" +
            "<style>" +
            ":root{--bg:#0b1220;--panel:#121b2e;--muted:#8ea3c7;--text:#eef4ff;--line:#24324d;--good:#22c55e;--warn:#f59e0b;--bad:#ef4444;--accent:#60a5fa;}" +
            "*{box-sizing:border-box}body{margin:0;font-family:Inter,Segoe UI,Arial,sans-serif;background:linear-gradient(180deg,#09101d,#0f172a);color:var(--text)}" +
            ".wrap{max-width:1200px;margin:0 auto;padding:32px 20px 48px}.hero{display:flex;justify-content:space-between;gap:24px;align-items:flex-end;margin-bottom:24px}" +
            ".title{font-size:34px;font-weight:800;letter-spacing:.2px}.subtitle{color:var(--muted);margin-top:8px}.pill{display:inline-block;padding:8px 12px;border:1px solid var(--line);background:rgba(96,165,250,.08);border-radius:999px;color:#cfe2ff;font-size:13px}" +
            ".grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.card{background:rgba(18,27,46,.88);border:1px solid var(--line);border-radius:18px;padding:18px;box-shadow:0 10px 40px rgba(0,0,0,.22)}" +
            ".label{font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:var(--muted)}.value{font-size:32px;font-weight:800;margin-top:8px}.sub{margin-top:6px;color:var(--muted);font-size:13px}" +
            ".good{color:var(--good)}.warn{color:var(--warn)}.bad{color:var(--bad)}.section{margin-top:18px}.section-title{font-size:18px;font-weight:700;margin:0 0 12px}" +
            ".panel-grid{display:grid;grid-template-columns:2fr 1fr;gap:16px}.history{min-height:320px}.chart{height:240px;display:flex;align-items:flex-end;gap:4px;padding-top:16px}.bar{flex:1;border-radius:6px 6px 0 0;background:linear-gradient(180deg,#60a5fa,#2563eb);opacity:.95;min-width:2px}" +
            ".list{display:grid;gap:10px}.row{display:flex;justify-content:space-between;gap:16px;padding:10px 0;border-bottom:1px solid rgba(255,255,255,.05)}.row:last-child{border-bottom:none}" +
            ".mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}.small{font-size:12px;color:var(--muted)}.footer{margin-top:18px;color:var(--muted);font-size:13px}" +
            "@media(max-width:980px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.panel-grid{grid-template-columns:1fr}}@media(max-width:620px){.grid{grid-template-columns:1fr}.hero{flex-direction:column;align-items:flex-start}.title{font-size:28px}}" +
            "</style></head><body>" +
            "<div class='wrap'>" +
            "<div class='hero'><div><div class='title'>XreatOptimizer Dashboard</div><div class='subtitle'>Live server performance, history, and feature visibility.</div></div><div class='pill'>Version " + escapeJson(version) + " • " + serverName + "</div></div>" +
            "<div class='grid'>" +
            "<div class='card'><div class='label'>TPS</div><div id='tps' class='value'>--</div><div id='tpsSub' class='sub'>Loading…</div></div>" +
            "<div class='card'><div class='label'>Memory</div><div id='memory' class='value'>--</div><div id='memorySub' class='sub'>Loading…</div></div>" +
            "<div class='card'><div class='label'>Entities</div><div id='entities' class='value'>--</div><div class='sub'>Current total across loaded worlds</div></div>" +
            "<div class='card'><div class='label'>Chunks</div><div id='chunks' class='value'>--</div><div id='playersSub' class='sub'>Players: --</div></div>" +
            "</div>" +
            "<div class='panel-grid section'>" +
            "<div class='card history'><h3 class='section-title'>Recent TPS history</h3><div id='chart' class='chart'></div><div class='small'>Bars show the most recent samples from /api/history.</div></div>" +
            "<div class='card'><h3 class='section-title'>Current profile</h3><div id='profile' class='value'>--</div><div class='sub'>Active optimization profile</div><div class='section'><h3 class='section-title'>Endpoints</h3><div class='list small mono'><div>/api/stats</div><div>/api/history?range=recent</div><div>/api/config</div><div>/api/system</div><div>/api/logs</div></div></div></div>" +
            "</div>" +
            "<div class='panel-grid section'>" +
            "<div class='card'><h3 class='section-title'>System overview</h3><div id='system' class='list small'>Loading…</div></div>" +
            "<div class='card'><h3 class='section-title'>Feature flags</h3><div id='features' class='list small'>Loading…</div></div>" +
            "</div>" +
            "<div class='panel-grid section'>" +
            "<div class='card'><h3 class='section-title'>Recent lag spikes</h3><div id='spikes' class='list small'>Loading…</div></div>" +
            "<div class='card'><h3 class='section-title'>Latest logs</h3><div id='logs' class='list small'>Loading…</div></div>" +
            "</div>" +
            "<div class='footer'>This dashboard is read-only and built from the plugin's live monitoring data.</div>" +
            "</div>" +
            "<script>" +
            "const qs=location.search||'';const auth=qs?qs:'';const withToken=u=>u+auth;" +
            "const fmt=n=>new Intl.NumberFormat().format(n);const fmtTime=t=>new Date(t).toLocaleTimeString();" +
            "function setStatus(el,v,good,warn){el.textContent=v;el.className='value '+(v>=good?'good':v>=warn?'warn':'bad')}" +
            "async function loadStats(){const r=await fetch(withToken('/api/stats'));const d=await r.json();setStatus(document.getElementById('tps'),d.tps,19,15);document.getElementById('tpsSub').textContent='Profile '+d.profile;document.getElementById('memory').textContent=d.memory.toFixed(1)+'%';document.getElementById('memory').className='value '+(d.memory<70?'good':d.memory<85?'warn':'bad');document.getElementById('memorySub').textContent=fmt(d.memoryUsed)+' MB / '+fmt(d.memoryMax)+' MB';document.getElementById('entities').textContent=fmt(d.entities);document.getElementById('chunks').textContent=fmt(d.chunks);document.getElementById('playersSub').textContent='Players: '+fmt(d.players);document.getElementById('profile').textContent=d.profile;}" +
            "async function loadHistory(){const r=await fetch(withToken('/api/history?range=recent'));const d=await r.json();const chart=document.getElementById('chart');chart.innerHTML='';const points=(d.history||[]).slice(-50);for(const p of points){const h=Math.max(8,Math.min(220,(p.tps/20)*220));const bar=document.createElement('div');bar.className='bar';bar.style.height=h+'px';bar.title='TPS '+p.tps.toFixed(2)+' @ '+fmtTime(p.t);chart.appendChild(bar);}const spikes=document.getElementById('spikes');spikes.innerHTML='';const recentSpikes=(d.lagSpikes||[]).slice(0,5);if(!recentSpikes.length){spikes.innerHTML='<div class=small>No recent lag spikes recorded.</div>';}else{for(const s of recentSpikes){const row=document.createElement('div');row.className='row';row.innerHTML='<div><strong>'+s.peak.toFixed(1)+'ms</strong><div class=small>'+s.cause+'</div></div><div class=small>'+fmtTime(s.t)+'</div>';spikes.appendChild(row);}}}" +
            "async function loadConfig(){const r=await fetch(withToken('/api/config'));const d=await r.json();const f=document.getElementById('features');f.innerHTML='';for(const [k,v] of Object.entries(d.features||{})){const row=document.createElement('div');row.className='row';row.innerHTML='<div>'+k.replace(/_/g,' ')+'</div><div class='+(v?'good':'small')+'>'+(v?'enabled':'disabled')+'</div>';f.appendChild(row);}}" +
            "async function loadSystem(){const r=await fetch(withToken('/api/system'));const d=await r.json();const el=document.getElementById('system');el.innerHTML='';const items=[['Java',d.java_version],['OS',d.os+' ('+d.os_arch+')'],['Processors',d.processors],['Server',d.server_name],['Bukkit',d.bukkit_version],['Max players',d.max_players]];for(const [k,v] of items){const row=document.createElement('div');row.className='row';row.innerHTML='<div>'+k+'</div><div>'+v+'</div>';el.appendChild(row);}}" +
            "async function loadLogs(){const r=await fetch(withToken('/api/logs'));const d=await r.json();const el=document.getElementById('logs');el.innerHTML='';const logs=(d.logs||[]).slice(0,6);if(!logs.length){el.innerHTML='<div class=small>No dashboard logs available.</div>';return;}for(const l of logs){const row=document.createElement('div');row.className='row';row.innerHTML='<div><strong>'+l.level+'</strong><div class=small>'+l.msg+'</div></div><div class=small>'+fmtTime(l.t)+'</div>';el.appendChild(row);}}" +
            "async function refresh(){try{await Promise.all([loadStats(),loadHistory(),loadConfig(),loadSystem(),loadLogs()]);}catch(e){console.error(e);}}refresh();setInterval(refresh,4000);" +
            "</script></body></html>";
    }

    private String getQueryParam(String query, String key, String defaultValue) {
        if (query == null || query.isEmpty()) return defaultValue;
        String[] parts = query.split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                return kv[1];
            }
        }
        return defaultValue;
    }

    private static class DataPoint {
        long timestamp;
        double tps;
        double memoryPercent;
        long memoryUsed;
        long memoryMax;
        int entities;
        int chunks;
        int players;
    }

    private static class LagSpikeRecord {
        long timestamp;
        double peakMs;
        String cause;
        double tps;
    }

    private static class LogEntry {
        long timestamp;
        String level;
        String message;
    }
}
