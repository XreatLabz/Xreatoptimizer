package com.xreatlabs.xreatoptimizer.metrics;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import io.micrometer.core.instrument.*;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Central metrics registry */
public class MetricsRegistry {

    private final XreatOptimizer plugin;
    private final PrometheusMeterRegistry registry;

    // Gauges
    private final AtomicInteger currentTps = new AtomicInteger(20);
    private final AtomicInteger minTps = new AtomicInteger(20);
    private final AtomicInteger maxTps = new AtomicInteger(20);
    private final AtomicLong usedMemoryMb = new AtomicLong(0);
    private final AtomicLong maxMemoryMb = new AtomicLong(0);
    private final AtomicInteger memoryPercentage = new AtomicInteger(0);
    private final AtomicInteger entityCount = new AtomicInteger(0);
    private final AtomicInteger chunkCount = new AtomicInteger(0);
    private final AtomicInteger playerCount = new AtomicInteger(0);
    private final AtomicInteger threadPoolActive = new AtomicInteger(0);
    private final AtomicInteger threadPoolQueued = new AtomicInteger(0);

    // Counters
    private Counter lagSpikeCounter;
    private Counter profileChangeCounter;
    private Counter chunkLoadCounter;
    private Counter chunkUnloadCounter;
    private Counter gcCounter;
    private Counter optimizationRunCounter;

    // Timers
    private Timer optimizationTimer;

    public MetricsRegistry(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        initializeMetrics();
        LoggerUtils.info("Metrics registry initialized");
    }

    private void initializeMetrics() {
        // TPS Gauges
        Gauge.builder("xreat_tps_current", currentTps, AtomicInteger::get)
            .description("Current server TPS")
            .baseUnit("tps")
            .register(registry);

        Gauge.builder("xreat_tps_min", minTps, AtomicInteger::get)
            .description("Minimum TPS since startup")
            .baseUnit("tps")
            .register(registry);

        Gauge.builder("xreat_tps_max", maxTps, AtomicInteger::get)
            .description("Maximum TPS since startup")
            .baseUnit("tps")
            .register(registry);

        // Memory Gauges
        Gauge.builder("xreat_memory_used_mb", usedMemoryMb, AtomicLong::get)
            .description("Used heap memory in MB")
            .baseUnit("megabytes")
            .register(registry);

        Gauge.builder("xreat_memory_max_mb", maxMemoryMb, AtomicLong::get)
            .description("Maximum heap memory in MB")
            .baseUnit("megabytes")
            .register(registry);

        Gauge.builder("xreat_memory_percentage", memoryPercentage, AtomicInteger::get)
            .description("Memory usage percentage")
            .baseUnit("percent")
            .register(registry);

        // Entity/Chunk Gauges
        Gauge.builder("xreat_entities_total", entityCount, AtomicInteger::get)
            .description("Total entity count")
            .baseUnit("entities")
            .register(registry);

        Gauge.builder("xreat_chunks_loaded", chunkCount, AtomicInteger::get)
            .description("Total loaded chunks")
            .baseUnit("chunks")
            .register(registry);

        Gauge.builder("xreat_players_online", playerCount, AtomicInteger::get)
            .description("Online player count")
            .baseUnit("players")
            .register(registry);

        // Thread Pool Gauges
        Gauge.builder("xreat_threadpool_active", threadPoolActive, AtomicInteger::get)
            .description("Active threads in thread pool")
            .baseUnit("threads")
            .register(registry);

        Gauge.builder("xreat_threadpool_queued", threadPoolQueued, AtomicInteger::get)
            .description("Queued tasks in thread pool")
            .baseUnit("tasks")
            .register(registry);

        // Counters
        lagSpikeCounter = Counter.builder("xreat_lag_spikes_total")
            .description("Total number of lag spikes detected")
            .register(registry);

        profileChangeCounter = Counter.builder("xreat_profile_changes_total")
            .description("Total number of optimization profile changes")
            .register(registry);

        chunkLoadCounter = Counter.builder("xreat_chunks_loaded_total")
            .description("Total chunks loaded")
            .register(registry);

        chunkUnloadCounter = Counter.builder("xreat_chunks_unloaded_total")
            .description("Total chunks unloaded")
            .register(registry);

        gcCounter = Counter.builder("xreat_gc_runs_total")
            .description("Total garbage collection runs")
            .register(registry);

        optimizationRunCounter = Counter.builder("xreat_optimizations_total")
            .description("Total optimization runs")
            .register(registry);

        // Timers
        optimizationTimer = Timer.builder("xreat_optimization_duration")
            .description("Time taken for optimization runs")
            .register(registry);

        // Server info as gauge (constant)
        Gauge.builder("xreat_info", () -> 1)
            .description("XreatOptimizer version info")
            .tag("version", plugin.getDescription().getVersion())
            .tag("server_version", Bukkit.getVersion())
            .tag("bukkit_version", Bukkit.getBukkitVersion())
            .register(registry);
    }

    public void updateTps(double tps) {
        currentTps.set((int) (tps * 100)); // Store as integer (20.00 = 2000)
        minTps.set((int) Math.min(minTps.get(), tps * 100));
        maxTps.set((int) Math.max(maxTps.get(), tps * 100));
    }

    public void updateMemory(long usedMb, long maxMb, double percentage) {
        usedMemoryMb.set(usedMb);
        maxMemoryMb.set(maxMb);
        memoryPercentage.set((int) percentage);
    }

    public void updateEntityCount(int count) {
        entityCount.set(count);
    }

    public void updateChunkCount(int count) {
        chunkCount.set(count);
    }

    public void updatePlayerCount(int count) {
        playerCount.set(count);
    }

    public void updateThreadPool(int active, int queued) {
        threadPoolActive.set(active);
        threadPoolQueued.set(queued);
    }

    public void recordLagSpike() {
        lagSpikeCounter.increment();
    }

    public void recordProfileChange() {
        profileChangeCounter.increment();
    }

    public void recordChunkLoad() {
        chunkLoadCounter.increment();
    }

    public void recordChunkUnload() {
        chunkUnloadCounter.increment();
    }

    public void recordGC() {
        gcCounter.increment();
    }

    public void recordOptimization() {
        optimizationRunCounter.increment();
    }

    public Timer.Sample startOptimizationTimer() {
        return Timer.start(registry);
    }

    public void stopOptimizationTimer(Timer.Sample sample) {
        sample.stop(optimizationTimer);
    }

    public PrometheusMeterRegistry getRegistry() {
        return registry;
    }

    public String scrape() {
        return registry.scrape();
    }
}
