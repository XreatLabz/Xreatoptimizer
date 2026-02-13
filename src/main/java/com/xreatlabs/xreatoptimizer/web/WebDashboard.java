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

    // Extended historical data storage
    private final LinkedList<DataPoint> recentHistory = new LinkedList<>();  // Last 5 minutes (1s intervals)
    private static final int MAX_RECENT_HISTORY = 300;

    private final LinkedList<DataPoint> hourlyHistory = new LinkedList<>();  // Last 24 hours (1min intervals)
    private static final int MAX_HOURLY_HISTORY = 1440;

    private final LinkedList<DataPoint> dailyHistory = new LinkedList<>();   // Last 30 days (1h intervals)
    private static final int MAX_DAILY_HISTORY = 720;

    private final LinkedList<LagSpikeRecord> lagSpikes = new LinkedList<>();
    private static final int MAX_LAG_SPIKES = 100;

    private final LinkedList<LogEntry> logEntries = new LinkedList<>();
    private static final int MAX_LOG_ENTRIES = 100;
    
    // Cached world data (updated from main thread to avoid async issues)
    private volatile String cachedSystemJson = "{}";
    
    // Authentication
    private String authToken = "";
    private boolean authEnabled = false;
    
    // Rate limiting
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> rateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int RATE_LIMIT_MAX = 60; // requests per window
    private static final long RATE_LIMIT_WINDOW = 60_000; // 1 minute
    
    public WebDashboard(XreatOptimizer plugin) {
        this.plugin = plugin;
    }
    
    public void start() {
        if (!plugin.getConfig().getBoolean("web_dashboard.enabled", false)) {
            LoggerUtils.debug("Web dashboard is disabled in config");
            return;
        }
        
        int port = plugin.getConfig().getInt("web_dashboard.port", 8080);
        String bindAddress = plugin.getConfig().getString("web_dashboard.bind_address", "0.0.0.0");
        
        // Load authentication settings
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
        if (server != null) {
            server.stop(0);
            running = false;
            LoggerUtils.info("Web dashboard stopped");
        }
    }
    
    private void startDataCollection() {
        // Collect data every second for recent history
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!running) return;

            DataPoint point = new DataPoint();
            point.timestamp = System.currentTimeMillis();
            point.tps = TPSUtils.getTPS();
            point.memoryPercent = MemoryUtils.getMemoryUsagePercentage();
            point.memoryUsed = MemoryUtils.getUsedMemoryMB();
            point.memoryMax = MemoryUtils.getMaxMemoryMB();
            point.entities = plugin.getPerformanceMonitor() != null ? plugin.getPerformanceMonitor().getCurrentEntityCount() : 0;
            point.chunks = plugin.getPerformanceMonitor() != null ? plugin.getPerformanceMonitor().getCurrentChunkCount() : 0;
            point.players = Bukkit.getOnlinePlayers().size();

            synchronized (recentHistory) {
                recentHistory.addLast(point);
                while (recentHistory.size() > MAX_RECENT_HISTORY) {
                    recentHistory.removeFirst();
                }
            }
        }, 20L, 20L); // Every second

        // Aggregate to hourly history every minute
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!running) return;
            aggregateToHourlyHistory();
        }, 1200L, 1200L); // Every minute

        // Aggregate to daily history every hour
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!running) return;
            aggregateToDailyHistory();
        }, 72000L, 72000L); // Every hour

        // Sync task to update cached system JSON (must run on main thread for world.getEntities())
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running) return;
            cachedSystemJson = buildSystemJsonSync();
        }, 20L, 40L);
    }

    private void aggregateToHourlyHistory() {
        synchronized (recentHistory) {
            if (recentHistory.isEmpty()) return;

            // Average the last minute of data
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

            // Average the last hour of data
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
            if (!checkRateLimit(exchange)) return;
            if (!checkAuth(exchange)) return;
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
            if (!checkRateLimit(exchange)) return;
            if (!checkAuth(exchange)) return;
            sendJsonResponse(exchange, generateStatsJson());
        }
    }
    
    private class HistoryApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkRateLimit(exchange)) return;
            if (!checkAuth(exchange)) return;

            // Parse query parameter for time range
            String query = exchange.getRequestURI().getQuery();
            String range = "recent"; // default
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && "range".equals(kv[0])) {
                        range = kv[1];
                        break;
                    }
                }
            }

            sendJsonResponse(exchange, generateHistoryJson(range));
        }
    }
    
    private class ConfigApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkRateLimit(exchange)) return;
            if (!checkAuth(exchange)) return;
            sendJsonResponse(exchange, generateConfigJson());
        }
    }
    
    private class SystemApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkRateLimit(exchange)) return;
            if (!checkAuth(exchange)) return;
            sendJsonResponse(exchange, generateSystemJson());
        }
    }
    
    private class LogsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!checkRateLimit(exchange)) return;
            if (!checkAuth(exchange)) return;
            sendJsonResponse(exchange, generateLogsJson());
        }
    }
    
    private void sendJsonResponse(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        // Restrict CORS to same-origin by default
        String allowedOrigin = plugin.getConfig().getString("web_dashboard.cors_origin", "");
        if (!allowedOrigin.isEmpty()) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        }
        exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private boolean checkAuth(HttpExchange exchange) throws IOException {
        if (!authEnabled) return true;
        
        // Check query parameter ?token=xxx
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0]) && authToken.equals(kv[1])) {
                    return true;
                }
            }
        }
        
        // Check Authorization header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.equals("Bearer " + authToken)) {
            return true;
        }
        
        String body = "{\"error\":\"Unauthorized. Provide ?token=YOUR_TOKEN or Authorization: Bearer YOUR_TOKEN\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(401, body.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return false;
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
        int players = Bukkit.getOnlinePlayers().size();
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
            first = true; // Reset for lag spikes loop
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
        sb.append("}}");
        return sb.toString();
    }
    
    private String generateSystemJson() {
        // Return cached data to avoid async thread issues with world.getEntities()
        return cachedSystemJson;
    }
    
    // Called from main thread via scheduler to safely access world entities
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
        String serverName = Bukkit.getServer().getName();
        int maxPlayers = Bukkit.getMaxPlayers();
        
        return "<!DOCTYPE html>\n" +
