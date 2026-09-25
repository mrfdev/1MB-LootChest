# Installation and Updates

## Requirements

- Paper 26.3 build 41 from the `ALPHA` channel (explicit experimental target).
- Java 27 at runtime. Compile with JDK `25.0.4.1` and Java 25 bytecode.
- The compatibility candidate `1MB-LootChest-v2.5.9.3-229-CMI-j25-26.3.jar`.
- Optional CMI `9.8.10.1` and CMILib `1.6.0.0` when holograms are required.

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

The current live-approved source is preserved by the
`1mb-lootchest-v2.5.9.1-build225-approved` tag. Build 224 is retained as the
immediately previous rollback point.

Saved particle names are validated against the running Paper API. Removed or typed
particles use `Particles.fallback_particle` and produce a concise warning.

## Building from Source

Build candidates on a `codex/<feature>` branch from canonical `master`. The root `pom.xml` is the
release source of truth. Increment `buildNumber` exactly once for each shipped
jar, then perform the full rebuild with JDK 25.0.4.1 and Maven:

```bash
git switch master
git pull --ff-only origin master
git switch -c codex/<short-feature-name>
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.1.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
mvn clean verify
```

The active reactor compiles against Paper API `26.3.build.41-alpha`, emits Java 25
bytecode, and writes the named release jar to the root `target/` directory. It
does not compile or package version-specific Minecraft internals. Follow the
[release process](release-process.md) before promoting a candidate to `master`.
Run the same named jar through `scripts/smoke-paper-26.3.py`; it selects JDK 27
for the project-local instance. Keep compilation on JDK 25.0.4.1 with
`--release 25`. The 26.3 work continues the preserved 26.2 build-228 branch;
see [the upgrade record](releases/paper-26.3.md) and [local setup](test-server.md).
