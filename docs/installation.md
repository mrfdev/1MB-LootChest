# Installation and Updates

## Requirements

- Paper 26.3 build 241 from the `STABLE` channel.
- Java 25 or newer; the local test runtime remains Java 27. Compile with
  the dynamically discovered installed Oracle JDK 25 and Java 25 bytecode.
- The compatibility candidate `1MB-LootChest-v2.5.9.3-232-CMI-j25-26.3.jar`.
- Optional CMI and CMILib when holograms are required. See the
  [tested version pairs](integrations.md#cmi-and-cmilib).
- Optional `1MB-CMIAPI-AutoSell`; collection protection was verified with
  `1MB-Lib-Core` and `1MB-Lib-AutoSell` `1.0.4-834`.

DecentHolograms is not a supported backend for this build.
Lootbox continues without holograms when CMI or CMILib is absent.

## Fresh Installation

1. Stop Paper cleanly.
2. Place the Lootbox jar in the top-level `plugins/` directory.
3. Confirm there is only one LootChest/Lootbox jar there.
4. Install and start compatible CMI and CMILib builds when holograms are required.
5. Start Paper and wait for `LootChest ... Plugin loaded`.
6. Run `/lc info`, `/lc help`, and `/lc list`.
7. Create a disposable test Lootbox and verify open, empty, break, respawn,
   particle, and hologram behavior before using production data.

## Updating

1. Stop Paper cleanly.
2. Back up `plugins/LootChest/`, especially `data.yml`, `config.yml`, and `lang.yml`.
3. Replace the old jar; do not leave duplicate versions.
4. Preserve the data folder. New defaults are added without requiring it to be deleted.
5. Start Paper and inspect `logs/latest.log` for LootChest errors.
6. Run `/lc reload` and `/lc respawnall`, then verify representative container types.

The latest gameplay-approved source is build 230, commit `3dc612feb28e`, preserved
on `master` and by `1mb-lootchest-v2.5.9.3-build230-approved`. Its exact JAR and
checksum are in the [approval record](releases/compatibility-2026-10-20.md#approval-record).
Build 232 remains a candidate awaiting gameplay approval. Keep the approved
build-230 JAR separate from the later toolchain verification JAR that reused
build 230; their source commits and checksums differ. The prior approved
Paper 26.2 build 225 is also retained for rollback with its matching server/data
backup.

When AutoSell is installed, players must turn it off before opening, breaking,
receiving or picking up Lootbox rewards. A pending transaction or off-setting
save also delays collection. New messages are added to `lang.yml` while existing
translations are preserved. See [AutoSell troubleshooting](troubleshooting.md#autosell-blocks-loot-collection).

Saved particle names are validated against the running Paper API. Removed or typed
particles use `Particles.fallback_particle` and produce a concise warning.

## Building from Source

Build candidates on a `codex/<feature>` branch. Until build 231 is
approved into `master`, use `codex/lootchest-autosell-guard` as the development base. The root
`pom.xml` is the release source of truth. Increment `buildNumber` exactly once for each shipped
jar, then use the validated Oracle 25 compiler and Oracle 27 test workflow:

```bash
git switch codex/lootchest-autosell-guard
git switch -c codex/<short-feature-name>
./mvnw -version
./mvnw clean verify
```

The active reactor compiles against Paper API `26.3.build.241-stable`, emits Java 25
bytecode, and writes the named release jar to the root `target/` directory. It
does not compile or package version-specific Minecraft internals. Follow the
[release process](release-process.md) before promoting a candidate to `master`.
Run the same named jar through `scripts/smoke-paper-26.3.py`; it selects JDK 27
for the project-local instance. Keep compilation on installed Oracle JDK 25 with
`--release 25`. See [local setup](test-server.md) for the standard and CMI
compatibility smoke profiles.