"<html lang=\"en\">\n" +
"<head>\n" +
"    <meta charset=\"UTF-8\">\n" +
"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
"    <title>XreatOptimizer Dashboard</title>\n" +
"    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap\" rel=\"stylesheet\">\n" +
"    <style>\n" +
"        :root{--bg:#000;--card:#0a0a0a;--border:rgba(255,255,255,0.04);--text:#fff;--muted:#666;--green:#00ff88;--cyan:#00d4ff;--purple:#a855f7;--red:#ff3366;--orange:#ff9500}\n" +
"        *{margin:0;padding:0;box-sizing:border-box}\n" +
"        body{font-family:'Inter',sans-serif;background:var(--bg);color:var(--text);min-height:100vh}\n" +
"        .dash{display:grid;grid-template-columns:260px 1fr;min-height:100vh}\n" +
"        .side{background:var(--card);border-right:1px solid var(--border);padding:24px;display:flex;flex-direction:column}\n" +
"        .logo{display:flex;align-items:center;gap:12px;margin-bottom:32px}\n" +
"        .block{width:44px;height:44px;position:relative;transform-style:preserve-3d;transform:rotateX(-15deg) rotateY(-45deg);animation:float 3s ease-in-out infinite}\n" +
"        @keyframes float{0%,100%{transform:rotateX(-15deg) rotateY(-45deg) translateY(0)}50%{transform:rotateX(-15deg) rotateY(-45deg) translateY(-3px)}}\n" +
"        .block-face{position:absolute;width:44px;height:44px;backface-visibility:hidden}\n" +
"        .block-top{background:linear-gradient(135deg,#7bc44f 0%,#5a9c3a 50%,#4a8530 100%);transform:rotateX(90deg) translateZ(22px);box-shadow:inset 0 0 10px rgba(0,0,0,0.2)}\n" +
"        .block-front{background:linear-gradient(180deg,#5a9c3a 0%,#5a9c3a 25%,#8b6914 25%,#6b5010 100%);transform:translateZ(22px)}\n" +
"        .block-right{background:linear-gradient(180deg,#4a8530 0%,#4a8530 25%,#7a5a12 25%,#5a4010 100%);transform:rotateY(90deg) translateZ(22px)}\n" +
"        .block-glow{position:absolute;width:60px;height:60px;top:-8px;left:-8px;background:radial-gradient(circle,rgba(123,196,79,0.3) 0%,transparent 70%);border-radius:50%;animation:glow 2s ease-in-out infinite}\n" +
"        @keyframes glow{0%,100%{opacity:0.5}50%{opacity:1}}\n" +
"        .logo-text{font-size:1.1rem;font-weight:700;background:linear-gradient(135deg,#fff,#888);-webkit-background-clip:text;-webkit-text-fill-color:transparent}\n" +
"        .info{background:rgba(255,255,255,0.02);border-radius:12px;padding:16px;margin-bottom:24px}\n" +
"        .status{display:flex;align-items:center;gap:8px;margin-bottom:12px}\n" +
"        .dot{width:8px;height:8px;background:var(--green);border-radius:50%;animation:pulse 2s infinite}\n" +
"        @keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(0,255,136,0.4)}50%{box-shadow:0 0 0 6px rgba(0,255,136,0)}}\n" +
"        .badge{padding:4px 10px;background:rgba(0,255,136,0.1);border-radius:12px;font-size:0.7rem;font-weight:600;color:var(--green)}\n" +
"        .detail{display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid var(--border);font-size:0.8rem}\n" +
"        .detail:last-child{border:none}\n" +
"        .detail-label{color:var(--muted)}\n" +
"        .detail-value{font-family:'JetBrains Mono',monospace;color:#888}\n" +
"        .nav-section{margin-bottom:20px}\n" +
"        .nav-title{font-size:0.65rem;text-transform:uppercase;letter-spacing:0.1em;color:#444;margin-bottom:8px;padding-left:10px}\n" +
"        .nav-item{display:flex;align-items:center;gap:10px;padding:10px;border-radius:8px;color:#888;cursor:pointer;font-size:0.85rem;transition:all 0.2s}\n" +
"        .nav-item:hover{background:rgba(255,255,255,0.03);color:var(--text)}\n" +
"        .nav-item.active{background:rgba(168,85,247,0.1);color:var(--purple)}\n" +
"        .main{padding:24px;overflow-y:auto}\n" +
"        .header{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}\n" +
"        .title{font-size:1.5rem;font-weight:700}\n" +
"        .time{font-size:0.8rem;color:var(--muted);font-family:'JetBrains Mono',monospace}\n" +
"        .page{display:none}\n" +
"        .page.active{display:block}\n" +
"        .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:24px}\n" +
"        .card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:20px;transition:all 0.2s}\n" +
"        .card:hover{border-color:rgba(168,85,247,0.2);transform:translateY(-2px)}\n" +
"        .card-header{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:12px}\n" +
"        .card-label{font-size:0.7rem;color:var(--muted);text-transform:uppercase;letter-spacing:0.05em}\n" +
"        .card-icon{width:32px;height:32px;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:14px}\n" +
"        .card-value{font-size:1.75rem;font-weight:800;font-family:'JetBrains Mono',monospace;margin-bottom:8px}\n" +
"        .good{color:var(--green)}.warn{color:var(--orange)}.crit{color:var(--red)}.info{color:var(--cyan)}\n" +
"        .bar{height:4px;background:rgba(255,255,255,0.05);border-radius:2px;overflow:hidden}\n" +
"        .bar-fill{height:100%;border-radius:2px;transition:width 0.5s}\n" +
"        .charts{display:grid;grid-template-columns:repeat(2,1fr);gap:16px;margin-bottom:24px}\n" +
"        .chart-card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:20px}\n" +
"        .chart-card.full{grid-column:span 2}\n" +
"        .chart-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}\n" +
"        .chart-title{font-size:0.9rem;font-weight:600}\n" +
"        .legend{display:flex;gap:12px;font-size:0.7rem;color:var(--muted)}\n" +
"        .legend-item{display:flex;align-items:center;gap:4px}\n" +
"        .legend-dot{width:6px;height:6px;border-radius:50%}\n" +
"        canvas{width:100%!important;height:160px!important}\n" +
"        .events{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:20px}\n" +
"        .event{display:flex;align-items:center;gap:12px;padding:12px;background:rgba(255,51,102,0.03);border-radius:8px;margin-bottom:8px;border-left:3px solid var(--red)}\n" +
"        .event-icon{width:36px;height:36px;background:rgba(255,51,102,0.1);border-radius:8px;display:flex;align-items:center;justify-content:center;color:var(--red)}\n" +
"        .event-content{flex:1}\n" +
"        .event-title{font-weight:600;font-size:0.9rem;margin-bottom:2px}\n" +
"        .event-details{font-size:0.8rem;color:var(--muted)}\n" +
"        .event-time{font-size:0.75rem;color:var(--muted);font-family:'JetBrains Mono',monospace}\n" +
"        .no-data{text-align:center;padding:40px;color:var(--muted)}\n" +
"        .no-data-icon{font-size:40px;margin-bottom:12px;opacity:0.3}\n" +
"        .table{width:100%;border-collapse:collapse}\n" +
"        .table th,.table td{padding:12px;text-align:left;border-bottom:1px solid var(--border)}\n" +
"        .table th{font-size:0.7rem;text-transform:uppercase;color:var(--muted);font-weight:600}\n" +
"        .table td{font-size:0.85rem}\n" +
"        .toggle{display:inline-flex;align-items:center;padding:4px 10px;border-radius:12px;font-size:0.75rem;font-weight:600}\n" +
"        .toggle.on{background:rgba(0,255,136,0.1);color:var(--green)}\n" +
"        .toggle.off{background:rgba(255,51,102,0.1);color:var(--red)}\n" +
"        .log-entry{padding:8px 12px;border-radius:6px;margin-bottom:4px;font-family:'JetBrains Mono',monospace;font-size:0.8rem;background:rgba(255,255,255,0.02)}\n" +
"        .log-entry.info{border-left:3px solid var(--cyan)}\n" +
"        .log-entry.warn{border-left:3px solid var(--orange)}\n" +
"        .log-entry.error{border-left:3px solid var(--red)}\n" +
"        .log-time{color:var(--muted);margin-right:12px}\n" +
"        .log-level{font-weight:600;margin-right:8px}\n" +
"        .section{margin-bottom:24px}\n" +
"        .section-title{font-size:1rem;font-weight:600;margin-bottom:16px;display:flex;align-items:center;gap:8px}\n" +
"        .perf-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}\n" +
"        @media(max-width:1200px){.grid{grid-template-columns:repeat(2,1fr)}.charts{grid-template-columns:1fr}.chart-card.full{grid-column:span 1}.perf-grid{grid-template-columns:repeat(2,1fr)}}\n" +
"        @media(max-width:768px){.dash{grid-template-columns:1fr}.side{display:none}.grid{grid-template-columns:1fr}.perf-grid{grid-template-columns:1fr}}\n" +
"    </style>\n" +
"</head>\n" +
"<body>\n" +
"<div class=\"dash\">\n" +
"    <aside class=\"side\">\n" +
"        <div class=\"logo\">\n" +
"            <div class=\"block\">\n" +
"                <div class=\"block-glow\"></div>\n" +
"                <div class=\"block-face block-top\"></div>\n" +
"                <div class=\"block-face block-front\"></div>\n" +
"                <div class=\"block-face block-right\"></div>\n" +
"            </div>\n" +
"            <span class=\"logo-text\">XreatOptimizer</span>\n" +
"        </div>\n" +
"        <div class=\"info\">\n" +
"            <div class=\"status\"><div class=\"dot\"></div><span class=\"badge\">Online</span></div>\n" +
"            <div class=\"detail\"><span class=\"detail-label\">Version</span><span class=\"detail-value\">v" + version + "</span></div>\n" +
"            <div class=\"detail\"><span class=\"detail-label\">Server</span><span class=\"detail-value\">" + serverName + "</span></div>\n" +
"            <div class=\"detail\"><span class=\"detail-label\">Max Players</span><span class=\"detail-value\">" + maxPlayers + "</span></div>\n" +
"        </div>\n" +
"        <div class=\"nav-section\">\n" +
"            <div class=\"nav-title\">Monitoring</div>\n" +
"            <div class=\"nav-item active\" onclick=\"showPage('dashboard')\"><span>📊</span>Dashboard</div>\n" +
"            <div class=\"nav-item\" onclick=\"showPage('performance')\"><span>⚡</span>Performance</div>\n" +
"            <div class=\"nav-item\" onclick=\"showPage('alerts')\"><span>🔔</span>Alerts</div>\n" +
"        </div>\n" +
"        <div class=\"nav-section\">\n" +
"            <div class=\"nav-title\">System</div>\n" +
"            <div class=\"nav-item\" onclick=\"showPage('settings')\"><span>⚙️</span>Settings</div>\n" +
"            <div class=\"nav-item\" onclick=\"showPage('logs')\"><span>📋</span>Logs</div>\n" +
"        </div>\n" +
"    </aside>\n" +
"    <main class=\"main\">\n" +
"        <!-- Dashboard Page -->\n" +
"        <div id=\"page-dashboard\" class=\"page active\">\n" +
"            <div class=\"header\"><h1 class=\"title\">Dashboard</h1><span class=\"time\" id=\"lastUpdate\">Updated just now</span></div>\n" +
"            <div class=\"grid\">\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">TPS</span><div class=\"card-icon\" style=\"background:rgba(0,255,136,0.1)\">⚡</div></div><div class=\"card-value\" id=\"tps\">--</div><div class=\"bar\"><div class=\"bar-fill\" id=\"tps-bar\" style=\"width:0%;background:var(--green)\"></div></div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">Memory</span><div class=\"card-icon\" style=\"background:rgba(0,212,255,0.1)\">💾</div></div><div class=\"card-value\" id=\"memory\">--</div><div class=\"bar\"><div class=\"bar-fill\" id=\"mem-bar\" style=\"width:0%;background:var(--cyan)\"></div></div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">Entities</span><div class=\"card-icon\" style=\"background:rgba(168,85,247,0.1)\">🎯</div></div><div class=\"card-value info\" id=\"entities\">--</div><div class=\"bar\"><div class=\"bar-fill\" id=\"ent-bar\" style=\"width:0%;background:var(--purple)\"></div></div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">Players</span><div class=\"card-icon\" style=\"background:rgba(255,149,0,0.1)\">👥</div></div><div class=\"card-value info\" id=\"players\">--</div><div class=\"bar\"><div class=\"bar-fill\" id=\"play-bar\" style=\"width:0%;background:var(--orange)\"></div></div></div>\n" +
"            </div>\n" +
"            <div class=\"charts\">\n" +
"                <div class=\"chart-card full\"><div class=\"chart-header\"><span class=\"chart-title\">Server Performance</span><div class=\"legend\"><div class=\"legend-item\"><div class=\"legend-dot\" style=\"background:var(--green)\"></div>TPS</div><div class=\"legend-item\"><div class=\"legend-dot\" style=\"background:var(--cyan)\"></div>Memory</div></div></div><canvas id=\"tpsChart\"></canvas></div>\n" +
"                <div class=\"chart-card\"><div class=\"chart-header\"><span class=\"chart-title\">Entity Count</span></div><canvas id=\"entityChart\"></canvas></div>\n" +
"                <div class=\"chart-card\"><div class=\"chart-header\"><span class=\"chart-title\">Chunk Load</span></div><canvas id=\"chunkChart\"></canvas></div>\n" +
"            </div>\n" +
"            <div class=\"events\"><div class=\"chart-header\"><span class=\"chart-title\">Recent Events</span></div><div id=\"lagSpikes\"><div class=\"no-data\"><div class=\"no-data-icon\">✓</div><div>No lag spikes detected</div></div></div></div>\n" +
"        </div>\n" +
"        <!-- Performance Page -->\n" +
"        <div id=\"page-performance\" class=\"page\">\n" +
"            <div class=\"header\"><h1 class=\"title\">Performance</h1><span class=\"time\" id=\"perfUpdate\">Updated just now</span></div>\n" +
"            <div class=\"section\"><div class=\"section-title\">⚡ Current Status</div>\n" +
"            <div class=\"perf-grid\">\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">TPS (Current)</span></div><div class=\"card-value\" id=\"perf-tps\">--</div><div style=\"font-size:0.8rem;color:var(--muted)\">Target: 20.00</div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">Heap Memory</span></div><div class=\"card-value\" id=\"perf-heap\">--</div><div style=\"font-size:0.8rem;color:var(--muted)\" id=\"perf-heap-detail\">0 / 0 MB</div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">Profile</span></div><div class=\"card-value info\" id=\"perf-profile\">--</div><div style=\"font-size:0.8rem;color:var(--muted)\">Optimization Mode</div></div>\n" +
"            </div></div>\n" +
"            <div class=\"section\"><div class=\"section-title\">🌍 World Statistics</div>\n" +
"            <div class=\"card\"><table class=\"table\"><thead><tr><th>World</th><th>Entities</th><th>Chunks</th><th>Players</th></tr></thead><tbody id=\"world-stats\"></tbody></table></div></div>\n" +
"            <div class=\"section\"><div class=\"section-title\">🔌 Loaded Plugins</div>\n" +
"            <div class=\"card\"><table class=\"table\"><thead><tr><th>Plugin</th><th>Version</th><th>Status</th></tr></thead><tbody id=\"plugin-list\"></tbody></table></div></div>\n" +
"        </div>\n" +
"        <!-- Alerts Page -->\n" +
"        <div id=\"page-alerts\" class=\"page\">\n" +
"            <div class=\"header\"><h1 class=\"title\">Alerts</h1><span class=\"time\">Alert History</span></div>\n" +
"            <div class=\"section\"><div class=\"section-title\">⚠️ Lag Spike History</div>\n" +
"            <div class=\"events\" id=\"alert-spikes\"><div class=\"no-data\"><div class=\"no-data-icon\">✓</div><div>No lag spikes recorded</div></div></div></div>\n" +
"            <div class=\"section\"><div class=\"section-title\">📊 Alert Thresholds</div>\n" +
"            <div class=\"perf-grid\">\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">TPS Light</span></div><div class=\"card-value good\" id=\"thresh-light\">--</div><div style=\"font-size:0.8rem;color:var(--muted)\">Minor optimizations</div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">TPS Normal</span></div><div class=\"card-value warn\" id=\"thresh-normal\">--</div><div style=\"font-size:0.8rem;color:var(--muted)\">Standard optimizations</div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">TPS Aggressive</span></div><div class=\"card-value crit\" id=\"thresh-aggressive\">--</div><div style=\"font-size:0.8rem;color:var(--muted)\">Emergency optimizations</div></div>\n" +
"            </div></div>\n" +
"        </div>\n" +
"        <!-- Settings Page -->\n" +
"        <div id=\"page-settings\" class=\"page\">\n" +
"            <div class=\"header\"><h1 class=\"title\">Settings</h1><span class=\"time\">Configuration Status</span></div>\n" +
"            <div class=\"section\"><div class=\"section-title\">🎛️ Feature Status</div>\n" +
"            <div class=\"card\"><table class=\"table\"><thead><tr><th>Feature</th><th>Status</th><th>Description</th></tr></thead><tbody id=\"feature-list\"></tbody></table></div></div>\n" +
"            <div class=\"section\"><div class=\"section-title\">💻 System Information</div>\n" +
"            <div class=\"perf-grid\">\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">Java Version</span></div><div class=\"card-value info\" style=\"font-size:1.25rem\" id=\"sys-java\">--</div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">Server Software</span></div><div class=\"card-value info\" style=\"font-size:1.25rem\" id=\"sys-server\">--</div></div>\n" +
"                <div class=\"card\"><div class=\"card-header\"><span class=\"card-label\">CPU Cores</span></div><div class=\"card-value info\" id=\"sys-cpu\">--</div></div>\n" +
"            </div></div>\n" +
"        </div>\n" +
"        <!-- Logs Page -->\n" +
"        <div id=\"page-logs\" class=\"page\">\n" +
"            <div class=\"header\"><h1 class=\"title\">Logs</h1><span class=\"time\" id=\"log-count\">0 entries</span></div>\n" +
"            <div class=\"card\" id=\"log-container\" style=\"max-height:600px;overflow-y:auto\"><div class=\"no-data\"><div class=\"no-data-icon\">📋</div><div>No log entries yet</div></div></div>\n" +
"        </div>\n" +
"    </main>\n" +
"</div>\n" +
"<script>\n" +
"const _urlParams=new URLSearchParams(window.location.search);const _authToken=_urlParams.get('token')||'';\n" +
"function _fetch(url){const sep=url.includes('?')?'&':'?';return fetch(_authToken?url+sep+'token='+encodeURIComponent(_authToken):url)}\n" +
"class Chart{constructor(id,color,max,min=0){this.canvas=document.getElementById(id);this.ctx=this.canvas.getContext('2d');this.color=color;this.max=max;this.min=min;this.data=[]}\n" +
"update(data){this.data=data;this.draw()}\n" +
"draw(){const r=this.canvas.getBoundingClientRect();this.canvas.width=r.width*2;this.canvas.height=r.height*2;this.ctx.scale(2,2);const w=r.width,h=r.height,p=35;this.ctx.clearRect(0,0,w,h);if(this.data.length<2)return;this.ctx.strokeStyle='rgba(255,255,255,0.03)';this.ctx.lineWidth=1;for(let i=0;i<=4;i++){const y=p+(h-p*2)*(i/4);this.ctx.beginPath();this.ctx.moveTo(p,y);this.ctx.lineTo(w-10,y);this.ctx.stroke();const v=this.max-(this.max-this.min)*(i/4);this.ctx.fillStyle='#333';this.ctx.font='10px sans-serif';this.ctx.fillText(v.toFixed(0),5,y+3)}this.ctx.beginPath();this.ctx.strokeStyle=this.color;this.ctx.lineWidth=2;this.ctx.shadowColor=this.color;this.ctx.shadowBlur=8;const step=(w-p-10)/(this.data.length-1);this.data.forEach((v,i)=>{const x=p+i*step,norm=(v-this.min)/(this.max-this.min),y=h-p-norm*(h-p*2);i===0?this.ctx.moveTo(x,y):this.ctx.lineTo(x,y)});this.ctx.stroke();this.ctx.shadowBlur=0;this.ctx.lineTo(p+(this.data.length-1)*step,h-p);this.ctx.lineTo(p,h-p);this.ctx.closePath();const g=this.ctx.createLinearGradient(0,p,0,h-p);g.addColorStop(0,this.color.replace(')',',0.2)').replace('rgb','rgba'));g.addColorStop(1,this.color.replace(')',',0)').replace('rgb','rgba'));this.ctx.fillStyle=g;this.ctx.fill()}}\n" +
"const tpsChart=new Chart('tpsChart','rgb(0,255,136)',20,0);\n" +
"const entityChart=new Chart('entityChart','rgb(168,85,247)',10000,0);\n" +
"const chunkChart=new Chart('chunkChart','rgb(0,212,255)',5000,0);\n" +
"function showPage(name){document.querySelectorAll('.page').forEach(p=>p.classList.remove('active'));document.querySelectorAll('.nav-item').forEach(n=>n.classList.remove('active'));document.getElementById('page-'+name).classList.add('active');document.querySelector('.nav-item[onclick*=\"'+name+'\"]').classList.add('active');if(name==='performance')loadPerformance();if(name==='settings')loadSettings();if(name==='alerts')loadAlerts();if(name==='logs')loadLogs()}\n" +
"function updateStats(){_fetch('/api/stats').then(r=>r.json()).then(d=>{const tpsEl=document.getElementById('tps');tpsEl.textContent=d.tps.toFixed(2);tpsEl.className='card-value '+(d.tps>=19?'good':d.tps>=15?'warn':'crit');document.getElementById('tps-bar').style.width=(d.tps/20*100)+'%';document.getElementById('tps-bar').style.background=d.tps>=19?'var(--green)':d.tps>=15?'var(--orange)':'var(--red)';const memEl=document.getElementById('memory');memEl.textContent=d.memory.toFixed(1)+'%';memEl.className='card-value '+(d.memory<70?'good':d.memory<85?'warn':'crit');document.getElementById('mem-bar').style.width=d.memory+'%';document.getElementById('mem-bar').style.background=d.memory<70?'var(--cyan)':d.memory<85?'var(--orange)':'var(--red)';document.getElementById('entities').textContent=d.entities.toLocaleString();document.getElementById('ent-bar').style.width=Math.min(d.entities/10000*100,100)+'%';document.getElementById('players').textContent=d.players;document.getElementById('play-bar').style.width=Math.min(d.players/50*100,100)+'%';document.getElementById('lastUpdate').textContent='Updated '+new Date().toLocaleTimeString()}).catch(e=>console.error(e))}\n" +
"function updateHistory(){_fetch('/api/history').then(r=>r.json()).then(d=>{if(d.history&&d.history.length>0){tpsChart.update(d.history.map(h=>h.tps));const maxE=Math.max(...d.history.map(h=>h.ent),1000);entityChart.max=Math.ceil(maxE/1000)*1000;entityChart.update(d.history.map(h=>h.ent));const maxC=Math.max(...d.history.map(h=>h.chunks),500);chunkChart.max=Math.ceil(maxC/500)*500;chunkChart.update(d.history.map(h=>h.chunks))}const el=document.getElementById('lagSpikes');if(d.lagSpikes&&d.lagSpikes.length>0){el.innerHTML=d.lagSpikes.slice(0,5).map(s=>{const t=new Date(s.t).toLocaleTimeString();return`<div class=\"event\"><div class=\"event-icon\">⚠</div><div class=\"event-content\"><div class=\"event-title\">Lag Spike</div><div class=\"event-details\">${s.cause} - Peak: ${s.peak.toFixed(1)}ms</div></div><span class=\"event-time\">${t}</span></div>`}).join('')}else{el.innerHTML='<div class=\"no-data\"><div class=\"no-data-icon\">✓</div><div>No lag spikes detected</div></div>'}}).catch(e=>console.error(e))}\n" +
"function loadPerformance(){_fetch('/api/stats').then(r=>r.json()).then(d=>{document.getElementById('perf-tps').textContent=d.tps.toFixed(2);document.getElementById('perf-tps').className='card-value '+(d.tps>=19?'good':d.tps>=15?'warn':'crit');document.getElementById('perf-heap').textContent=d.memory.toFixed(1)+'%';document.getElementById('perf-heap').className='card-value '+(d.memory<70?'good':d.memory<85?'warn':'crit');document.getElementById('perf-heap-detail').textContent=d.memoryUsed+' / '+d.memoryMax+' MB';document.getElementById('perf-profile').textContent=d.profile;document.getElementById('perfUpdate').textContent='Updated '+new Date().toLocaleTimeString()}).catch(e=>console.error('Stats error:',e));_fetch('/api/system').then(r=>r.json()).then(d=>{let html='';if(d&&d.worlds&&d.worlds.length>0){d.worlds.forEach(w=>{html+=`<tr><td>${w.name}</td><td>${w.entities.toLocaleString()}</td><td>${w.chunks.toLocaleString()}</td><td>${w.players}</td></tr>`})}document.getElementById('world-stats').innerHTML=html||'<tr><td colspan=\"4\">No worlds available</td></tr>';let phtml='';if(d&&d.plugins&&d.plugins.length>0){d.plugins.forEach(p=>{phtml+=`<tr><td>${p.name}</td><td>${p.version}</td><td><span class=\"toggle ${p.enabled?'on':'off'}\">${p.enabled?'Enabled':'Disabled'}</span></td></tr>`})}document.getElementById('plugin-list').innerHTML=phtml||'<tr><td colspan=\"3\">No plugins available</td></tr>'}).catch(e=>{console.error('System error:',e);document.getElementById('world-stats').innerHTML='<tr><td colspan=\"4\">Loading...</td></tr>';document.getElementById('plugin-list').innerHTML='<tr><td colspan=\"3\">Loading...</td></tr>'})}\n" +
"function loadSettings(){_fetch('/api/config').then(r=>r.json()).then(d=>{const features=[{k:'entity_limiter',n:'Entity Limiter',desc:'Limits entity spawns when server is overloaded'},{k:'auto_clear',n:'Auto Clear',desc:'Automatically clears stuck arrows'},{k:'hibernate',n:'Hibernation',desc:'Hibernates entities in distant chunks'},{k:'redstone_hopper',n:'Redstone/Hopper',desc:'Optimizes redstone and hopper tick rates'},{k:'empty_server',n:'Empty Server Mode',desc:'Reduces resource usage when no players online'},{k:'stack_fusion',n:'Stack Fusion',desc:'Merges nearby dropped items and XP orbs'},{k:'notifications',n:'Discord Notifications',desc:'Sends alerts to Discord webhook'},{k:'web_dashboard',n:'Web Dashboard',desc:'This dashboard interface'}];let html='';if(d&&d.features){features.forEach(f=>{const on=d.features[f.k];html+=`<tr><td>${f.n}</td><td><span class=\"toggle ${on?'on':'off'}\">${on?'Enabled':'Disabled'}</span></td><td style=\"color:var(--muted)\">${f.desc}</td></tr>`})}document.getElementById('feature-list').innerHTML=html||'<tr><td colspan=\"3\">Loading...</td></tr>'}).catch(e=>console.error('Config error:',e));_fetch('/api/system').then(r=>r.json()).then(d=>{if(d){document.getElementById('sys-java').textContent=d.java_version||'--';document.getElementById('sys-server').textContent=d.server_name||'--';document.getElementById('sys-cpu').textContent=d.processors||'--'}}).catch(e=>{console.error('System error:',e);document.getElementById('sys-java').textContent='--';document.getElementById('sys-server').textContent='--';document.getElementById('sys-cpu').textContent='--'})}\n" +
"function loadAlerts(){_fetch('/api/config').then(r=>r.json()).then(d=>{if(d&&d.thresholds){document.getElementById('thresh-light').textContent=d.thresholds.tps_light.toFixed(1);document.getElementById('thresh-normal').textContent=d.thresholds.tps_normal.toFixed(1);document.getElementById('thresh-aggressive').textContent=d.thresholds.tps_aggressive.toFixed(1)}}).catch(e=>console.error('Config error:',e));_fetch('/api/history').then(r=>r.json()).then(d=>{const el=document.getElementById('alert-spikes');if(d&&d.lagSpikes&&d.lagSpikes.length>0){el.innerHTML=d.lagSpikes.map(s=>{const t=new Date(s.t).toLocaleString();return`<div class=\"event\"><div class=\"event-icon\">⚠</div><div class=\"event-content\"><div class=\"event-title\">Lag Spike - ${s.peak.toFixed(1)}ms</div><div class=\"event-details\">${s.cause} | TPS: ${s.tps.toFixed(2)}</div></div><span class=\"event-time\">${t}</span></div>`}).join('')}else{el.innerHTML='<div class=\"no-data\"><div class=\"no-data-icon\">✓</div><div>No lag spikes recorded</div></div>'}}).catch(e=>console.error('History error:',e))}\n" +
"function loadLogs(){_fetch('/api/logs').then(r=>r.json()).then(d=>{document.getElementById('log-count').textContent=(d&&d.logs?d.logs.length:0)+' entries';const el=document.getElementById('log-container');if(d&&d.logs&&d.logs.length>0){el.innerHTML=d.logs.map(l=>{const t=new Date(l.t).toLocaleTimeString();const cls=l.level.toLowerCase().includes('error')?'error':l.level.toLowerCase().includes('warn')?'warn':'info';return`<div class=\"log-entry ${cls}\"><span class=\"log-time\">${t}</span><span class=\"log-level\">[${l.level}]</span><span>${l.msg}</span></div>`}).join('')}else{el.innerHTML='<div class=\"no-data\"><div class=\"no-data-icon\">📋</div><div>No log entries yet</div></div>'}}).catch(e=>console.error('Logs error:',e))}\n" +
"updateStats();updateHistory();setInterval(updateStats,2000);setInterval(updateHistory,5000);window.addEventListener('resize',()=>{tpsChart.draw();entityChart.draw();chunkChart.draw()});\n" +
"</script>\n" +
"</body>\n" +
"</html>";
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
