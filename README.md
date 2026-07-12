# Lootbox

Lootbox is the custom 1MoreBlock edition of LootChest. It creates repeatable,
staff-configured loot containers with randomized contents, respawn timers,
announcements, particles, optional falling-package effects, and CMI holograms.

This fork is intentionally focused on **Paper 26.2** and **Java 25**. It is not a
general-purpose legacy Bukkit or Spigot build.

Player documentation: [Lootbox on docs.1moreblock.com](https://docs.1moreblock.com/custom-server-plugins/lootbox/)

## Compatibility

| Component | Target |
| --- | --- |
| Server | Paper 26.2 |
| Paper API | `26.2.build.29-alpha` |
| Java runtime and bytecode | Java 25 |
| Plugin version | `2.5.9.1` |
| Main command | `/lootchest`, alias `/lc` |
| Holograms | CMI and CMILib when installed |

## Features

- Chest, trapped chest, barrel, shulker box, and copper chest containers.
- Per-item reward chances and a configurable maximum number of filled slots.
- Per-container respawn time, position, random spawn radius, announcements,
  hologram text, particle, protection time, and falling-package toggle.
- Runtime particle choices sourced directly from Paper. Only particles that can
  be spawned safely without an additional payload are shown in the editor.
- Automatic fallback when a saved particle is unavailable after an upgrade.
- Optional region-aware spawn checks, proxy-wide announcements, and Lootin support.
- MiniMessage formatting for locale, console, menu, notification, and hologram text.
- Persistent chest definitions in `plugins/LootChest/data.yml`.

## Documentation

- [Player guide](docs/player-guide.md)
- [Commands](docs/commands.md)
- [Permissions](docs/permissions.md)
- [Configuration](docs/configuration.md)
- [Installation and updates](docs/installation.md)
- [Integrations](docs/integrations.md)
- [Troubleshooting](docs/troubleshooting.md)

Lootbox does not currently expose PlaceholderAPI placeholders. The PlaceholderAPI
compile dependency does not itself create any placeholders.

## Administrative Quick Start

1. Place and fill one supported container.
2. Look directly at it and run `/lc create <name>`.
3. Configure contents, chances, timer, particle, messages, and effects in the editor.
4. Run `/lc respawn <name>` and test opening, emptying, and breaking it.
5. Run `/lc reload` after editing configuration, locale, or chest data.

Back up the complete `plugins/LootChest/` directory before an update. Do not keep
multiple LootChest or Lootbox jars in the server's top-level `plugins/` directory.

## Build

```bash
mvn package
```

The release artifact is written to:

```text
target/LootChest-2.5.9.1-paper-26.2-j25-CMI.jar
```

The project contains a native `v_26_2` falling-package adapter and emits Java 25
class files. The active Maven reactor packages only that adapter.

## Developer API

`fr.black_eyes.api.LootChestAPI` exposes lookup, creation, copy, removal, and save
operations. `fr.black_eyes.api.events.LootChestSpawnEvent` is fired after a
container is populated and activated. These APIs follow the plugin's runtime
types and are not promised as a stable cross-version binary API.

## Privacy and Updates

The 1MoreBlock build defaults `CheckForUpdates` to `false` and does not instantiate
the bundled bStats metrics class. It therefore does not start LootChest telemetry.

## License

This project is distributed under the GNU General Public License v3.0. See
[LICENSE](LICENSE).
