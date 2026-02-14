package com.xreatlabs.xreatoptimizer;

import com.xreatlabs.xreatoptimizer.commands.OptimizeCommand;
import com.xreatlabs.xreatoptimizer.commands.OptimizeTabCompleter;
import com.xreatlabs.xreatoptimizer.commands.ReportCommand;
import com.xreatlabs.xreatoptimizer.commands.OptimizeGUICommand;
import com.xreatlabs.xreatoptimizer.listeners.ServerEventListener;
import com.xreatlabs.xreatoptimizer.listeners.EntityEventListener;
import com.xreatlabs.xreatoptimizer.listeners.GUIClickListener;
import com.xreatlabs.xreatoptimizer.managers.*;
import com.xreatlabs.xreatoptimizer.storage.StatisticsStorage;
import com.xreatlabs.xreatoptimizer.config.ConfigReloader;
import com.xreatlabs.xreatoptimizer.config.WorldConfig;
import com.xreatlabs.xreatoptimizer.notifications.NotificationManager;
import com.xreatlabs.xreatoptimizer.metrics.Metrics;
import com.xreatlabs.xreatoptimizer.web.WebDashboard;
import com.xreatlabs.xreatoptimizer.version.VersionAdapter;
import com.xreatlabs.xreatoptimizer.core.AdaptiveThresholdManager;
import com.xreatlabs.xreatoptimizer.api.XreatOptimizerAPI;
import com.xreatlabs.xreatoptimizer.Constants;
import org.bukkit.plugin.java.JavaPlugin;

public class XreatOptimizer extends JavaPlugin {

    private static XreatOptimizer instance;
    private long startTime;
    private VersionAdapter versionAdapter;
    private OptimizationManager optimizationManager;
    private ThreadPoolManager threadPoolManager;
    private PerformanceMonitor performanceMonitor;
    private AdaptiveThresholdManager adaptiveThresholdManager;
    private AdvancedEntityOptimizer advancedEntityOptimizer;
    private SmartTickDistributor smartTickDistributor;
    private NetworkOptimizer networkOptimizer;
    private AdvancedCPURAMOptimizer advancedCPURAMOptimizer;
    private EntityCullingManager entityCullingManager;
    private EmptyServerOptimizer emptyServerOptimizer;
    private PredictiveChunkLoader predictiveChunkLoader;
    private RedstoneHopperOptimizer redstoneHopperOptimizer;
    private LagSpikeDetector lagSpikeDetector;
    private TickBudgetManager tickBudgetManager;
    private PathfindingCache pathfindingCache;
    // Store managers that need to be accessed by other components
    private HibernateManager hibernateManager;
    private ChunkPreGenerator chunkPreGenerator;
    private MemorySaver memorySaver;
    private AutoClearTask autoClearTask;
    private DynamicViewDistance dynamicViewDistance;
    private ItemDropTracker itemDropTracker;
    private StatisticsStorage statisticsStorage;
    private ConfigReloader configReloader;
    private WorldConfig worldConfig;
    private NotificationManager notificationManager;
    private Metrics metrics;
    private WebDashboard webDashboard;
    private com.xreatlabs.xreatoptimizer.metrics.PrometheusExporter prometheusExporter;
    private com.xreatlabs.xreatoptimizer.core.PerformanceTrendAnalyzer trendAnalyzer;
    private com.xreatlabs.xreatoptimizer.core.AlertManager alertManager;
    private com.xreatlabs.xreatoptimizer.profiling.JFRIntegration jfrIntegration;

