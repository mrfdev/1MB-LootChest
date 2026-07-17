# Lootbox

Lootbox is the custom 1MoreBlock edition of LootChest. It creates repeatable,
staff-configured loot containers with randomized contents, respawn timers,
announcements, particles, and CMI holograms.

The maintained runtime, build, documentation, and test target is **Paper 26.2**
with **Java 25**.

Player documentation: [Lootbox on docs.1moreblock.com](https://docs.1moreblock.com/custom-server-plugins/lootbox/)

## Compatibility

| Component | Target |
| --- | --- |
| Server | Paper 26.2 |
| Paper API | `26.2.build.60-beta` |
| Java runtime and bytecode | Java 25 |
| Plugin version | `2.5.9.1` |
| Main command | `/lootchest`, alias `/lc` |
| Holograms | Optional: CMI `9.8.8.5` and CMILib `1.5.9.9` |

## Features

- Chest, trapped chest, barrel, shulker box, and copper chest containers.
- Per-item reward chances and a configurable maximum number of filled slots.
- Per-container respawn time, position, random spawn radius, announcements,
  hologram text, particle, and protection time.
- Runtime particle choices sourced directly from Paper. Only particles that can
  be spawned safely without an additional payload are shown in the editor.
- Automatic fallback when a saved particle is unavailable after an upgrade.
- Optional region-aware spawn checks and Lootin support.
- MiniMessage formatting for locale, console, menu, notification, and hologram text.
- Persistent chest definitions in `plugins/LootChest/data.yml`.

## Documentation

- [Player guide](docs/player-guide.md)
- [Commands](docs/commands.md)
- [Permissions](docs/permissions.md)
- [Configuration](docs/configuration.md)
- [Installation and updates](docs/installation.md)
- [Release process](docs/release-process.md)
- [Integrations](docs/integrations.md)
- [Troubleshooting](docs/troubleshooting.md)

Lootbox does not currently expose or require PlaceholderAPI placeholders.

## Canonical Development Branch

`master` is the canonical integration and release branch. Development happens on
short-lived `codex/<feature>` branches. The supported hologram backend is
CMI/CMILib; retired DecentHolograms work is kept only as an archive tag and must
not be merged into release builds.

The `origin` remote is the canonical 1MB repository
(`mrfdev/1MB-LootChest`). The original project is available as `upstream` for
careful comparison only; do not build releases from an upstream branch.

The current live-approved Paper 26.2 baseline is build 200, preserved by the
`1mb-lootchest-v2.5.9.1-build200-approved` tag. Build 199 is the immediately
previous rollback release. Older snapshots remain tagged for history but are not
development baselines.

Start future work from an up-to-date `master`:

```bash
git switch master
git pull --ff-only origin master
git switch -c codex/<short-feature-name>
```

Build and test on the feature branch. Promote its clean source commit to `master`
only after the exact candidate jar passes the central Paper smoke test and manual
gameplay approval. Tag that approved commit and retain the previous approved jar.
The complete checklist is in [Release process](docs/release-process.md).

## Administrative Quick Start

1. Place and fill one supported container.
2. Look directly at it and run `/lc create <name>`.
3. Configure contents, chances, timer, particle, messages, and effects in the editor.
4. Run `/lc respawn <name>` and test opening, emptying, and breaking it.
5. Run `/lc reload` after editing configuration, locale, or chest data.

Back up the complete `plugins/LootChest/` directory before an update. Do not keep
multiple LootChest or Lootbox jars in the server's top-level `plugins/` directory.

## Installation and Updates

1. Stop Paper cleanly.
2. Back up the complete `plugins/LootChest/` data directory and the current working jar.
3. Remove the previous LootChest/Lootbox jar from the top-level `plugins/` directory.
4. Install the new `1MB-LootChest` jar, leaving exactly one LootChest/Lootbox jar there.
5. Keep current CMI and CMILib jars installed when holograms are required.
6. Start Paper and confirm `LootChest ... Plugin loaded` without an exception.
7. Run `/lc info`, `/lc help`, `/lc list`, `/lc reload`, and `/lc respawnall`.
8. Verify a chest, barrel, shulker box, and copper chest before treating the update as live.

To roll back, stop Paper, restore the previous jar and backed-up `plugins/LootChest/`
directory together, then start and verify the server again.

## Build

Before shipping a new build, increment `buildNumber` in the root `pom.xml`. Change
`revision` only when the plugin version itself changes. Then build from the repository
root with JDK 25:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
mvn -DskipTests clean package
```

Release artifacts use this format:

```text
target/1MB-LootChest-v<version>-<build>-CMI-j25-26.2.jar
```

The current live-approved release is:

```text
target/1MB-LootChest-v2.5.9.1-200-CMI-j25-26.2.jar
```

The project emits Java 25 class files and uses only the Paper API for Minecraft
integration. The unused falling-package feature and its version-specific NMS
adapters were removed after build 197. Every artifact embeds its build number,
source commit, clean/dirty state, Paper target/API, Java target, and filename.
These details are printed during startup and by `/lc info`.

Run the central smoke test before merging or publishing:

```bash
/Users/floris/Projects/Codex/servers/run-test-server \
  --paper 26.2 \
  --plugin target/1MB-LootChest-v2.5.9.1-200-CMI-j25-26.2.jar \
  --foreground
```

## Developer API

`fr.black_eyes.api.LootChestAPI` exposes lookup, creation, copy, removal, and save
operations. `fr.black_eyes.api.events.LootChestSpawnEvent` is fired after a
container is populated and activated. These APIs follow the plugin's runtime
types and are not promised as a stable cross-version binary API.

## Privacy

The 1MoreBlock build contains neither the upstream update checker nor bStats
metrics classes. Lootbox performs no update or telemetry requests.

## License

This project is distributed under the GNU General Public License v3.0. See
[LICENSE](LICENSE).
