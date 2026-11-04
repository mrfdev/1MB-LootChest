# Lootbox

Lootbox is the custom 1MoreBlock edition of LootChest. It creates repeatable,
staff-configured loot containers with randomized contents, respawn timers,
announcements, particles, and CMI holograms.

The maintained target is **Paper 26.3 build 241 (STABLE)** with **Java 25 bytecode**,
built using **Oracle JDK 25** and tested locally on **Oracle JDK 27**. Paper requires
Java 25 or newer. Stable 26.3 is the target for future development and tests;
manual gameplay approval is still required before promoting a release live.

Player documentation: [Lootbox on docs.1moreblock.com](https://docs.1moreblock.com/custom-server-plugins/lootbox/)

## Compatibility

| Component | Target |
| --- | --- |
| Server | Paper 26.3 build 241 (`STABLE`) |
| Paper API | `26.3.build.241-stable` |
| Java bytecode | Java 25 |
| Maven and compiler JDK | Installed Oracle JDK 25 |
| Unit, integration and Paper test JDK | Installed Oracle JDK 27 |
| Plugin version | `2.5.9.3` |
| Candidate build | `231` |
| Main command | `/lootchest`, alias `/lc` |
| Holograms | Optional: CMI `9.8.11.0` and CMILib `1.6.1.0` |

## Features

- Chest, trapped chest, barrel, shulker box, and copper chest containers.
- Per-item reward chances and a configurable maximum number of filled slots.
- Per-container respawn time, position, random spawn radius, announcements,
  hologram text, particle, and protection time.
- Runtime particle choices sourced directly from Paper. Only particles that can
  be spawned safely without an additional payload are shown in the editor.
- Automatic fallback when a saved particle is unavailable after an upgrade.
- Bolt-compatible protected-container handling through Paper event cancellation.
- AutoSell checks before opening or collecting loot, including items dropped from a Lootbox.
- MiniMessage formatting for locale, console, menu, notification, and hologram text.
- Persistent chest definitions in `plugins/LootChest/data.yml`.

## Documentation

- [Player guide](docs/player-guide.md)
- [Commands](docs/commands.md)
- [Permissions](docs/permissions.md)
- [Configuration](docs/configuration.md)
- [Installation and updates](docs/installation.md)
- [Release process](docs/release-process.md)
- [Local build and JDK setup](docs/build.md)
- [Integrations](docs/integrations.md)
- [Troubleshooting](docs/troubleshooting.md)
- [AutoSell protection and Paper 241 verification](docs/releases/autosell-2026-11-04.md)
- [October 2026 compatibility checks and in-game test steps](docs/releases/compatibility-2026-10-20.md)

Lootbox does not currently expose or require PlaceholderAPI placeholders.

## Canonical Development Branch

`master` is the canonical integration and release branch. Development happens on
short-lived `codex/<feature>` branches. The supported hologram backend is
CMI/CMILib; retired DecentHolograms work is kept only as an archive tag and must
not be merged into release builds.

The `origin` remote is the canonical 1MB repository
(`mrfdev/1MB-LootChest`). The original project is available as `upstream` for
careful comparison only; do not build releases from an upstream branch.

The latest gameplay-approved source is build 230 on Paper 26.3 build 207,
commit `3dc612feb28e`, preserved on `master` and by the
`1mb-lootchest-v2.5.9.3-build230-approved` tag. Its
[approval record](docs/releases/compatibility-2026-10-20.md#approval-record)
identifies the exact tested JAR. Approval and source promotion did not deploy it
to a live server. The previous approved Paper 26.2 build 225 remains a rollback
artifact; older snapshots and build 224 are historical releases.

Build 231 adds the Oracle 25/27 workflow, AutoSell protection and Paper 26.3
build 241 pins. Its automated checks passed; manual gameplay approval remains
pending. The earlier 26.2 snapshot is `v2.5.9.2-paper-26.2`; see the
[snapshot evidence](docs/releases/paper-26.2-snapshot.md). The stopped instance at
`servers/Paper-26.2/` remains intact beside `servers/Paper-26.3/`.

Until build 231 is gameplay-approved and promoted to `master`,
start future work from `codex/lootchest-autosell-guard` so development keeps the
Oracle toolchain, AutoSell protection and current stable pins:

```bash
git switch codex/lootchest-autosell-guard
git switch -c codex/<short-feature-name>
```

Build and test on the feature branch. Promote its clean source commit to `master`
only after the exact candidate jar passes the local Paper smoke test and manual
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

The root `pom.xml` is the release source of truth for semantic version, build
number, Paper target/API/build/channel, and Java target. Before shipping a new
build, increment `buildNumber` exactly once and update `revision` only when the
plugin version changes. Build from the repository root with the local launcher:

```bash
./mvnw -version
./mvnw clean verify
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

The launcher uses installed Maven and Python 3.9+. On macOS it resolves installed
Oracle JDKs with `/usr/libexec/java_home -F -v 25` and `-v 27`, then validates
their actual major versions and vendor. Maven and both source compilations run
on Oracle 25 with `--release 25`; unit and integration test forks run on Oracle
27. It downloads neither Maven nor a JDK. New Oracle patch installations require
no project edits. See [the build guide](docs/build.md) for explicit installed-home
overrides, configuration guards and verification evidence.

Release artifacts use this format:

```text
target/1MB-LootChest-v<version>-<build>-CMI-j25-26.3.jar
```

The current compatibility candidate is:

```text
target/1MB-LootChest-v2.5.9.3-231-CMI-j25-26.3.jar
```

The latest gameplay-approved release is preserved separately from the candidate:

```text
1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar
```

The final rollback build containing direct WorldGuard random-spawn filtering is:

```text
Artifact:      1MB-LootChest-v2.5.9.1-203-CMI-j25-26.2.jar
Local archive: servers/Paper-26.2/archive/1MB-LootChest-v2.5.9.1-203-CMI-j25-26.2-LAST-WORLDGUARD-SUPPORT.jar
Commit:        f863a03fa374
Tag:           1mb-lootchest-v2.5.9.1-build203-last-worldguard-support
SHA-256:       9027167883a01ad7f904234f32b3f77ffde9adb36bb904f84946462edaa8b0c1
```

Later builds intentionally contain no direct WorldGuard integration. WorldGuard
may remain installed for the server's regions; the 1MoreBlock deployment places
randomized Lootboxes only in staff-selected regions.

The canonical full rebuild is `./mvnw clean verify`; it runs all unit tests,
integration tests and release-jar checks. Preserve previous `target/` artifacts and test reports in
`archive/` before running `clean` when they are needed as historical records.
The compiler's `release`, `source`, and `target` remain 25. Paper 26.3 is tested
with Oracle Java 27; the bytecode target does not lower Paper's runtime requirements.
Reuse the reserved build number after a failed attempt; do not increment again.

The project emits Java 25 class files and uses only the Paper API for Minecraft
integration. The unused falling-package feature and its version-specific NMS
adapters were removed after build 197. Every artifact embeds its build number,
source commit, clean/dirty state, Paper target/build/channel/API, Java target,
and filename. These details are printed during startup and by `/lc info`.

Run the repeatable local smoke test against the exact clean candidate:

```bash
./scripts/smoke-paper-26.3.py \
  target/1MB-LootChest-v2.5.9.3-231-CMI-j25-26.3.jar
```

The runner uses the maintained `servers/Paper-26.3/1MB-minecraft.sh` launcher
with dynamically selected Oracle JDK 27, checks the pinned server checksum and release metadata, and requires
available local ports. It verifies plugin enable, info/help/list/reload/despawn/
respawn/audit, malformed reload recovery, custom-item metadata, a cold restart,
saved definitions, clean shutdown and port release. Logs remain under
`target/smoke-paper-26.3/`. The legacy 26.2 runner is retained for rollback checks.
See [the local test setup](docs/test-server.md) and
[the stable migration record](docs/releases/paper-26.3-stable.md) for results and
manual checks. The [2026-09-25 alpha upgrade](docs/releases/paper-26.3.md) remains
as historical evidence.

## Developer API

`fr.black_eyes.api.LootChestAPI` exposes lookup, creation, copy, removal, and save
operations. `fr.black_eyes.api.events.LootChestSpawnEvent` is fired after a
container is populated and activated. These APIs follow the plugin's runtime
types and are not promised as a stable cross-version binary API. World,
inventory, registration, removal, and save operations must run on Paper's main
server thread. `getAllLootChests()` returns a read-only snapshot; use the named
API methods for mutations so reserved and reload-in-progress definitions remain
protected.

## Privacy

The 1MoreBlock build contains neither the upstream update checker nor bStats
metrics classes. Lootbox performs no update or telemetry requests.

## License

This project is distributed under the GNU General Public License v3.0. See
[LICENSE](LICENSE).