    @Override
    public void onEnable() {
        instance = this;
        startTime = System.currentTimeMillis();

        // Initialize API first
        XreatOptimizerAPI.initialize(this);

        // Display startup banner
        getLogger().info(Constants.STARTUP_BANNER);

        // Initialize version adapter
        versionAdapter = new VersionAdapter(this);
        getLogger().info("Detected server version: " + versionAdapter.getServerVersion());
        
        // Initialize thread pool manager first
        threadPoolManager = new ThreadPoolManager();
        
        // Initialize performance monitor
        performanceMonitor = new PerformanceMonitor(this);
        
        // Initialize other managers
        optimizationManager = new OptimizationManager(this);
        
        // Initialize managers that depend on others
        hibernateManager = new HibernateManager(this);
        chunkPreGenerator = new ChunkPreGenerator(this);
        memorySaver = new MemorySaver(this);
        autoClearTask = new AutoClearTask(this);
        dynamicViewDistance = new DynamicViewDistance(this);
        itemDropTracker = new ItemDropTracker(this);
        
        // Initialize advanced optimization systems
        advancedEntityOptimizer = new AdvancedEntityOptimizer(this);
        smartTickDistributor = new SmartTickDistributor(this);
        networkOptimizer = new NetworkOptimizer(this);
        advancedCPURAMOptimizer = new AdvancedCPURAMOptimizer(this);
        entityCullingManager = new EntityCullingManager(this);
        emptyServerOptimizer = new EmptyServerOptimizer(this);
        
        // Initialize next-gen optimization systems
        predictiveChunkLoader = new PredictiveChunkLoader(this);
        redstoneHopperOptimizer = new RedstoneHopperOptimizer(this);
        lagSpikeDetector = new LagSpikeDetector(this);
        tickBudgetManager = new TickBudgetManager(this);
        pathfindingCache = new PathfindingCache(this);
        
        // Initialize adaptive threshold manager
        adaptiveThresholdManager = new AdaptiveThresholdManager(this);

        // Initialize trend analyzer and alert manager
        trendAnalyzer = new com.xreatlabs.xreatoptimizer.core.PerformanceTrendAnalyzer(this);
        alertManager = new com.xreatlabs.xreatoptimizer.core.AlertManager(this);

        // Initialize JFR integration
        jfrIntegration = new com.xreatlabs.xreatoptimizer.profiling.JFRIntegration(this);

        // Register commands with tab completion
        OptimizeCommand optimizeCommand = new OptimizeCommand(this);
        getCommand("xreatopt").setExecutor(optimizeCommand);
        getCommand("xreatopt").setTabCompleter(new OptimizeTabCompleter(this));
        getCommand("xreatreport").setExecutor(new ReportCommand(this));
        getCommand("xreatgui").setExecutor(new OptimizeGUICommand(this));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new ServerEventListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityEventListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIClickListener(this), this);

        // Initialize configuration
        saveDefaultConfig();

        // Initialize storage and config systems
        statisticsStorage = new StatisticsStorage(this);
        configReloader = new ConfigReloader(this);
        worldConfig = new WorldConfig(this);
        
        // Initialize notification manager
        notificationManager = new NotificationManager(this);
        
        // Initialize metrics
        metrics = new Metrics(this);

        // Initialize Prometheus exporter
        prometheusExporter = new com.xreatlabs.xreatoptimizer.metrics.PrometheusExporter(this);

        // Initialize web dashboard
        webDashboard = new WebDashboard(this);

        // Validate configuration
        if (!configReloader.validateConfig()) {
            getLogger().warning("Configuration validation failed - using defaults where applicable");
        }

        // Start all systems
        optimizationManager.start();
        performanceMonitor.start();
        advancedEntityOptimizer.start();
        smartTickDistributor.start();
        networkOptimizer.start();
        advancedCPURAMOptimizer.start();
        entityCullingManager.start();
        emptyServerOptimizer.start();
        itemDropTracker.start();
        
        // Start next-gen systems
        predictiveChunkLoader.start();
        redstoneHopperOptimizer.start();
        lagSpikeDetector.start();
        tickBudgetManager.start();
        pathfindingCache.start();
        
        adaptiveThresholdManager.start();
        trendAnalyzer.start();
        alertManager.start();

        // Start JFR integration
        if (jfrIntegration != null) {
            jfrIntegration.start();
        }

        // Register PlaceholderAPI expansion if available
        registerPlaceholderExpansion();

        // Start Prometheus exporter
        if (prometheusExporter != null) {
            prometheusExporter.start();
        }

        // Start web dashboard
        if (webDashboard != null && getConfig().getBoolean("web_dashboard.enabled", false)) {
            webDashboard.start();
        }

        // Send startup notification
        if (notificationManager != null && getConfig().getBoolean("notifications.enabled", false)) {
            notificationManager.notifyServerStart();
        }

