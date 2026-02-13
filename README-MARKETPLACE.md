<div align="center">

# XreatOptimizer

### A comprehensive performance optimization plugin for Minecraft servers

![Version](https://img.shields.io/badge/Version-1.2.0-00aa00?style=for-the-badge)
![Minecraft](https://img.shields.io/badge/Minecraft-1.8--1.21.10-00aa00?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-11+-00aa00?style=for-the-badge)

**Combines multiple optimization techniques into a single solution**

[Features](#-features) | [Installation](#-installation) | [Commands](#-commands) | [Configuration](#-configuration)

---

</div>

## About XreatOptimizer

XreatOptimizer is a performance optimization plugin that combines entity management, chunk optimization, memory management, and monitoring into one solution. It supports Minecraft 1.8 through 1.21.10.

**Important:** Performance improvements vary significantly based on your server hardware, configuration, installed plugins, player count, and world size. The benchmarks shown represent specific test conditions and may not reflect your results.

---

## Features

### Empty Server Optimizer
Automatically reduces resource usage when no players are online:
- Reduces view distance and unloads distant chunks
- Removes dropped items and unnecessary entities
- Configurable delay before activation (default: 30 seconds)
- Instantly restores normal operation when players join

### Automated Performance Tuning
Uses statistical analysis (EWMA) to adapt optimization strategies:
- Adjusts TPS thresholds based on server patterns
- Modifies entity limits based on available resources
- Increases optimization intensity when memory pressure is detected
- Learns from 60+ data points over time

### Entity Management
- Configurable entity limits (passive, hostile, items)
- Entity culling based on player view distance
- Stack fusion for dropped items and experience orbs
- Pathfinding calculation caching
- Smart AI throttling based on distance from players

### Chunk Optimization
- Async chunk pregeneration
- Predictive chunk loading based on player movement
- Dynamic view distance adjustment based on TPS
- Chunk hibernation system (freezes entity AI in distant chunks)

### Advanced Features (v1.2.0)
- **Predictive Performance Management**: Forecasts performance issues 30-60 seconds ahead
- **Real-time Anomaly Detection**: Detects memory leaks, entity explosions, chunk thrashing
- **JFR Integration**: Automatic Java Flight Recorder profiling during lag spikes
- **Prometheus Metrics**: Export metrics for Grafana integration
- **Plugin API**: Extensible API for third-party developers
- **PlaceholderAPI**: 30+ placeholders for displaying server metrics

### Monitoring & Reporting
- Real-time TPS, memory, CPU, and entity tracking
- Historical performance data storage
- Detailed performance reports with recommendations
- Optional web dashboard for remote monitoring

---

## Installation

1. Download `XreatOptimizer.jar`
2. Place in your `plugins/` folder
3. Restart server (don't use /reload)
4. Configure as needed (works with defaults)

### Requirements
- **Server**: Spigot, Paper, Purpur (or forks)
- **Minecraft**: 1.8 - 1.21.10
- **Java**: 11 or higher (17+ recommended)
- **RAM**: Minimum 2GB (4GB+ recommended)

---

## Commands

| Command | Description |
|---------|-------------|
| `/xreatopt stats` | View performance statistics |
| `/xreatopt boost` | Trigger immediate optimization |
| `/xreatopt pregen <world> <radius>` | Pre-generate chunks |
| `/xreatopt purge` | Clean up entities and chunks |
| `/xreatopt reload` | Reload configuration |
| `/xreatopt report` | Generate detailed performance report |
| `/xreatgui` | Open management GUI |

**Aliases:** `/xreat`, `/xopt`

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `xreatopt.view` | View stats and GUI | Everyone |
| `xreatopt.admin` | All admin commands | OP |

---

## Recommended Configuration

For servers using high-performance plugins (spawners, vehicles, etc.), use this configuration:

```yaml
# Increased limits for servers with many plugins
optimization:
  entity_limits:
    passive: 250      # Increased for spawner plugins
    hostile: 200      # Increased for mob farms
    item: 1500        # Increased for item-heavy plugins

# Predictive optimization (prevents lag before it happens)
predictive_optimization:
  enabled: true
  forecast_horizon_seconds: 60

# Anomaly detection (catches issues early)
anomaly_detection:
  enabled: true

# Disable hibernation by default (can cause chunk issues)
hibernate:
  enabled: false
```

Full configuration guide available in the [GitHub repository](https://github.com/XreatLabz/Xreatoptimizer).

---

## Performance Expectations

**What to Expect:**
- TPS improvements: +0.5 to +3.0 TPS (varies by server)
- RAM savings: 100-500 MB through optimization
- CPU reduction: 2-10% lower CPU usage
- Lag spike reduction: 50-80% fewer spikes

**Empty Server Mode:**
When no players are online, resource usage can drop significantly:
- RAM: Can drop to 200-500 MB (from 1.5-3 GB)
- CPU: Can drop to 1-3% (from 10-25%)
- Chunks: Only spawn area kept loaded

**Factors Affecting Performance:**
- Server hardware (CPU, RAM, disk speed)
- Number and type of plugins installed
- Player count and activity patterns
- World size and complexity
- Entity counts (mobs, items, vehicles)

### Test Environment (Reference Only)

The following benchmarks were conducted on a specific test server:
- Server: Intel Xeon E5-2680v4 (14 cores)
- RAM: 16GB DDR4
- Players: 50-80 concurrent
- Plugins: 25+ (WorldGuard, Essentials, Vault, etc.)

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Average TPS | 15.2 | 19.8 | +4.6 TPS |
| RAM (Active) | 4.2 GB | 2.1 GB | -50% |
| RAM (Empty) | 3.8 GB | 380 MB | -90% |
| CPU (Active) | 45% | 28% | -17% |

**Your results will vary.** These numbers represent a specific configuration and should not be considered guaranteed improvements.

---

## Resource Usage

The plugin itself uses resources to provide optimization:

**Memory Usage:**
- Base: 80-110 MB
- With all features: 250-350 MB
- Scales with server size

**CPU Usage:**
- Idle: 0.5-1% CPU
- Active optimization: 2-3% CPU
- Net benefit: Saves more than it uses

**TPS Impact:**
- Monitoring: 0.01 TPS
- Optimization cycles: 0.05-0.2 TPS
- Net benefit: Improves TPS by 0.5-3.0 overall

---

## Optimization Profiles

The plugin automatically switches between profiles based on server TPS:

| Profile | When Active | What It Does |
|---------|-------------|--------------|
| **LIGHT** | TPS > 19.5 | Minimal optimizations |
| **NORMAL** | TPS 18-19.5 | Balanced optimization |
| **AGGRESSIVE** | TPS 16-18 | Enhanced cleanup |
| **EMERGENCY** | TPS < 16 | Maximum optimization |
| **AUTO** | Any | Automatically selects best profile |

---

## Safety & Gameplay

### Protected Entities (NEVER Removed)
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

---

## Compatibility

### Supported Server Software
- Spigot
- Paper (Recommended)
- Purpur
- Pufferfish
- Airplane

### Works With
- All world management plugins (Multiverse, etc.)
- All economy plugins
- All permission plugins
- All protection plugins

### May Conflict With
- ClearLag (similar functionality)
- Other entity limiters
- Other chunk managers

Test compatibility on a development server first if using similar plugins.

---

## FAQ

**Q: Will this delete player items?**
> No. Only removes dropped ground items after 10 minutes (with warnings). Never touches inventories or containers.

**Q: Does it modify world files?**
> No. All optimizations are runtime-only.

**Q: How much will this improve my server's performance?**
> Performance improvements vary significantly based on your server configuration. Typical improvements range from +0.5 to +3.0 TPS with 100-500 MB RAM savings. See "Performance Expectations" section for details.

**Q: Why did my RAM usage increase after installing the plugin?**
> The plugin uses 80-350 MB RAM to run its optimization systems. However, it prevents future RAM growth by stopping memory leaks and entity buildup. Net result is lower RAM usage over time.

**Q: Can I use this with many other plugins?**
> Yes. XreatOptimizer is designed to work alongside other plugins and can help mitigate performance issues caused by plugin overhead.

---

## Changelog

### v1.2.0 (Current)
- **NEW**: Prometheus metrics exporter for Grafana integration
- **NEW**: Predictive performance management (forecasts lag 30-60s ahead)
- **NEW**: Real-time anomaly detection (memory leaks, entity explosions, chunk thrashing)
- **NEW**: JFR integration for automatic profiling during lag spikes
- **NEW**: Plugin API for third-party extensions
- **NEW**: PlaceholderAPI expansion with 30+ placeholders
- **NEW**: Smart entity AI optimization with importance classification
- **IMPROVED**: Enhanced Discord notifications
- **IMPROVED**: Web dashboard with historical data
- **FIX**: Added 30-second cooldown to anomaly reporting (prevents log spam)
- **FIX**: Clarified CPU logging (Process CPU vs System CPU)

### v1.1.0
- **NEW**: Web Dashboard with real-time monitoring
- **FIX**: Async thread safety improvements
- **FIX**: Better error handling
- **CHANGE**: Java 11 minimum (was 17)

### v1.0.0
- Initial release

---

## Documentation

- **Full README**: [GitHub Repository](https://github.com/XreatLabz/Xreatoptimizer)
- **Resource Usage Guide**: [RESOURCE_USAGE.md](https://github.com/XreatLabz/Xreatoptimizer/blob/main/RESOURCE_USAGE.md)
- **Safety Guide**: [SAFETY_GUIDE.md](https://github.com/XreatLabz/Xreatoptimizer/blob/main/SAFETY_GUIDE.md)

---

## Support

- **Website**: [xreatlabs.space](https://xreatlabs.space)
- **Developer**: XreatLabs

---

<div align="center">

### Made by XreatLabs

**Compatible with Minecraft 1.8 - 1.21.10**

</div>
