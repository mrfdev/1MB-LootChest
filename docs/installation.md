# Installation and Updates

## Requirements

- Paper 26.2.
- Java 25.
- The release jar `1MB-LootChest-v2.5.9.1-195-CMI-j25-26.2.jar`.
- CMI and CMILib for holograms. Lootboxes continue without holograms when CMI is absent.

Do not install this build on Spigot, legacy Minecraft versions, Paper 26.1, or an
older Java runtime.

## Fresh Installation

1. Stop Paper cleanly.
2. Place the Lootbox jar in the top-level `plugins/` directory.
3. Confirm there is only one LootChest/Lootbox jar there.
4. Install and start compatible CMI and CMILib builds when holograms are required.
5. Start Paper and wait for `LootChest ... Plugin loaded`.
6. Run `/lc info`, `/lc help`, and `/lc list`.
7. Create a disposable test Lootbox and verify open, empty, break, respawn,
   particle, falling-package, and hologram behavior before using production data.

## Updating

1. Stop Paper cleanly.
2. Back up `plugins/LootChest/`, especially `data.yml`, `config.yml`, and `lang.yml`.
3. Replace the old jar; do not leave duplicate versions.
4. Preserve the data folder. New defaults are added without requiring it to be deleted.
5. Start Paper and inspect `logs/latest.log` for LootChest errors.
6. Run `/lc reload` and `/lc respawnall`, then verify representative container types.

Saved particle names are validated against the running Paper API. Removed or typed
particles use `Particles.fallback_particle` and produce a concise warning.

## Building from Source

From the repository root with JDK 25 and Maven available:

```bash
mvn package
```

The active reactor compiles against Paper API `26.2.build.29-alpha`, packages the
native `v_26_2` adapter, emits Java 25 bytecode, and writes the named release jar
to the root `target/` directory.
