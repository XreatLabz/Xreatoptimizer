# XreatOptimizer

XreatOptimizer is a Bukkit/Spigot performance helper for Minecraft servers. It focuses on practical runtime optimizations, conservative defaults, and lightweight monitoring instead of aggressive entity deletion.

**Project target:** Minecraft 1.8-1.21.10  
**Server types:** Spigot, Paper, Purpur, and compatible forks  
**Java:** 11+

## What it does

- Monitors TPS, memory, entities, and loaded chunks
- Automatically adjusts optimization profile based on server load
- Dynamically lowers view distance when the server is under pressure
- Uses a low-power mode when the server is empty
- Merges nearby dropped items and XP orbs to reduce entity clutter
- Offers optional item cleanup with warnings before despawn
- Includes optional dashboard, Prometheus metrics, predictive loading, and conservative advanced optimization modules

## Safe defaults

XreatOptimizer ships with the riskier features turned off by default.

**Disabled by default**
- item removal
- auto clear
- hibernation
- predictive loading
- redstone/hopper optimization
- dashboard and Prometheus export
- AI throttling
- automatic chunk unloading

**Enabled by default**
- performance monitoring
- automatic profile management
- memory saver
- dynamic view distance
- empty-server low-power mode
- item/XP stack fusion

## Installation

1. Put the jar in `plugins/`
2. Start or restart the server
3. Edit `plugins/XreatOptimizer/config.yml` if needed
4. Use `/xreatopt stats` to verify it is running

> Avoid `/reload` for production server changes. Use a full restart when possible.

## Commands

| Command | Description |
|---|---|
| `/xreatopt stats` | Show live performance and feature status |
| `/xreatopt report` | Show a quick performance summary |
| `/xreatopt boost` | Run a safe manual optimization pass |
| `/xreatopt pregen <world> <radius> <speed>` | Pre-generate chunks in controlled batches |
| `/xreatopt purge` | Clear runtime caches and remove excess arrows if enabled |
| `/xreatopt reload` | Reload config and refresh runtime systems |
| `/xreatopt clearcache` | Clear cached chunk metadata |
| `/xreatopt dashboard` | Show dashboard status and connection info |
| `/xreatgui` | Open the control panel GUI |

**Aliases:** `/xreat`, `/xopt`, `/xoptgui`

## Permissions

| Permission | Default | Description |
|---|---|---|
| `xreatopt.view` | op | View stats and use the GUI |
| `xreatopt.admin` | op | Use admin commands |

## Configuration highlights

Important defaults from `config.yml`:

```yml
empty_server:
  enabled: true

item_removal:
  enabled: false

auto_clear:
  enabled: false

hibernate:
  enabled: false

predictive_loading:
  enabled: false

web_dashboard:
  enabled: false

metrics:
  prometheus:
    enabled: false
```

### Notes

- `hibernate` currently works as **conservative distant-chunk tracking**. It does **not** remove and respawn entities.
- `item_removal` only affects dropped ground items and warns players before removal.
- AI throttling, predictive loading, and automatic chunk unloading are **off by default** because they can affect mob behavior or remote contraptions if used aggressively.
- The redstone/hopper module is monitoring-first and does not throttle farms or sorters by default.
- Auto clear only targets excess arrows when enabled; it does not purge general entities.
- If you enable the web dashboard publicly, set an auth token first.
- If you already run other cleanup or optimization plugins, test the combination on a staging server before production.

## Summary

XreatOptimizer is best suited for server owners who want:
- safer defaults
- useful monitoring
- moderate automatic optimization
- optional advanced features they can enable deliberately

It is not designed as a “delete everything and hope TPS improves” plugin.
