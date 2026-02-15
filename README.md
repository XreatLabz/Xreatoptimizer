# XreatOptimizer

[![Spigot](https://img.shields.io/badge/Spigot-1.8--1.21.10-orange.svg)](https://www.spigotmc.org/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.2.0-blue.svg)](https://github.com/XreatLabs/XreatOptimizer)
[![Java](https://img.shields.io/badge/Java-11+-blue.svg)](https://www.oracle.com/java/)

> **A comprehensive performance optimization plugin for Minecraft servers**

**XreatOptimizer** is a performance optimization plugin that combines multiple optimization techniques into a single solution. It supports Minecraft versions **1.8 through 1.21.10** and provides automated performance tuning, resource management, and monitoring capabilities.

**Important Disclaimer:** Performance improvements vary significantly based on server hardware, configuration, installed plugins, player count, and world size. The benchmarks shown in this document represent specific test conditions and may not reflect your server's results.

---

## Table of Contents

- [Key Features](#key-features)
- [Installation](#installation)
- [Recommended Configuration](#recommended-configuration)
- [Configuration](#configuration)
- [Commands](#commands)
- [Permissions](#permissions)
- [Optimization Profiles](#optimization-profiles)
- [Performance Expectations](#performance-expectations)
- [Resource Usage](#resource-usage)
- [Technical Architecture](#technical-architecture)
- [Build Instructions](#build-instructions)
- [Version Compatibility](#version-compatibility)
- [Support & Contact](#support--contact)

---

## Key Features

### Core Optimization Systems

- **Universal Compatibility**: Supports Minecraft versions 1.8 through 1.21.10 with automatic version detection
- **Async-First Architecture**: Heavy operations run asynchronously to minimize main thread impact
- **Automated Performance Tuning**: Uses statistical analysis (EWMA) to adapt optimization strategies
- **Cross-Platform Support**: Compatible with Spigot, Paper, Purpur, and forks

### Performance Optimization Features

#### Empty Server Optimizer
- Automatically reduces resource usage when no players are online
- Configurable delay before activation (default: 30 seconds)
- Reduces view distance, unloads distant chunks, and removes dropped items
- Instantly restores normal operation when players join

#### Entity Management
- Configurable entity limits (passive, hostile, items)
- Entity culling based on player view distance
- Stack fusion for dropped items and experience orbs
- Pathfinding calculation caching

#### Chunk & World Management
- Async chunk pregeneration
- Predictive chunk loading based on player movement
- Dynamic view distance adjustment based on TPS
- Chunk hibernation system (freezes entity AI in distant chunks)

#### Memory & CPU Optimization
- Real-time resource monitoring
- Compressed RAM caching with automatic cleanup
- Thread pool management for parallel operations
- Smart tick distribution to prevent lag spikes

#### Monitoring & Reporting
- Real-time TPS, memory, CPU, and entity tracking
- Historical performance data storage
- Detailed performance reports with recommendations
- Optional web dashboard for remote monitoring
- Prometheus metrics export (v1.2.0+)

### Advanced Features (v1.2.0)

- **Predictive Performance Management**: Forecasts performance issues 30-60 seconds ahead using time-series analysis
- **Real-time Anomaly Detection**: Detects memory leaks, entity explosions, chunk thrashing, and TPS oscillations
- **JFR Integration**: Automatic Java Flight Recorder profiling during lag spikes (Java 11+)
- **Plugin API**: Extensible API for third-party developers
- **PlaceholderAPI Support**: 30+ placeholders for displaying server metrics

---

## Installation

1. **Download** the latest XreatOptimizer JAR file
2. **Place** the JAR file in your server's `plugins/` folder
3. **Restart** your server (do NOT use `/reload`)
4. **Configure** (optional) - The plugin works with default settings
5. **Monitor** performance and adjust configuration as needed

### Requirements

- **Minecraft Server**: Spigot, Paper, or Purpur (versions 1.8 - 1.21.10)
- **Java Version**: Java 11 or higher (Java 17+ recommended)
- **RAM**: Minimum 2GB (4GB+ recommended)

---

## Recommended Configuration

For optimal performance with minimal plugin overhead, use this configuration:

```yaml
# XreatOptimizer - Recommended Configuration
# This configuration balances optimization effectiveness with low plugin resource usage

general:
  auto_mode: true                      # Enable automatic optimization profile switching
  initial_profile: AUTO                # Let the plugin decide the best profile
  broadcast_interval_minutes: 30       # Status broadcasts every 30 minutes

optimization:
  tps_thresholds:
    light: 19.5                        # Switch to LIGHT profile above 19.5 TPS
    normal: 18                         # Switch to NORMAL profile above 18 TPS
    aggressive: 16                     # Switch to AGGRESSIVE profile above 16 TPS
  entity_limits:
    passive: 250                       # Increased for servers with spawner plugins
    hostile: 200                       # Increased for mob farms
    item: 1500                         # Increased for item-heavy plugins

hibernate:
  enabled: false                       # Disable by default (can cause chunk issues)
  radius: 64                           # Radius around players to keep chunks active

pregen:
  max_threads: 2                       # Threads for chunk pregeneration
  default_speed: 100                   # Chunk generation speed

empty_server:
  enabled: true                        # Enable empty server optimizations
  delay_seconds: 30                    # Wait 30 seconds before optimizing
  freeze_time: true                    # Freeze time at noon when empty
  min_view_distance: 2                 # Minimum view distance when empty
  min_simulation_distance: 2           # Minimum simulation distance when empty

item_removal:
  lifetime_seconds: 600                # Items despawn after 10 minutes
  warning_seconds: 10                  # Show countdown in last 10 seconds

# Advanced Settings
clear_interval_seconds: 300            # Entity cleanup every 5 minutes
memory_reclaim_threshold_percent: 80   # Trigger cleanup at 80% memory
enable_stack_fusion: true              # Combine nearby dropped items/XP
compress_ram_cache: true               # Enable RAM compression
auto_tune: true                        # Enable statistical auto-tuning

# Predictive Optimization (v1.2.0+)
predictive_optimization:
  enabled: true                        # Enable predictive performance management
  forecast_horizon_seconds: 60         # Forecast 60 seconds ahead
  confidence_threshold: 0.7            # Confidence threshold for predictions

# Anomaly Detection (v1.2.0+)
anomaly_detection:
  enabled: true                        # Enable real-time anomaly detection

# JFR Profiling (v1.2.0+) - Requires Java 11+
jfr:
  enabled: false                       # Disabled by default (enable for debugging)
  auto_record_on_lag: true             # Auto-record during lag spikes
  recording_duration_seconds: 60       # Recording duration

# Prometheus Metrics (v1.2.0+)
metrics:
  prometheus:
    enabled: false                     # Disabled by default (security)
    port: 9090                         # Metrics endpoint port
    bind_address: "127.0.0.1"          # Localhost only for security

# Web Dashboard
web_dashboard:
  enabled: false                       # Disabled by default (security)
  port: 8080                           # Dashboard port
  bind_address: "127.0.0.1"            # Localhost only for security
```

### Configuration Notes

- **Hibernation**: Disabled by default as it can cause chunk loading issues on some servers
- **JFR/Metrics/Dashboard**: Disabled by default for security; enable only if needed
- **Entity Limits**: Increased for servers using spawner plugins (Virtual spawner, Lpx, etc.)
- **Predictive Optimization**: Helps prevent lag before it happens
- **Anomaly Detection**: Catches issues like memory leaks and entity explosions early

---

## Configuration

XreatOptimizer generates a comprehensive `config.yml` file with all customization options. See the [Recommended Configuration](#recommended-configuration) section above for optimal settings.

### Configuration Examples

#### High-Performance Server (Strong Hardware)
```yaml
general:
  auto_mode: true
  initial_profile: LIGHT

optimization:
  tps_thresholds:
    light: 19.8
    normal: 18.5
    aggressive: 17.0
  entity_limits:
    passive: 400
    hostile: 300
    item: 2000
```

#### Budget Server (Limited Resources)
```yaml
general:
  auto_mode: true
  initial_profile: AGGRESSIVE

optimization:
  tps_thresholds:
    light: 19.0
    normal: 17.0
    aggressive: 15.0
  entity_limits:
    passive: 100
    hostile: 75
    item: 500

empty_server:
  delay_seconds: 15
  min_view_distance: 1
```

---

## Commands

### Main Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/xreatopt` | `xreatopt.view` | Main command for XreatOptimizer |
| `/xreatopt stats` | `xreatopt.view` | View server performance statistics |
| `/xreatopt boost` | `xreatopt.admin` | Trigger immediate optimization cycle |
| `/xreatopt pregen <world> <radius> [speed]` | `xreatopt.admin` | Generate chunks asynchronously |
| `/xreatopt purge` | `xreatopt.admin` | Unload unused chunks and remove excess entities |
| `/xreatopt reload` | `xreatopt.admin` | Reload plugin configuration |
| `/xreatopt report` | `xreatopt.admin` | Generate detailed performance report |
| `/xreatopt clearcache` | `xreatopt.admin` | Clear RAM caches and suggest garbage collection |
| `/xreatgui` | `xreatopt.view` | Open interactive GUI |
| `/xreatreport` | `xreatopt.admin` | Alias for `/xreatopt report` |

### Command Aliases

- `/xreat` - Alias for `/xreatopt`
- `/xopt` - Alias for `/xreatopt`
- `/xoptgui` - Alias for `/xreatgui`

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `xreatopt.view` | View statistics and use GUI | `true` (all players) |
| `xreatopt.admin` | Full access to admin commands | `op` (operators only) |

---

## Optimization Profiles

XreatOptimizer features 5 optimization profiles that automatically switch based on server TPS:

| Profile | TPS Range | Description |
|---------|-----------|-------------|
| **LIGHT** | > 19.5 TPS | Minimal optimizations for high-performance servers |
| **NORMAL** | 18-19.5 TPS | Balanced optimization for most servers |
| **AGGRESSIVE** | 16-18 TPS | Enhanced optimizations for servers under load |
| **EMERGENCY** | < 16 TPS | Maximum optimizations for crisis situations |
| **AUTO** | Any | Automatically selects the best profile based on TPS |

### Profile Selection Logic

The AUTO profile (default) switches between optimization levels based on:
1. **TPS (Ticks Per Second)**: Primary factor for profile selection
2. **Memory Usage**: Increases optimization when memory >80%
3. **Entity Count**: Adjusts entity limits based on current load
4. **Historical Performance**: Statistical analysis learns patterns over time

---

## Performance Expectations

### What to Expect

Performance improvements depend heavily on your specific server configuration:

**Factors Affecting Performance:**
- Server hardware (CPU, RAM, disk speed)
- Number and type of plugins installed
- Player count and activity patterns
- World size and complexity
- Entity counts (mobs, items, vehicles)

**Typical Improvements (varies by server):**
- TPS: +0.5 to +3.0 TPS improvement
- RAM: 100-500 MB saved through optimization
- CPU: 2-10% reduction in CPU usage
- Lag Spikes: 50-80% reduction in frequency

**Empty Server Mode:**
When no players are online, the plugin can reduce resource usage significantly:
- RAM: Can drop to 200-500 MB (from 1.5-3 GB)
- CPU: Can drop to 1-3% (from 10-25%)
- Chunks: Only spawn area kept loaded

### Test Environment (Reference Only)

The following benchmarks were conducted on a specific test server and may not represent your results:

- Server: Intel Xeon E5-2680v4 (14 cores)
- RAM: 16GB DDR4
- Players: 50-80 concurrent
- Plugins: 25+ (WorldGuard, Essentials, Vault, etc.)
- World Size: 15,000 x 15,000 blocks

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Average TPS | 15.2 | 19.8 | +4.6 TPS |
| RAM (Active) | 4.2 GB | 2.1 GB | -50% |
| RAM (Empty) | 3.8 GB | 380 MB | -90% |
| CPU (Active) | 45% | 28% | -17% |
| CPU (Empty) | 22% | 2% | -20% |

**Your results will vary.** These numbers represent a specific configuration and should not be considered guaranteed performance improvements.

---

## Resource Usage

### Plugin Resource Consumption

XreatOptimizer itself uses resources to provide optimization:

**Memory Usage:**
- Base: 80-110 MB
- With all features enabled: 250-350 MB
- Scales with server size and player count

**CPU Usage:**
- Idle: 0.5-1% CPU
- Active optimization: 2-3% CPU
- Net benefit: Saves more CPU than it uses

**TPS Impact:**
- Monitoring: 0.01 TPS
- Optimization cycles: 0.05-0.2 TPS during execution
- Net benefit: Improves TPS by 0.5-3.0 overall

**Disk Space:**
- Plugin JAR: ~840 KB
- Configuration: ~50 KB
- Logs: 10-100 MB (depending on retention)

### Return on Investment

The plugin uses resources but saves more than it consumes:
- **RAM ROI**: Saves 1.5-6x more RAM than it uses
- **CPU ROI**: Saves 2-7x more CPU than it uses
- **TPS ROI**: Improves TPS by 10-40x more than it costs

For detailed resource usage analysis, see [RESOURCE_USAGE.md](RESOURCE_USAGE.md).

---

## Technical Architecture

### System Components

#### Core Systems
1. **XreatOptimizer (Main Class)**: Plugin initialization and manager coordination
2. **OptimizationManager**: Central optimization controller with profile management
3. **PerformanceMonitor**: Real-time metrics collection and analysis
4. **ThreadPoolManager**: Multi-threaded task execution framework
5. **StatisticsStorage**: Historical data persistence

#### Optimization Managers
- **AdvancedEntityOptimizer**: Entity grouping, batching, and AI throttling
- **AdvancedCPURAMOptimizer**: Real-time CPU/RAM monitoring and optimization
- **EmptyServerOptimizer**: Resource reduction for empty servers
- **HibernateManager**: Chunk hibernation and entity AI freezing
- **MemorySaver**: Compressed caching and memory reclamation
- **DynamicViewDistance**: Adaptive view distance management

#### Advanced Systems (v1.2.0+)
- **PredictiveEngine**: Time-series forecasting using Holt-Winters exponential smoothing
- **AnomalyDetector**: Statistical anomaly detection using Z-score and IQR methods
- **JFRIntegration**: Java Flight Recorder profiling integration
- **PrometheusExporter**: Metrics export for Prometheus/Grafana
- **XreatOptimizerAPI**: Plugin API for third-party extensions

#### Utilities & Support
- **VersionAdapter**: Cross-version compatibility layer
- **ConfigReloader**: Hot-reload configuration system
- **SelfProtectionManager**: Security and integrity checks
- **ItemDropTracker**: Timed item removal system

### Async Architecture

Heavy operations run asynchronously to prevent main thread lag:

```
Main Thread (TPS-Critical)
├── Entity ticking
├── Chunk loading/unloading
├── Player events
└── World time/weather

Async Threads (Non-Critical)
├── Chunk pregeneration
├── Statistics collection
├── Performance analysis
├── Statistical auto-tuning
├── Network optimization
└── Entity cleanup
```

---

## Build Instructions

### Prerequisites

- **JDK 11** or higher
- **Gradle** (included via wrapper)
- **Git** (for cloning repository)

### Building with Gradle

```bash
# Clone the repository
git clone https://github.com/XreatLabs/XreatOptimizer.git
cd XreatOptimizer

# Build the plugin
./gradlew clean build

# Or on Windows
gradlew.bat clean build

# Built JAR location
# build/libs/Xreatoptimizer-1.2.0.jar
```

### Development Build

```bash
# Build without tests
./gradlew build -x test

# Clean previous builds
./gradlew clean build
```

---

## Version Compatibility

### Minecraft Version Support

| Version Range | Support Level | Notes |
|---------------|---------------|-------|
| **1.8 - 1.12** | Limited | Core features supported, some advanced features unavailable |
| **1.13 - 1.16** | Full Support | All features available |
| **1.17 - 1.20** | Full Support | Enhanced features with modern API |
| **1.21 - 1.21.10** | Full Support | Latest features with newest API |

### Server Software Compatibility

- **Spigot**: Fully compatible
- **Paper**: Fully compatible (recommended)
- **Purpur**: Fully compatible
- **Pufferfish**: Compatible
- **Airplane**: Compatible

---

## Safety & Gameplay

### Protected Entities

XreatOptimizer NEVER removes or affects:
- Boss mobs (Ender Dragon, Wither)
- Named entities (custom names)
- Tamed pets (wolves, cats, horses, parrots)
- Villagers with trades
- Entities with passengers
- Farm animals in player-built farms

### What Gets Optimized

- Distant entity AI throttling (based on distance from players)
- Dropped items after 10 minutes (with warnings)
- Experience orbs (stack fusion only)
- Stuck arrows (only in extreme cases, 500+ threshold)

For complete safety information, see [SAFETY_GUIDE.md](SAFETY_GUIDE.md).

---

## Branding & License

### Fixed Branding

XreatOptimizer includes hardcoded branding that cannot be removed or modified:

**Announcement Message:**
```
✦ Made by XreatLabs | https://xreatlabs.space - The Ultimate Performance Engine
```

This message is broadcast every 30 minutes and appears in server console, in-game announcements, and command outputs.

### License

**Proprietary License** - All rights reserved by XreatLabs

- You may use this plugin on your Minecraft server
- You may not decompile, modify, or redistribute the plugin
- You may not remove or modify branding elements
- Commercial use is permitted on your own servers

---

## Support & Contact

### Official Channels

- **Website**: [https://xreatlabs.space](https://xreatlabs.space)
- **Developer**: XreatLabs
- **Version**: 1.2.0

### Getting Help

1. Check the [Recommended Configuration](#recommended-configuration) section
2. Review [RESOURCE_USAGE.md](RESOURCE_USAGE.md) for performance expectations
3. Check [SAFETY_GUIDE.md](SAFETY_GUIDE.md) for gameplay safety information
4. Contact XreatLabs through official channels

---

## FAQ

### General Questions

**Q: Will this plugin conflict with other optimization plugins?**
A: XreatOptimizer may conflict with similar plugins like ClearLag, Hibernate, or Chunky. Test compatibility on a development server first.

**Q: Does this work on modded servers?**
A: XreatOptimizer is designed for Bukkit-based servers. Modded servers (Forge, Fabric) are not officially supported.

**Q: Will this affect gameplay or remove player items?**
A: The plugin only removes dropped items after 10 minutes (with warnings). It never touches player inventories, chests, or containers. See [SAFETY_GUIDE.md](SAFETY_GUIDE.md) for details.

**Q: Can I disable the branding messages?**
A: Yes. The Branding messages are Removed.

### Performance Questions

**Q: How much will this improve my server's performance?**
A: Performance improvements vary significantly based on your server configuration. Typical improvements range from +0.5 to +3.0 TPS, with 100-500 MB RAM savings. See [Performance Expectations](#performance-expectations) for details.

**Q: Why did my RAM usage increase after installing the plugin?**
A: The plugin uses 80-350 MB RAM to run its optimization systems. However, it prevents future RAM growth by stopping memory leaks and entity buildup. Net result is lower RAM usage over time.

**Q: Does this work on servers with many plugins?**
A: Yes. XreatOptimizer is designed to work alongside other plugins and can help mitigate performance issues caused by plugin overhead.

### Technical Questions

**Q: Does this modify world files?**
A: No. XreatOptimizer only optimizes runtime behavior and does not modify world save files.

**Q: Is this compatible with backup plugins?**
A: Yes, fully compatible with all backup plugins.

**Q: Can I use this with world management plugins (Multiverse, etc.)?**
A: Yes, XreatOptimizer is compatible with all major world management plugins.

---

## Changelog

### Version 1.2.0 (Current)
- **NEW**: Prometheus metrics exporter for Grafana integration
- **NEW**: Predictive performance management (forecasts lag 30-60s ahead)
- **NEW**: Real-time anomaly detection (memory leaks, entity explosions, chunk thrashing)
- **NEW**: JFR integration for automatic profiling during lag spikes
- **NEW**: Plugin API for third-party extensions
- **NEW**: PlaceholderAPI expansion with 30+ placeholders
- **NEW**: Smart entity AI optimization with importance classification
- **IMPROVED**: Enhanced Discord notifications with rich embeds
- **IMPROVED**: Web dashboard with historical data and charts
- **FIX**: Added 30-second cooldown to anomaly reporting to prevent log spam
- **FIX**: Clarified CPU logging (Process CPU vs System CPU)
- **CHANGE**: Updated README to comply with Modrinth content rules

### Version 1.1.0
- **NEW**: Web Dashboard - Real-time browser-based monitoring interface
- **FIX**: Resolved async thread safety issues with entity counting
- **FIX**: Improved error handling in dashboard API endpoints
- **CHANGE**: Reduced Java requirement from 17 to 11 for broader compatibility

### Version 1.0.0
- Initial release
- Cross-version support (1.8 - 1.21.10)
- Statistical auto-tuning engine
- Empty server optimizer
- Advanced entity optimization
- Smart tick distribution
- Network optimization
- Predictive chunk loading
- Lag spike detection
- Real-time performance monitoring
- GUI management interface

---

**Made by XreatLabs** | https://xreatlabs.space

Compatible with Minecraft 1.8 through 1.21.10
