package com.xreatlabs.xreatoptimizer.version;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.Bukkit;

/** Version compatibility adapter */
public class VersionAdapter {
    private final XreatOptimizer plugin;
    private final String serverVersion;
    private final int versionProtocol;

    public VersionAdapter(XreatOptimizer plugin) {
        this.plugin = plugin;
        this.serverVersion = getServerVersionString();
        this.versionProtocol = parseVersionProtocol(serverVersion);
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public int getVersionProtocol() {
        return versionProtocol;
    }

    public boolean isVersionAtLeast(int major, int minor) {
        String[] parts = serverVersion.split("\\.");
        if (parts.length < 2) return false;
        
        try {
            int serverMajor = Integer.parseInt(parts[0]);
            int serverMinor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            
            if (serverMajor > major) return true;
            if (serverMajor < major) return false;
            return serverMinor >= minor;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Could not parse server version: " + serverVersion);
            return false;
        }
    }

    public VersionSpecificAdapter getVersionSpecificAdapter() {
        if (isVersionAtLeast(1, 21)) {
            return new Version_1_21_X(this);
        } else if (isVersionAtLeast(1, 17)) {
            return new Version_1_17_1_20_X(this);
        } else if (isVersionAtLeast(1, 13)) {
            return new Version_1_13_1_16_X(this);
        } else {
            return new Version_1_8_1_12_X(this);
        }
    }

    private String getServerVersionString() {
        String version = Bukkit.getBukkitVersion();
        // Extract version from string like "1.20.4-R0.1-SNAPSHOT"
        int dashIndex = version.indexOf('-');
        if (dashIndex != -1) {
            version = version.substring(0, dashIndex);
        }
        return version;
    }

    private int parseVersionProtocol(String version) {
        // Protocol version mapping for Minecraft versions
        if (version.startsWith("1.21")) return 767;
        if (version.startsWith("1.20")) return 765;
        if (version.startsWith("1.19")) return 763;
        if (version.startsWith("1.18")) return 759;
        if (version.startsWith("1.17")) return 757;
        if (version.startsWith("1.16")) return 754;
        if (version.startsWith("1.15")) return 575;
        if (version.startsWith("1.14")) return 498;
        if (version.startsWith("1.13")) return 404;
        if (version.startsWith("1.12")) return 340;
        if (version.startsWith("1.11")) return 335;
        if (version.startsWith("1.10")) return 210;
        if (version.startsWith("1.9")) return 110;
        if (version.startsWith("1.8")) return 47;
        
        return 0; // Unknown version
    }

    public interface VersionSpecificAdapter {
        void initialize();
        void cleanup();
        boolean supportsAdvancedFeatures();
        Object getNMSHandler();
    }

    public static class Version_1_8_1_12_X implements VersionSpecificAdapter {
        private final VersionAdapter parent;

        public Version_1_8_1_12_X(VersionAdapter parent) {
            this.parent = parent;
        }

        @Override
        public void initialize() {
            parent.plugin.getLogger().info("Initializing 1.8-1.12 compatibility mode");
        }

        @Override
        public void cleanup() {
            // Cleanup for older versions
        }

        @Override
        public boolean supportsAdvancedFeatures() {
            return false; // Limited support for newer features
        }

        @Override
        public Object getNMSHandler() {
            // Use reflection to access NMS classes for older versions
            try {
                String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                Class<?> nmsClass = Class.forName("net.minecraft.server." + version + ".MinecraftServer");
                return nmsClass;
            } catch (ClassNotFoundException e) {
                parent.plugin.getLogger().warning("Could not access NMS classes for 1.8-1.12");
                return null;
            }
        }
    }

    public static class Version_1_13_1_16_X implements VersionSpecificAdapter {
        private final VersionAdapter parent;

        public Version_1_13_1_16_X(VersionAdapter parent) {
            this.parent = parent;
        }

        @Override
        public void initialize() {
            parent.plugin.getLogger().info("Initializing 1.13-1.16 compatibility mode");
        }

        @Override
        public void cleanup() {
            // Cleanup for 1.13-1.16 versions
        }

        @Override
        public boolean supportsAdvancedFeatures() {
            return true; // Better support for newer features
        }

        @Override
        public Object getNMSHandler() {
            // Use reflection for 1.13+ NMS
            try {
                String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                Class<?> nmsClass = Class.forName("net.minecraft.server." + version + ".MinecraftServer");
                return nmsClass;
            } catch (ClassNotFoundException e) {
                parent.plugin.getLogger().warning("Could not access NMS classes for 1.13-1.16");
                return null;
            }
        }
    }

    public static class Version_1_17_1_20_X implements VersionSpecificAdapter {
        private final VersionAdapter parent;

        public Version_1_17_1_20_X(VersionAdapter parent) {
            this.parent = parent;
        }

        @Override
        public void initialize() {
            parent.plugin.getLogger().info("Initializing 1.17-1.20.x compatibility mode");
        }

        @Override
        public void cleanup() {
            // Cleanup for 1.17+ versions
        }

        @Override
        public boolean supportsAdvancedFeatures() {
            return true; // Full support for advanced features
        }

        @Override
        public Object getNMSHandler() {
            // Use reflection for 1.17+ NMS (changed package structure)
            try {
                Class<?> nmsClass = Class.forName("net.minecraft.server.MinecraftServer");
                return nmsClass;
            } catch (ClassNotFoundException e) {
                parent.plugin.getLogger().warning("Could not access NMS classes for 1.17+");
                return null;
            }
        }
    }

    public static class Version_1_21_X implements VersionSpecificAdapter {
        private final VersionAdapter parent;

        public Version_1_21_X(VersionAdapter parent) {
            this.parent = parent;
        }

        @Override
        public void initialize() {
            parent.plugin.getLogger().info("Initializing 1.21+ compatibility mode");
        }

        @Override
        public void cleanup() {
            // Cleanup for 1.21+ versions
        }

        @Override
        public boolean supportsAdvancedFeatures() {
            return true; // Latest features supported
        }

        @Override
        public Object getNMSHandler() {
            // Use reflection for 1.21+ NMS
            try {
                Class<?> nmsClass = Class.forName("net.minecraft.server.MinecraftServer");
                return nmsClass;
            } catch (ClassNotFoundException e) {
                parent.plugin.getLogger().warning("Could not access NMS classes for 1.21+");
                return null;
            }
        }
    }
}