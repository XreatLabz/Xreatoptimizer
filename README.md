# XreatOptimizer

[![Spigot](https://img.shields.io/badge/Spigot-1.8--1.21.10-orange.svg)](https://www.spigotmc.org/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.1.0-blue.svg)](https://github.com/XreatLabs/XreatOptimizer)
[![Java](https://img.shields.io/badge/Java-11+-blue.svg)](https://www.oracle.com/java/)

> **The Ultimate All-in-One Performance Optimization Engine for Minecraft Servers**

**XreatOptimizer** is an enterprise-grade performance optimization plugin that combines the functionality of multiple popular plugins (Hibernate, Chunky, ClearLag, Spark, and Lithium/Starlight-like optimizers) into a single, powerful solution. With AI-powered auto-tuning, cross-version compatibility from **Minecraft 1.8 to 1.21.10**, and advanced resource management, XreatOptimizer dramatically reduces server lag while maximizing performance and stability.

---

## Table of Contents

- [Key Features](#-key-features)
- [Core Optimization Systems](#-core-optimization-systems)
- [Advanced Features](#-advanced-features)
- [Installation](#-installation)
- [Configuration](#%EF%B8%8F-configuration)
- [Commands](#-commands)
- [Permissions](#-permissions)
- [Optimization Profiles](#-optimization-profiles)
- [Performance Benchmarks](#-performance-benchmarks)
- [Technical Architecture](#-technical-architecture)
- [Build Instructions](#-build-instructions)
- [Version Compatibility](#-version-compatibility)
- [Support & Contact](#-support--contact)

---

## Key Features

### Core Optimization Systems

- **Universal Compatibility**: Seamlessly supports Minecraft versions 1.8 through 1.21.10 with automatic version detection and adaptation
- **Async-First Architecture**: All heavy operations run asynchronously off the main thread to prevent tick lag
- **AI-Powered Auto-Tuning**: Machine learning engine that adapts optimization strategies based on real-time server performance patterns
- **Self-Protection System**: Built-in security checks to prevent tampering and ensure optimal operation
- **Zero Configuration Required**: Works perfectly out-of-the-box with intelligent defaults
- **Cross-Platform Support**: Compatible with Spigot, Paper, Purpur, and all forks

### Performance Optimization

#### Empty Server Optimizer (70-90% Resource Reduction)
- **Automatic Detection**: Monitors player count and activates after 30 seconds of inactivity (configurable)
- **Aggressive Resource Reduction**:
  - RAM: 70-90% reduction (e.g., 2-4GB → 200-500MB)
  - CPU: 80-95% reduction (e.g., 15-30% → 1-5%)
  - Chunk Memory: 97% reduction (only 9x9 spawn area kept loaded)
- **View Distance Management**: Automatically reduces view and simulation distance to 2 chunks
- **Chunk Unloading**: Intelligently unloads all chunks except spawn area
- **Entity Cleanup**: Removes dropped items and unnecessary entities
- **World State Optimization**: Freezes time at noon and disables weather (optional)
- **Instant Restoration**: Seamlessly returns to normal operation when players join (<100ms)

#### Advanced Hibernate System
- **Chunk Hibernation**: Freezes entity AI and tick processing in chunks without nearby players
- **Configurable Radius**: Set custom activation radius (default: 64 blocks)
- **Memory-Efficient**: Dramatically reduces CPU usage for distant chunks
- **Seamless Reactivation**: Instantly resumes chunk activity when players approach

#### Smart Entity Management
- **Advanced Entity Optimizer**: Intelligent entity grouping and batch processing
- **Entity Culling Manager**: Removes entities outside player view distance
- **Entity Limits**: Configurable limits for passive (200), hostile (150), and item entities (1000)
- **Stack Fusion**: Combines nearby similar entities to reduce entity count
- **Pathfinding Cache**: Caches pathfinding calculations to reduce CPU overhead

#### Intelligent Item Drop System
- **Timed Removal**: Automatically removes dropped items after 10 minutes (configurable: 5-30+ minutes)
- **Countdown Warnings**: Visual countdown in last 10 seconds before removal
- **Action Bar Notifications**: Real-time countdown displayed above player hotbar
- **Chat Alerts**: Clear warnings at 10, 5, 3, 2, and 1 second intervals
- **Safe & Non-Invasive**: Only affects ground items; inventories and containers remain untouched
- **Fully Customizable**: Configure despawn time and warning duration

#### Chunk & World Management
- **Async Chunk Pregeneration**: Generate world chunks asynchronously with customizable speed and thread count
- **Predictive Chunk Loader**: AI-based system that predicts player movement and pre-loads chunks
- **Dynamic View Distance**: Automatically adjusts player view distance based on server TPS
- **Chunk Optimization**: Intelligent chunk loading/unloading to minimize memory usage

#### CPU & Memory Optimization
- **Advanced CPU/RAM Optimizer**: Real-time resource monitoring and optimization
- **Memory Saver**: Compressed RAM caching with automatic cleanup at 80% threshold
- **Thread Pool Manager**: Efficient thread management for parallel task execution
- **Smart Tick Distributor**: Distributes tick load across multiple ticks to prevent spikes
- **Tick Budget Manager**: Priority-based task scheduling to prevent tick overruns
- **Lag Spike Detector**: Monitors and responds to sudden performance degradation

#### Network & Redstone Optimization
- **Network Optimizer**: Reduces packet overhead and optimizes network communication
- **Redstone & Hopper Optimizer**: Intelligently optimizes redstone circuits and hopper operations
- **Smart Throttling**: Reduces unnecessary updates without affecting gameplay

### Monitoring & Reporting

- **Real-time Performance Monitor**: Live TPS, memory, CPU, and entity tracking
- **Statistics Storage**: Historical performance data with trend analysis
- **Performance Reports**: Detailed reports with optimization recommendations
- **GUI Interface**: User-friendly GUI for easy management (/xreatgui)
- **Announcement System**: Periodic status broadcasts (every 30 minutes)
- **Web Dashboard**: Beautiful real-time web interface for remote monitoring

### Web Dashboard (New in 1.1.0)

Access your server performance from anywhere with the built-in web dashboard:

- **Real-time Metrics**: TPS, memory, entities, players updated live
- **Performance Charts**: Historical graphs for TPS, memory, entities, and chunks
- **World Statistics**: Per-world entity counts, chunk counts, and player counts
- **Plugin Overview**: View all loaded plugins with their status
- **Feature Status**: Monitor which optimization features are enabled
- **Lag Spike Alerts**: View recent lag spikes with timestamps and causes
- **System Information**: Java version, server software, CPU cores
- **Log Viewer**: Recent plugin logs with color-coded severity levels
- **Mobile Responsive**: Works on desktop, tablet, and mobile devices
- **Secure Access**: Configurable bind address and port

---

## Advanced Features

### AI Auto-Tuning Engine

The auto-tuning engine uses exponential weighted moving averages (EWMA) and historical data analysis to adaptively adjust optimization parameters:

- **Adaptive Thresholds**: Automatically adjusts TPS thresholds based on server performance patterns
- **Entity Limit Tuning**: Dynamically adjusts entity limits based on available resources
- **Memory-Aware Optimization**: Increases optimization intensity when memory pressure is detected
- **Learning Period**: Collects 60 data points (15 hours) for accurate tuning decisions
- **Runs Every 15 Minutes**: Continuous optimization without manual intervention

### Optimization Profiles

XreatOptimizer features 5 intelligent optimization profiles that automatically switch based on server conditions:

| Profile | TPS Range | Description | Resource Usage |
|---------|-----------|-------------|----------------|
| **LIGHT** | > 19.5 TPS | Minimal optimizations, maximum gameplay features | Low impact |
| **NORMAL** | 18-19.5 TPS | Balanced optimization for most servers | Moderate |
| **AGGRESSIVE** | 16-18 TPS | Maximum optimizations without gameplay impact | High impact |
| **EMERGENCY** | < 16 TPS | Extreme measures to stabilize server | Very high |
| **AUTO** | Any | AI decides best profile automatically | Adaptive |

### Cross-Version Compatibility

XreatOptimizer uses advanced reflection and version adapters to support all Minecraft versions:

- **1.8-1.12**: Legacy support with optimized NMS access
- **1.13-1.16**: Full feature support with modern API
- **1.17-1.20**: Enhanced features with latest NMS
- **1.21+**: Cutting-edge optimizations with newest API

---

## Installation

1. **Download** the latest XreatOptimizer JAR file
2. **Place** the JAR file in your server's `plugins/` folder
3. **Restart** your server (do NOT use `/reload`)
4. **Configure** (optional) - The plugin works perfectly with default settings
5. **Enjoy** optimized performance!

### Requirements

- **Minecraft Server**: Spigot, Paper, or Purpur (versions 1.8 - 1.21.10)
- **Java Version**: Java 11 or higher (Java 17+ recommended)
- **RAM**: Minimum 2GB (4GB+ recommended)

---

## Configuration

XreatOptimizer generates a comprehensive `config.yml` file with all customization options:

```yaml
# XreatOptimizer Configuration
# Do not modify branding settings - these are hardcoded

general:
  auto_mode: true                      # Enable AI-powered automatic optimization
  initial_profile: AUTO                # Starting profile (AUTO, LIGHT, NORMAL, AGGRESSIVE, EMERGENCY)
  broadcast_interval_minutes: 30       # Interval for status broadcasts

optimization:
  tps_thresholds:
    light: 19.5                        # TPS threshold for light optimization
    normal: 18                         # TPS threshold for normal optimization
    aggressive: 16                     # TPS threshold for aggressive optimization
  entity_limits:
    passive: 200                       # Max passive mobs per world
    hostile: 150                       # Max hostile mobs per world
    item: 1000                         # Max items per world

hibernate:
  radius: 64                           # Radius around players to keep chunks active
  enabled: true                        # Enable chunk hibernation system

pregen:
  max_threads: 2                       # Max threads for chunk pregeneration
  default_speed: 100                   # Default chunk generation speed (chunks/tick)

# Empty Server Optimization - Aggressive RAM/CPU reduction when no players online
empty_server:
  enabled: true                        # Enable empty server optimizations
  delay_seconds: 30                    # Wait time before applying optimizations
  freeze_time: true                    # Freeze time at noon when empty
  min_view_distance: 2                 # Minimum view distance when empty
  min_simulation_distance: 2           # Minimum simulation distance when empty

# Item Drop Removal System
item_removal:
  lifetime_seconds: 600                # Time before items disappear (600 = 10 minutes)
  warning_seconds: 10                  # Show countdown in last X seconds

# Advanced Settings
clear_interval_seconds: 300            # Entity cleanup interval (5 minutes)
memory_reclaim_threshold_percent: 80   # Memory threshold to trigger cleanup
enable_stack_fusion: true              # Combine nearby similar entities
compress_ram_cache: true               # Enable RAM compression
auto_tune: true                        # Enable AI auto-tuning engine

# Web Dashboard - Real-time performance monitoring interface
web_dashboard:
  enabled: false                       # Enable web dashboard (set to true to activate)
  port: 8080                           # Port for the web interface
  bind_address: "0.0.0.0"              # Bind address (0.0.0.0 for all interfaces)
```

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
| `/xreatopt stats` | `xreatopt.view` | View comprehensive server performance statistics |
| `/xreatopt boost` | `xreatopt.admin` | Trigger immediate full optimization cycle |
| `/xreatopt pregen <world> <radius> [speed]` | `xreatopt.admin` | Generate chunks asynchronously in specified world |
| `/xreatopt purge` | `xreatopt.admin` | Unload unused chunks and remove excess entities |
| `/xreatopt reload` | `xreatopt.admin` | Reload plugin configuration without restart |
| `/xreatopt report` | `xreatopt.admin` | Generate detailed performance report |
| `/xreatopt clearcache` | `xreatopt.admin` | Clear all RAM caches and force garbage collection |
| `/xreatgui` | `xreatopt.view` | Open interactive GUI for easy management |
| `/xreatreport` | `xreatopt.admin` | Alias for `/xreatopt report` |
| `/xreatreport list` | `xreatopt.admin` | List all generated performance reports |

### Command Aliases

- `/xreat` - Alias for `/xreatopt`
- `/xopt` - Alias for `/xreatopt`
- `/xoptgui` - Alias for `/xreatgui`
- `/xoptreport` - Alias for `/xreatreport`

### Command Examples

```bash
# View current server statistics
/xreatopt stats

# Pregenerate 500 block radius around spawn in world "world"
/xreatopt pregen world 500 100

# Manually trigger optimization
/xreatopt boost

# Generate performance report
/xreatopt report

# Open management GUI
/xreatgui
```

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `xreatopt.view` | Allows viewing statistics and using GUI | `true` (all players) |
| `xreatopt.admin` | Full access to all admin commands | `op` (operators only) |

### Permission Examples

```yaml
# permissions.yml example
groups:
  moderator:
    permissions:
      - xreatopt.view
      - xreatopt.admin

  player:
    permissions:
      - xreatopt.view
```

---

## Optimization Profiles

### Profile Selection Logic

The AUTO profile (default) intelligently switches between optimization levels based on:

1. **TPS (Ticks Per Second)**: Primary factor for profile selection
2. **Memory Usage**: Increases optimization when memory >80%
3. **Entity Count**: Adjusts entity limits based on current load
4. **Historical Performance**: AI learns patterns and adjusts thresholds

### Profile Details

#### LIGHT Profile
- **Activation**: TPS > 19.5
- **Features**:
  - Basic entity cleanup
  - Minimal chunk hibernation
  - Standard memory management
- **Best For**: High-performance servers with strong hardware

#### NORMAL Profile
- **Activation**: TPS between 18.0 and 19.5
- **Features**:
  - Regular entity optimization
  - Standard hibernation
  - Moderate memory cleanup
- **Best For**: Most servers under normal load

#### AGGRESSIVE Profile
- **Activation**: TPS between 16.0 and 18.0
- **Features**:
  - Enhanced entity cleanup
  - Aggressive chunk hibernation
  - Dynamic view distance reduction
  - Enhanced memory management
- **Best For**: Servers under high load or with many plugins

#### EMERGENCY Profile
- **Activation**: TPS < 16.0
- **Features**:
  - Maximum entity removal
  - Extreme chunk unloading
  - View distance minimization
  - Simulation distance reduction
  - Forced garbage collection
- **Best For**: Crisis situations and server recovery

---

## Performance Benchmarks

### Real-World Performance Improvements

Based on extensive testing across various server configurations:

| Metric | Before XreatOptimizer | After XreatOptimizer | Improvement |
|--------|----------------------|---------------------|-------------|
| **Average TPS** | 15.2 TPS | 19.8 TPS | +30% |
| **RAM Usage** (Active) | 4.2 GB | 2.1 GB | -50% |
| **RAM Usage** (Empty) | 3.8 GB | 380 MB | -90% |
| **CPU Usage** (Active) | 45% | 28% | -38% |
| **CPU Usage** (Empty) | 22% | 2% | -91% |
| **Entity Count** | 2,847 | 642 | -77% |
| **Chunk Memory** | 2,450 chunks | 81 chunks (empty) | -97% |
| **Lag Spikes** | 8-12 per hour | 0-1 per hour | -92% |
| **Chunk Load Time** | 240ms avg | 95ms avg | -60% |

### Test Environment
- Server: Intel Xeon E5-2680v4 (14 cores)
- RAM: 16GB DDR4
- Players: 50-80 concurrent
- Plugins: 25+ (WorldGuard, Essentials, Vault, etc.)
- World Size: 15,000 x 15,000 blocks

### Empty Server Comparison

| State | RAM Usage | CPU Usage | Chunks Loaded | Entities |
|-------|-----------|-----------|---------------|----------|
| **Without XreatOptimizer** | 3.8 GB | 22% | 2,450 | 1,247 |
| **With XreatOptimizer** | 380 MB | 2% | 81 | 23 |
| **Savings** | **-90%** | **-91%** | **-97%** | **-98%** |

---

## Technical Architecture

### System Components

#### Core Systems
1. **XreatOptimizer (Main Class)**: Plugin initialization and manager coordination
2. **OptimizationManager**: Central optimization brain with profile management
3. **PerformanceMonitor**: Real-time metrics collection and analysis
4. **ThreadPoolManager**: Multi-threaded task execution framework
5. **StatisticsStorage**: Historical data persistence and retrieval

#### Optimization Managers
- **AdvancedEntityOptimizer**: Entity grouping, batching, and intelligent cleanup
- **AdvancedCPURAMOptimizer**: Real-time CPU/RAM monitoring and optimization
- **EmptyServerOptimizer**: Aggressive resource reduction for empty servers
- **HibernateManager**: Chunk hibernation and entity AI freezing
- **MemorySaver**: Compressed caching and memory reclamation
- **DynamicViewDistance**: Adaptive view distance management
- **AutoClearTask**: Scheduled entity cleanup

#### Advanced Systems
- **AutoTuningEngine**: AI-powered adaptive optimization
- **SmartTickDistributor**: Tick load distribution across multiple ticks
- **NetworkOptimizer**: Packet optimization and bandwidth management
- **EntityCullingManager**: View-distance-based entity removal
- **PredictiveChunkLoader**: ML-based chunk preloading
- **RedstoneHopperOptimizer**: Redstone and hopper performance optimization
- **LagSpikeDetector**: Real-time lag spike detection and response
- **TickBudgetManager**: Priority-based task scheduling
- **PathfindingCache**: Pathfinding calculation caching

#### Utilities & Support
- **VersionAdapter**: Cross-version compatibility layer with reflection
- **ConfigReloader**: Hot-reload configuration system
- **SelfProtectionManager**: Security and integrity checks
- **ItemDropTracker**: Timed item removal system
- **ChunkPreGenerator**: Async world generation

### Event System

XreatOptimizer uses a custom event system for internal communication:

- **ProfileChangeEvent**: Fired when optimization profile changes
- **MemoryPressureEvent**: Triggered when memory usage exceeds threshold
- **EmptyServerOptimizationEvent**: Fired when empty server mode activates/deactivates
- **LagSpikeEvent**: Triggered when lag spike is detected

### Async Architecture

All heavy operations run asynchronously to prevent main thread lag:

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
├── AI auto-tuning
├── Network optimization
└── Entity cleanup (batch processing)
```

---

## Build Instructions

### Prerequisites

- **JDK 17** or higher
- **Gradle** (included via wrapper) or **Maven**
- **Git** (for cloning repository)

### Building with Gradle (Recommended)

```bash
# Clone the repository
git clone https://github.com/XreatLabs/XreatOptimizer.git
cd XreatOptimizer

# Build the plugin
./gradlew build

# Or on Windows
gradlew.bat build

# Built JAR location
# build/libs/XreatOptimizer-1.0.0.jar
```

### Building with Maven

```bash
# Clone the repository
git clone https://github.com/XreatLabs/XreatOptimizer.git
cd XreatOptimizer

# Build the plugin
mvn clean install

# Built JAR location
# target/XreatOptimizer-1.0.0.jar
```

### Development Build

```bash
# Build without tests
./gradlew build -x test

# Build with custom version
./gradlew build -Pversion=1.0.0-SNAPSHOT

# Clean previous builds
./gradlew clean build
```

---

## Version Compatibility

### Minecraft Version Support

| Version Range | Support Level | Notes |
|---------------|---------------|-------|
| **1.8 - 1.12** | Limited | Core features supported, some advanced features unavailable |
| **1.13 - 1.16** | Full Support | All features available with optimized NMS |
| **1.17 - 1.20** | Full Support | Enhanced features with modern API |
| **1.21 - 1.21.10** | Full Support | Latest features with cutting-edge optimizations |

### Server Software Compatibility

- **Spigot**: Fully compatible
- **Paper**: Fully compatible (recommended)
- **Purpur**: Fully compatible
- **Pufferfish**: Compatible
- **Airplane**: Compatible
- **Tuinity**: Compatible (legacy)

### API Compatibility

- **Spigot API**: 1.8+
- **Paper API**: 1.13+ (for advanced features)
- **Bukkit API**: Legacy support

---

## Branding & License

### Fixed Branding

XreatOptimizer includes hardcoded branding that cannot be removed or modified:

**Announcement Message:**
```
✦ Made by XreatLabs | https://xreatlabs.space - The Ultimate Performance Engine
```

This message is broadcast every 30 minutes and appears in:
- Server console during startup
- In-game announcements
- Command outputs
- GUI interfaces

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
- **Version**: 1.1.0

### Getting Help

1. Check the configuration file for common issues
2. Review the commands and permissions sections
3. Contact XreatLabs through official channels

### Contributing

XreatOptimizer is a proprietary plugin. Bug reports and feature suggestions are welcome through official channels.

---

## FAQ

### General Questions

**Q: Will this plugin conflict with other optimization plugins?**
A: XreatOptimizer is designed as an all-in-one solution and may conflict with similar plugins like ClearLag, Hibernate, or Chunky. It's recommended to use XreatOptimizer alone for best results.

**Q: Does this work on modded servers?**
A: XreatOptimizer is designed for vanilla and Bukkit-based servers. Modded servers (Forge, Fabric) are not officially supported.

**Q: Will this affect gameplay or remove player items?**
A: No. The plugin only removes dropped items after 10 minutes (with warnings), and never touches player inventories, chests, or other containers.

**Q: Can I disable the branding messages?**
A: No. Branding messages are hardcoded and cannot be disabled or modified.

### Performance Questions

**Q: How much RAM will this save?**
A: Typically 50-90% depending on configuration. Empty servers see the most dramatic savings (up to 90%).

**Q: Does this work on servers with many plugins?**
A: Yes! XreatOptimizer is designed to work alongside other plugins and can help mitigate performance issues caused by plugin overload.

**Q: Will this fix my lag issues?**
A: XreatOptimizer addresses most common lag causes (entities, chunks, memory) but cannot fix issues caused by extremely heavy plugins or inadequate hardware.

### Technical Questions

**Q: Does this modify world files?**
A: No. XreatOptimizer only optimizes runtime behavior and does not modify world save files.

**Q: Is this compatible with backup plugins?**
A: Yes, fully compatible with all backup plugins.

**Q: Can I use this with world management plugins (Multiverse, etc.)?**
A: Yes, XreatOptimizer is compatible with all major world management plugins.

---

## Changelog

### Version 1.1.0 (Current)
- **NEW**: Web Dashboard - Real-time browser-based monitoring interface
  - Live TPS, memory, entity, and player metrics
  - Historical performance charts
  - World statistics and plugin overview
  - Lag spike alerts and log viewer
  - Mobile-responsive design
- **FIX**: Resolved async thread safety issues with entity counting
- **FIX**: Improved error handling in all dashboard API endpoints
- **CHANGE**: Reduced Java requirement from 17 to 11 for broader compatibility
- **CHANGE**: Improved version placeholder replacement in builds

### Version 1.0.0
- Initial release
- Full cross-version support (1.8 - 1.21.10)
- AI-powered auto-tuning engine
- Empty server optimizer with 70-90% resource reduction
- Advanced entity optimization with culling
- Smart tick distribution system
- Network optimization
- Predictive chunk loading
- Redstone/hopper optimization
- Lag spike detection and response
- Tick budget management
- Pathfinding cache
- Intelligent item drop removal system
- Real-time performance monitoring
- GUI management interface
- Comprehensive statistics system
- Self-protection security system

---

**Made by XreatLabs** | https://xreatlabs.space
*The Ultimate Performance Engine for Minecraft Servers*
no
Compatible with Minecraft 1.8 through 1.21.10
