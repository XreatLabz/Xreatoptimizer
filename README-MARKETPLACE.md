# XreatOptimizer

XreatOptimizer is a performance helper plugin for Bukkit-based Minecraft servers. It aims to improve day-to-day server stability with safer defaults, lightweight monitoring, and optional optimization features that can be enabled when needed.

## Best fit

XreatOptimizer is a good fit if you want:
- live TPS, memory, entity, and chunk visibility
- automatic profile-based optimization
- lower resource usage when the server is empty
- dynamic view distance under load
- dropped item and XP merging
- optional item cleanup, dashboard, predictive loading, and advanced safe-mode tuning

## Default behavior

The plugin is conservative by default.

**Enabled by default**
- monitoring
- automatic optimization profile selection
- memory saver
- dynamic view distance
- empty-server low-power mode
- dropped item / XP stack fusion

**Disabled by default**
- item removal
- auto clear
- hibernation
- predictive loading
- redstone/hopper optimization
- web dashboard
- Prometheus metrics
- AI throttling
- automatic chunk unloading

## Important notes

- `item_removal` is **off by default**. If enabled, it only removes dropped ground items after a warning period.
- `hibernate` is **off by default** and currently works as safe distant-chunk tracking rather than removing and respawning entities.
- AI throttling, predictive loading, and automatic chunk unloading are also off by default to avoid affecting mob behavior or remote contraptions.
- The redstone/hopper system is monitoring-first and avoids changing sorter or farm behavior by default.
- Auto clear only targets excess arrows when explicitly enabled.
- The plugin is designed to help with stability and cleanup, not to replace every other optimization plugin on the server.

## Commands

- `/xreatopt stats`
- `/xreatopt report`
- `/xreatopt boost`
- `/xreatopt pregen <world> <radius> <speed>`
- `/xreatopt purge`
- `/xreatopt reload`
- `/xreatopt clearcache`
- `/xreatopt dashboard`
- `/xreatgui`

## Permissions

- `xreatopt.view` — default: OP
- `xreatopt.admin` — default: OP

## Compatibility

- Minecraft: 1.8-1.21.10
- Java: 11+
- Server software: Spigot, Paper, Purpur, and similar forks

## Short summary

XreatOptimizer is a practical optimization plugin with a safer approach:
- reduce overhead when possible
- expose useful runtime information
- keep risky features opt-in
- avoid aggressive cleanup by default
