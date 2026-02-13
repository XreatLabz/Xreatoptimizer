package com.xreatlabs.xreatoptimizer.profiling;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import jdk.jfr.*;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Java Flight Recorder (JFR) Integration
 * Provides professional-grade profiling for performance analysis
 *
 * Features:
 * - Automatic JFR recording during lag spikes
 * - Custom JFR events for Minecraft operations
 * - Low-overhead continuous profiling
 * - Export recordings for analysis with JDK Mission Control
 *
 * Requirements: Java 11+
 *
 * @since 1.2.0
 */
public class JFRIntegration {

    private final XreatOptimizer plugin;
    private Recording continuousRecording;
    private Recording lagSpikeRecording;
    private final Path recordingsDir;
    private volatile boolean isEnabled = false;
    private volatile boolean isRecordingLagSpike = false;

    // Configuration
    private final long MAX_RECORDING_SIZE = 100 * 1024 * 1024; // 100 MB
    private final Duration MAX_RECORDING_AGE = Duration.ofHours(1);
    private final Duration LAG_SPIKE_RECORDING_DURATION = Duration.ofSeconds(30);

    public JFRIntegration(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.recordingsDir = Paths.get(plugin.getDataFolder().getAbsolutePath(), "jfr-recordings");
    }

    /**
     * Start JFR integration
     */
    public void start() {
        if (!isJFRAvailable()) {
            LoggerUtils.info("JFR is not available (requires Java 11+). Profiling disabled.");
            return;
        }

        if (!plugin.getConfig().getBoolean("jfr.enabled", false)) {
            LoggerUtils.info("JFR integration is disabled in config.");
            return;
        }

        try {
            // Create recordings directory
            Files.createDirectories(recordingsDir);

            // Register custom JFR events
            registerCustomEvents();

            // Start continuous recording if enabled
            if (plugin.getConfig().getBoolean("jfr.continuous_recording", true)) {
                startContinuousRecording();
            }

            isEnabled = true;
            LoggerUtils.info("JFR integration started - recordings saved to: " + recordingsDir);

        } catch (Exception e) {
            LoggerUtils.warn("Failed to start JFR integration: " + e.getMessage());
        }
    }

    /**
     * Stop JFR integration
     */
    public void stop() {
        if (!isEnabled) return;

        try {
            // Stop continuous recording
            if (continuousRecording != null) {
                stopRecording(continuousRecording, "continuous-final");
                continuousRecording = null;
            }

            // Stop lag spike recording if active
            if (lagSpikeRecording != null) {
                stopRecording(lagSpikeRecording, "lagspike-final");
                lagSpikeRecording = null;
            }

            isEnabled = false;
            LoggerUtils.info("JFR integration stopped");

        } catch (Exception e) {
            LoggerUtils.warn("Error stopping JFR integration: " + e.getMessage());
        }
    }

