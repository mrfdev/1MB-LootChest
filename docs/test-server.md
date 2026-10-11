# Local Paper test instances

`servers/Paper-26.2/` is the stopped rollback instance. Keep it intact. The
2026-09-25 upgrade cloned it to `servers/Paper-26.3/`, including its local world
and LootChest data. All runtime files remain Git-ignored.

The 26.3 launcher, `servers/Paper-26.3/1MB-minecraft.sh`, uses
`/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home` and requires Java 27.
Its active server JAR is `Paper-26.3-175.jar`, channel `STABLE`, SHA-256
`ece5afb6d0cdf0c930489618bf92341e33d2ebcd4f21e6d365daff6ed26dafb0`.
The root POM pins the same build/API and checksum. Compile and unit-test with
JDK 25.0.4.1, retaining Java 25 bytecode.
Paper's minimum runtime is Java 25; Java 27 is this project's selected test
runtime, not a Paper minimum. Stable build 175 was verified on 2026-10-11.
Its API dependency is `26.3.build.175-stable`.

The clone binds `127.0.0.1:32931` for Minecraft TCP; query UDP `32932` and
RCON TCP `32933` are separately assigned but disabled. PaperScript uses session
`lootchest-paper-26.3-32931`, server name `LootChest-Paper-26.3`, and explicit
`STABLE` defaults for both channel fields. Automatic same-version build upgrades
are disabled. Before launching, check every assigned TCP/UDP port again.

Copied PaperScript state/cache/download/lock files, the `.paper` launcher state,
and old server JARs were moved inside the clone's
`archive/copied-26.2-launcher-state/`. The new state records the verified 26.3
JAR, checksum and absolute directory. Never restore copied 26.2 launcher state
into the active 26.3 instance.

During the historical 2026-09-25 alpha upgrade, dependency JARs were copied
into this local fixture. The stable migration reuses those local copies and
does not read or modify the shared server:

| Plugin | Previous local version | New local version |
| --- | --- | --- |
| CMI | 9.8.9.9 | 9.8.10.1 |
| CMILib | 1.5.9.9 | 1.6.0.0 |
| CoreProtect | 24.0-dev1 | 25.0 |
| Jobs | 5.2.6.5 | 5.2.6.6 |
| LuckPerms | 5.5.81 | 5.5.85 |
| PyroLib | 1.4.8 | 1.6.0 |
| Bolt | absent | 1.2.20 |

Existing PlaceholderAPI 2.12.3, Vault 1.7.3-CMI, PyroWelcomesPro 0.5.1 and
mcMMO 2.2.049 remain. LootChest directly compiles only against the existing
public CMI API 9.8.6.4 and CMILib API 1.5.9.6; compatible runtime JARs are not
bundled. DiscordSRV remains inactive in `plugins-disabled/`.

Build the clean source, then run:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.0.4.1.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean verify
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./scripts/smoke-paper-26.3.py target/1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar
```

The smoke runner selects JDK 27 independently of the compiler environment. It
locks the local instance, verifies that it is stopped, backs up only its old
LootChest JAR, installs the exact candidate, and invokes the maintained launcher.
It requires clean source metadata, the exact Paper target and checksum, all saved
chests activated, active CMI holograms, command and lifecycle checks, two cold
boots, clean shutdowns, and released ports. It rejects alpha/beta coordinates,
mismatched API pins, and enabled automatic build upgrades. Python 3.9 or newer
is supported. Development checks may explicitly use
`LOOTCHEST_ALLOW_DIRTY=1`; such results are never release approval.
Logs and machine-readable results remain in `target/smoke-paper-26.3/`.
The copied fixture area in `minecraft:overworld`, from block `-64,-64` to
`15,-33`, is kept force-loaded (ten chunks) so the no-player smoke run can
inspect all 35 original containers. The audit must report zero unavailable
locations as well as zero issues. This only affects the disposable 26.3 clone.

The runner packages the test-only `PaperRuntimeProbe` from Maven's test classes
into a temporary, console-only plugin that requires an explicit JVM property
and loopback binding. It creates `stable_smoke_metadata` at `spawn:8,200,-40`
only when that block is air, bringing the fixture to 36 containers. The fixture
contains third-party PDC, custom model data, enchantment/glint/damage components,
and a shulker item with nested contents. Spawn, delivery, reload and cold restart
must preserve those items; modifying a delivered item must leave its template
unchanged. Off-thread API mutation must be refused before world access. The
probe JAR is removed after the test and release packaging tests forbid it in
the distributable.
Each first boot verifies and recreates an existing probe fixture, so subsequent
runs exercise new chest creation as well as loading saved definitions.

If an interrupted probe left an unregistered chest at that exact location, it
can recover only a chest containing its two exact test items and no other loot.
Any other occupied location or inactive saved definition fails closed.

The first boot temporarily writes malformed YAML to the disposable `lang.yml`,
requires a rejected reload and a clean runtime audit, restores the exact original
language bytes, then checks a successful retry. Expected decoding errors remain
in the log. After both clean stops, saved definitions are compared byte for byte
with only `lastreset` timestamps and serialized component-key ordering excluded.
Every component value and folded continuation is retained in that comparison,
and the live probe additionally asserts complete `ItemStack` equality.
Evidence includes both console logs,
both persisted snapshots and `result.json`.

Repeat the same checks without any optional plugins to verify graceful operation
when CMI/CMILib and the rest of the integration stack are absent:

```bash
./scripts/smoke-paper-26.3.py --without-integrations \
  target/1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar
```

This profile temporarily moves only local optional plugin JARs into that run's
backup and restores them even after a failed check. Their configuration and
database directories remain in place. It requires `/lc info` to report the
optional hologram integration unavailable while the same loot, reload, metadata,
async guard, restart and persistence checks pass.

Before changing the local server pin, hold `servers/.lootchest-paper-26.3-smoke.lock`,
verify stopped state and ports, and archive the old server JAR, PaperScript state
and LootChest fixture. Resolve the exact download URL and SHA-256 from Fill's
26.3 build record; do not select another Minecraft version or guess coordinates.
Keep only the pinned `Paper-26.3-<build>.jar` active and align `current_build`,
`current_channel`, `current_jar`, `current_sha256`, `expected_sha256`, both channel
settings and the example configuration. The launcher records the actual selected
JAR after startup. Build-41 backups for this migration are under
`archive/paper-26.3-stable/before-server-update/`.

Retain safe Paper defaults for piston/headless-piston/permanent-block-break
exploits, unsafe end-portal teleportation and tripwire placement validation;
keep oversized-component sanitizer exclusions empty. Automated command smoke
checks do not replace player inventory, protection, particles and visual hologram
checks described in the release process.

The shared server at
`/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3/` remains stopped and untouched.
Deployment is separate from compatibility testing and requires a separate
request, verified artifact, deployment lock and recoverable backup.
