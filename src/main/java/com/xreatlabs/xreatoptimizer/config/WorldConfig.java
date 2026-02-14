package com.xreatlabs.xreatoptimizer.config;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-world configuration */
public class WorldConfig {
    
    private final XreatOptimizer plugin;
    private final File configFile;
    private FileConfiguration config;
    
    private final Map<String, WorldSettings> worldSettings = new ConcurrentHashMap<>();
    private final Set<String> protectedChunks = ConcurrentHashMap.newKeySet();
    
    public WorldConfig(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "worlds.yml");
        loadConfig();
    }
    
    public void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        
        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String worldName : worldsSection.getKeys(false)) {
                ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
                if (worldSection != null) {
                    WorldSettings settings = new WorldSettings();
                    settings.entityLimiterEnabled = worldSection.getBoolean("entity_limiter.enabled", false);
                    settings.maxEntitiesPerWorld = worldSection.getInt("entity_limiter.max_entities", 10000);
                    settings.hibernateEnabled = worldSection.getBoolean("hibernate.enabled", false);
                    settings.hibernateRadius = worldSection.getInt("hibernate.radius", 64);
                    settings.autoClearEnabled = worldSection.getBoolean("auto_clear.enabled", false);
                    settings.viewDistanceOverride = worldSection.getInt("view_distance", -1);
                    
                    worldSettings.put(worldName, settings);
                }
            }
        }
        
        List<String> protectedList = config.getStringList("protected_chunks");
        protectedChunks.clear();
        protectedChunks.addAll(protectedList);
        
        LoggerUtils.info("Loaded world configurations for " + worldSettings.size() + " worlds, " +
                        protectedChunks.size() + " protected chunks");
    }
    
    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            
            config = YamlConfiguration.loadConfiguration(configFile);
            
            config.options().header(
                "XreatOptimizer Per-World Configuration\n" +
                "Configure different optimization settings for each world.\n" +
                "\n" +
                "You can also protect specific chunks from being unloaded/hibernated.\n" +
                "Format: world_name:chunk_x:chunk_z\n"
            );
            
            config.set("worlds.world.entity_limiter.enabled", false);
            config.set("worlds.world.entity_limiter.max_entities", 10000);
            config.set("worlds.world.hibernate.enabled", false);
            config.set("worlds.world.hibernate.radius", 64);
            config.set("worlds.world.auto_clear.enabled", false);
            config.set("worlds.world.view_distance", -1);
            
            config.set("worlds.world_nether.entity_limiter.enabled", false);
            config.set("worlds.world_nether.entity_limiter.max_entities", 5000);
            config.set("worlds.world_nether.hibernate.enabled", false);
            config.set("worlds.world_nether.hibernate.radius", 48);
            config.set("worlds.world_nether.auto_clear.enabled", false);
            config.set("worlds.world_nether.view_distance", -1);
            
            config.set("worlds.world_the_end.entity_limiter.enabled", false);
            config.set("worlds.world_the_end.entity_limiter.max_entities", 5000);
            config.set("worlds.world_the_end.hibernate.enabled", false);
            config.set("worlds.world_the_end.hibernate.radius", 48);
            config.set("worlds.world_the_end.auto_clear.enabled", false);
            config.set("worlds.world_the_end.view_distance", -1);
            
            config.set("protected_chunks", Collections.emptyList());
            
            saveConfig();
            
        } catch (IOException e) {
            LoggerUtils.error("Failed to create worlds.yml", e);
        }
    }
    
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            LoggerUtils.error("Failed to save worlds.yml", e);
        }
    }
    
    public WorldSettings getWorldSettings(String worldName) {
        return worldSettings.getOrDefault(worldName, new WorldSettings());
    }
    
    public WorldSettings getWorldSettings(World world) {
        return getWorldSettings(world.getName());
    }
    
    public boolean isEntityLimiterEnabled(String worldName) {
        WorldSettings settings = worldSettings.get(worldName);
        if (settings != null) {
            return settings.entityLimiterEnabled;
        }
        return plugin.getConfig().getBoolean("entity_limiter.enabled", false);
    }
    
    public boolean isHibernateEnabled(String worldName) {
        WorldSettings settings = worldSettings.get(worldName);
        if (settings != null) {
            return settings.hibernateEnabled;
        }
        return plugin.getConfig().getBoolean("hibernate.enabled", false);
    }
    
    public int getMaxEntities(String worldName) {
        WorldSettings settings = worldSettings.get(worldName);
        if (settings != null && settings.maxEntitiesPerWorld > 0) {
            return settings.maxEntitiesPerWorld;
        }
        return plugin.getConfig().getInt("entity_limiter.max_entities_per_world", 10000);
    }
    
    public int getHibernateRadius(String worldName) {
        WorldSettings settings = worldSettings.get(worldName);
        if (settings != null && settings.hibernateRadius > 0) {
            return settings.hibernateRadius;
        }
        return plugin.getConfig().getInt("hibernate.radius", 64);
    }
    
    public boolean isChunkProtected(Chunk chunk) {
        String key = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        return protectedChunks.contains(key);
    }
    
    public boolean isChunkProtected(String worldName, int chunkX, int chunkZ) {
        String key = worldName + ":" + chunkX + ":" + chunkZ;
        return protectedChunks.contains(key);
    }
    
    public void addProtectedChunk(String worldName, int chunkX, int chunkZ) {
        String key = worldName + ":" + chunkX + ":" + chunkZ;
        protectedChunks.add(key);
        
        List<String> list = new ArrayList<>(protectedChunks);
        config.set("protected_chunks", list);
        saveConfig();
        
        LoggerUtils.info("Added protected chunk: " + key);
    }
    
    public void removeProtectedChunk(String worldName, int chunkX, int chunkZ) {
        String key = worldName + ":" + chunkX + ":" + chunkZ;
        protectedChunks.remove(key);
        
        List<String> list = new ArrayList<>(protectedChunks);
        config.set("protected_chunks", list);
        saveConfig();
        
        LoggerUtils.info("Removed protected chunk: " + key);
    }
    
    public Set<String> getProtectedChunks() {
        return Collections.unmodifiableSet(protectedChunks);
    }
    
    public void setWorldSetting(String worldName, String setting, Object value) {
        String path = "worlds." + worldName + "." + setting;
        config.set(path, value);
        saveConfig();
        loadConfig();
    }
    
    public void reload() {
        loadConfig();
    }
    
    public static class WorldSettings {
        public boolean entityLimiterEnabled = false;
        public int maxEntitiesPerWorld = 10000;
        public boolean hibernateEnabled = false;
        public int hibernateRadius = 64;
        public boolean autoClearEnabled = false;
        public int viewDistanceOverride = -1;
        
        @Override
        public String toString() {
            return String.format(
                "WorldSettings[entities=%b/%d, hibernate=%b/%d, autoClear=%b, viewDist=%d]",
                entityLimiterEnabled, maxEntitiesPerWorld,
                hibernateEnabled, hibernateRadius,
                autoClearEnabled, viewDistanceOverride
            );
        }
    }
}