    /**
     * Start continuous low-overhead recording
     */
    private void startContinuousRecording() throws Exception {
        Configuration config = Configuration.getConfiguration("profile");

        continuousRecording = new Recording(config);
        continuousRecording.setName("XreatOptimizer-Continuous");
        continuousRecording.setMaxSize(MAX_RECORDING_SIZE);
        continuousRecording.setMaxAge(MAX_RECORDING_AGE);
        continuousRecording.setToDisk(true);

        // Enable relevant events
        continuousRecording.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
        continuousRecording.enable("jdk.GarbageCollection");
        continuousRecording.enable("jdk.ThreadAllocationStatistics").withPeriod(Duration.ofSeconds(10));
        continuousRecording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(20));
        continuousRecording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(20));

        continuousRecording.start();
        LoggerUtils.info("Started continuous JFR recording");
    }

    /**
     * Trigger lag spike recording
     */
    public void recordLagSpike(double tickTimeMs, String cause) {
        if (!isEnabled || isRecordingLagSpike) return;

        try {
            isRecordingLagSpike = true;

            // Create high-detail recording for lag spike
            Configuration config = Configuration.getConfiguration("profile");
            lagSpikeRecording = new Recording(config);
            lagSpikeRecording.setName("XreatOptimizer-LagSpike-" + System.currentTimeMillis());
            lagSpikeRecording.setDuration(LAG_SPIKE_RECORDING_DURATION);
            lagSpikeRecording.setToDisk(true);

            // Enable detailed events
            lagSpikeRecording.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(100));
            lagSpikeRecording.enable("jdk.GarbageCollection");
            lagSpikeRecording.enable("jdk.ObjectAllocationInNewTLAB");
            lagSpikeRecording.enable("jdk.ObjectAllocationOutsideTLAB");
            lagSpikeRecording.enable("jdk.JavaMonitorEnter");
            lagSpikeRecording.enable("jdk.ThreadPark");
            lagSpikeRecording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));

            lagSpikeRecording.start();

            // Emit custom lag spike event
            LagSpikeEvent event = new LagSpikeEvent();
            event.tickTimeMs = tickTimeMs;
            event.cause = cause;
            event.commit();

            LoggerUtils.info("Started JFR recording for lag spike: " + tickTimeMs + "ms (" + cause + ")");

            // Schedule recording stop
            plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    try {
                        stopRecording(lagSpikeRecording, "lagspike-" + System.currentTimeMillis());
                        lagSpikeRecording = null;
                        isRecordingLagSpike = false;
                    } catch (Exception e) {
                        LoggerUtils.warn("Error stopping lag spike recording: " + e.getMessage());
                    }
                },
                LAG_SPIKE_RECORDING_DURATION.getSeconds() * 20L // Convert to ticks
            );

        } catch (Exception e) {
            LoggerUtils.warn("Failed to start lag spike recording: " + e.getMessage());
            isRecordingLagSpike = false;
        }
    }

    /**
     * Stop and save a recording
     */
    private void stopRecording(Recording recording, String name) throws IOException {
        if (recording == null || recording.getState() != RecordingState.RUNNING) {
            return;
        }

        Path outputPath = recordingsDir.resolve(name + ".jfr");
        recording.dump(outputPath);
        recording.close();

        LoggerUtils.info("JFR recording saved: " + outputPath);
    }

    /**
     * Register custom JFR events
     */
    private void registerCustomEvents() {
        // Custom events are registered automatically via @Name annotation
        LoggerUtils.debug("Registered custom JFR events");
    }

    /**
     * Check if JFR is available
     */
    private boolean isJFRAvailable() {
        try {
            Class.forName("jdk.jfr.Recording");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Get recording statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", isEnabled);
        stats.put("continuous_recording_active", continuousRecording != null && continuousRecording.getState() == RecordingState.RUNNING);
        stats.put("lag_spike_recording_active", isRecordingLagSpike);
        stats.put("recordings_directory", recordingsDir.toString());

        try {
            long recordingCount = Files.list(recordingsDir)
                .filter(p -> p.toString().endsWith(".jfr"))
                .count();
            stats.put("total_recordings", recordingCount);
        } catch (IOException e) {
            stats.put("total_recordings", 0);
        }

        return stats;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public boolean isRecordingLagSpike() {
        return isRecordingLagSpike;
    }

    // Custom JFR Events

    /**
     * Lag spike event
     */
    @Name("com.xreatlabs.xreatoptimizer.LagSpike")
    @Label("Lag Spike")
    @Description("Detected lag spike in server tick")
    @Category("XreatOptimizer")
    @StackTrace(false)
    public static class LagSpikeEvent extends Event {
        @Label("Tick Time (ms)")
        public double tickTimeMs;

        @Label("Cause")
        public String cause;
    }

    /**
     * Optimization event
     */
    @Name("com.xreatlabs.xreatoptimizer.Optimization")
    @Label("Optimization")
    @Description("Optimization cycle executed")
    @Category("XreatOptimizer")
    @StackTrace(false)
    public static class OptimizationEvent extends Event {
        @Label("Profile")
        public String profile;

        @Label("Execution Time (ms)")
        public long executionTimeMs;

        @Label("TPS Before")
        public double tpsBefore;

        @Label("TPS After")
        public double tpsAfter;
    }

    /**
     * Entity optimization event
     */
    @Name("com.xreatlabs.xreatoptimizer.EntityOptimization")
    @Label("Entity Optimization")
    @Description("Entity optimization executed")
    @Category("XreatOptimizer")
    @StackTrace(false)
    public static class EntityOptimizationEvent extends Event {
        @Label("Entities Before")
        public int entitiesBefore;

        @Label("Entities After")
        public int entitiesAfter;

        @Label("Entities Removed")
        public int entitiesRemoved;
    }

    /**
     * Memory optimization event
     */
    @Name("com.xreatlabs.xreatoptimizer.MemoryOptimization")
    @Label("Memory Optimization")
    @Description("Memory optimization executed")
    @Category("XreatOptimizer")
    @StackTrace(false)
    public static class MemoryOptimizationEvent extends Event {
        @Label("Memory Before (%)")
        public double memoryBeforePercent;

        @Label("Memory After (%)")
        public double memoryAfterPercent;

        @Label("Chunks Offloaded")
        public int chunksOffloaded;
    }
}