        getLogger().info("XreatOptimizer enabled.");
    }



    @Override
    public void onDisable() {
        // Save statistics before shutdown
        if (statisticsStorage != null) {
            statisticsStorage.saveStatistics();
            getLogger().info("Statistics saved to disk");
        }

        // Stop all systems safely
        if (performanceMonitor != null) {
            performanceMonitor.stop();
        }

        if (adaptiveThresholdManager != null) {
            adaptiveThresholdManager.stop();
        }

        if (trendAnalyzer != null) {
            trendAnalyzer.stop();
        }

        if (alertManager != null) {
            alertManager.stop();
        }

        if (jfrIntegration != null) {
            jfrIntegration.stop();
        }

        if (advancedEntityOptimizer != null) {
            advancedEntityOptimizer.stop();
        }
        
        if (smartTickDistributor != null) {
            smartTickDistributor.stop();
        }
        
        if (networkOptimizer != null) {
            networkOptimizer.stop();
        }
        
        if (advancedCPURAMOptimizer != null) {
            advancedCPURAMOptimizer.stop();
        }
        
        if (entityCullingManager != null) {
            entityCullingManager.stop();
        }
        
        if (emptyServerOptimizer != null) {
            emptyServerOptimizer.stop();
        }

        if (itemDropTracker != null) {
            itemDropTracker.stop();
        }
        
        // Stop next-gen systems
        if (predictiveChunkLoader != null) {
            predictiveChunkLoader.stop();
        }
        
        if (redstoneHopperOptimizer != null) {
            redstoneHopperOptimizer.stop();
        }
        
        if (lagSpikeDetector != null) {
            lagSpikeDetector.stop();
        }
        
        if (tickBudgetManager != null) {
            tickBudgetManager.stop();
        }
        
        if (pathfindingCache != null) {
            pathfindingCache.stop();
        }
        
        if (optimizationManager != null) {
            optimizationManager.stop();
        }
        
        // Shutdown metrics
        if (metrics != null) {
            metrics.shutdown();
        }

        // Shutdown Prometheus exporter
        if (prometheusExporter != null) {
            prometheusExporter.stop();
        }

        // Shutdown web dashboard
        if (webDashboard != null) {
            webDashboard.stop();
        }
        
        // Shutdown thread pools
        if (threadPoolManager != null) {
            threadPoolManager.shutdown();
        }

        // Shutdown Libby manager
        // if (libbyManager != null) {  // Commented out due to dependency issues
        //     libbyManager.shutdown();
        // }

        getLogger().info("XreatOptimizer disabled.");
    }

    // Getters
    public static XreatOptimizer getInstance() {
        return instance;
    }

    public VersionAdapter getVersionAdapter() {
        return versionAdapter;
    }

    public OptimizationManager getOptimizationManager() {
        return optimizationManager;
    }

    public ThreadPoolManager getThreadPoolManager() {
        return threadPoolManager;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public HibernateManager getHibernateManager() {
        return hibernateManager;
    }

    public MemorySaver getMemorySaver() {
        return memorySaver;
    }

    public AutoClearTask getAutoClearTask() {
        return autoClearTask;
    }

    public ChunkPreGenerator getChunkPreGenerator() {
        return chunkPreGenerator;
    }

    public DynamicViewDistance getDynamicViewDistance() {
        return dynamicViewDistance;
    }

    public AdvancedCPURAMOptimizer getAdvancedCPURAMOptimizer() {
        return advancedCPURAMOptimizer;
    }

    public AdvancedEntityOptimizer getAdvancedEntityOptimizer() {
        return advancedEntityOptimizer;
    }

    public SmartTickDistributor getSmartTickDistributor() {
        return smartTickDistributor;
    }

    public NetworkOptimizer getNetworkOptimizer() {
        return networkOptimizer;
    }

    public EntityCullingManager getEntityCullingManager() {
        return entityCullingManager;
    }

    public EmptyServerOptimizer getEmptyServerOptimizer() {
        return emptyServerOptimizer;
    }

    public PredictiveChunkLoader getPredictiveChunkLoader() {
        return predictiveChunkLoader;
    }

    public RedstoneHopperOptimizer getRedstoneHopperOptimizer() {
        return redstoneHopperOptimizer;
    }

    public LagSpikeDetector getLagSpikeDetector() {
        return lagSpikeDetector;
    }

    public TickBudgetManager getTickBudgetManager() {
        return tickBudgetManager;
    }

    public PathfindingCache getPathfindingCache() {
        return pathfindingCache;
    }

    public StatisticsStorage getStatisticsStorage() {
        return statisticsStorage;
    }

    public ConfigReloader getConfigReloader() {
        return configReloader;
    }

    public AdaptiveThresholdManager getAdaptiveThresholdManager() {
        return adaptiveThresholdManager;
    }

    public ItemDropTracker getItemDropTracker() {
        return itemDropTracker;
    }

    public WorldConfig getWorldConfig() {
        return worldConfig;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public WebDashboard getWebDashboard() {
        return webDashboard;
    }

    public com.xreatlabs.xreatoptimizer.metrics.PrometheusExporter getPrometheusExporter() {
        return prometheusExporter;
    }

    public com.xreatlabs.xreatoptimizer.core.PerformanceTrendAnalyzer getTrendAnalyzer() {
        return trendAnalyzer;
    }

    public com.xreatlabs.xreatoptimizer.core.AlertManager getAlertManager() {
        return alertManager;
    }

    public com.xreatlabs.xreatoptimizer.profiling.JFRIntegration getJFRIntegration() {
        return jfrIntegration;
    }

    public long getStartTime() {
        return startTime;
    }

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new com.xreatlabs.xreatoptimizer.hooks.XreatPlaceholderExpansion(this).register();
                getLogger().info("PlaceholderAPI expansion registered.");
            } catch (Exception e) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            }
        }
    }
}