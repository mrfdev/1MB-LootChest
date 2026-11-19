# Local Paper test instances

`servers/Paper-26.2/` is the stopped rollback instance. Keep it intact. The
2026-09-25 upgrade cloned it to `servers/Paper-26.3/`, including its local world
and LootChest data. All runtime files remain Git-ignored.

`scripts/smoke-paper-26.2.sh` and historical release records retain their old
Java settings as rollback evidence. They are outside the maintained build/test
workflow; use the 26.3 runner below. The 26.2 instance is not started or changed
by the Oracle 25/27 setup.

The 26.3 launcher, `servers/Paper-26.3/1MB-minecraft.sh`, uses the shared installed
JDK resolver in `scripts/oracle_jdks.py` and requires Oracle Java 27. On macOS,
the resolver runs `/usr/libexec/java_home -F -v 27`, or validates the explicit
installed home in `ONE_MB_TEST_JAVA_HOME`. It checks the actual major version
and Oracle vendor and never downloads a JDK or falls back to another vendor.
New Oracle 27 patch installations require no launcher edits.
Its active server JAR is `Paper-26.3-241.jar`, channel `STABLE`, SHA-256
`ea1081663127a4256ca8ce500899a44b896c3a834a8bfac65d1e58ee1f1bee13`.
The root POM pins the same build/API and checksum. Maven and compilation of
plugin and test sources use Oracle JDK 25, retaining Java 25 bytecode. Unit and
integration tests run in Oracle JDK 27 forks. See [the build guide](build.md).
Paper's minimum runtime is Java 25; Java 27 is this project's selected test
runtime, not a Paper minimum. Stable build 241 was selected on 2026-11-04.
Its API dependency is `26.3.build.241-stable`.

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
into this local fixture. On 2026-10-20, CMI and CMILib were updated from the
user's Downloads; the other listed plugins remain at their previous tested
versions. The shared server is not used:

| Plugin | Previous local version | New local version |
| --- | --- | --- |
| CMI | 9.8.10.1 | 9.8.11.0 |
| CMILib | 1.6.0.0 | 1.6.1.0 |
| CoreProtect | 24.0-dev1 | 25.0 |
| Jobs | 5.2.6.5 | 5.2.6.6 |
| LuckPerms | 5.5.81 | 5.5.85 |
| PyroLib | 1.4.8 | 1.6.0 |
| Bolt | absent | 1.2.20 |
| 1MB-Lib-Core | absent | 1.0.4-834 |
| 1MB-Lib-AutoSell | absent | 1.0.4-834 |

The Core and AutoSell JARs were copied from the local 1MB-Library candidate
artifacts on 2026-11-04 for the AutoSell integration checks. Only this isolated
fixture was changed. The before-update server, world, plugin data, launcher
state, JAR and build reports are preserved in
`archive/autosell-20261104-before-changes/`.

Existing PlaceholderAPI 2.12.3, Vault 1.7.3-CMI, PyroWelcomesPro 0.5.1 and
mcMMO 2.2.049 remain. LootChest directly compiles only against the existing
public CMI API 9.8.6.4 and CMILib API 1.5.9.6; compatible runtime JARs are not
bundled. DiscordSRV remains inactive in `plugins-disabled/`.

Build the clean source, then run:

```bash
./mvnw clean verify
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./scripts/smoke-paper-26.3.py target/1MB-LootChest-v2.5.9.3-232-CMI-j25-26.3.jar
```

The smoke runner validates and selects Oracle JDK 27 independently of the
compiler environment and records its actual home, version and vendor. It
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
Integrated checks also compare CMI's actual hologram registry with the active
LootChest set, require transient (unsaved) holograms, verify text updates reach
the registered object, and require complete registry/timer cleanup on despawn.
A temporary timer on the disposable fixture must advance before its original
state is restored. These assertions run around reload, respawn and cold boots.
The disposable metadata fixture also temporarily exercises all eight copper
oxidation/wax states through effect reactivation and rejects a barrel. Its
original block, inventory, configured type and effects are restored afterward.
Each first boot verifies and recreates an existing probe fixture, so subsequent
runs exercise new chest creation as well as loading saved definitions.

With integrations present, the same test-only probe uses the actual Core/AutoSell
`1.0.4-834` JARs and synthetic Java/Bedrock player UUIDs. It covers AutoSell on/off
changes, pending off-setting saves, opening, inventory actions, breaking, marked
drop pickup, full inventories and direct grants. Refused actions must leave
rewards unchanged and a safe manual retry must succeed. It does not execute a
real sale or economy payout. See the [AutoSell verification record](releases/autosell-2026-11-04.md)
for the completed runs and remaining client checks.

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
  target/1MB-LootChest-v2.5.9.3-232-CMI-j25-26.3.jar
```

This profile temporarily moves only local optional plugin JARs into that run's
backup and restores them even after a failed check. Their configuration and
database directories remain in place. It requires `/lc info` to report the
optional hologram integration unavailable while the same loot, reload, metadata,
async guard, restart and persistence checks pass.

### CMI compatibility smoke

Use the CMI profile to check a specific Paper build and two dependency JARs in
a fresh loopback instance. Supply the Paper build, commit and SHA-256 from
[PaperMC Fill](https://docs.papermc.io/misc/downloads-service/) and use licensed
CMI and official CMILib JARs. The profile creates 26 active definitions and four
definitions in an unloaded synthetic world. It uses freshly generated worlds
and configuration under `servers/Paper-26.3/disposable-cmi/`.

```bash
./scripts/smoke-paper-26.3.py --cmi-holograms \
  --paper-jar /path/to/Paper-26.3-256.jar --paper-build 256 \
  --paper-commit 1eda666 --paper-sha256 c86baf83b1fb0eb0d572f39484c242210896c6adf9cc9342d5c16c4de70c9bdc \
  --cmi-jar /path/to/CMI-9.8.11.2.jar --cmi-version 9.8.11.2 \
  --cmilib-jar /path/to/CMILib1.6.1.3.jar --cmilib-version 1.6.1.3 \
  target/1MB-LootChest-v2.5.9.3-232-CMI-j25-26.3.jar
```

Run `./mvnw clean verify` first to compile the test probe. Use the artifact's
original filename and pass `--expect-untested-warning` when reproducing the
warning with an older artifact. The runner verifies the installed versions,
exact CMI registry membership, countdown updates, despawn, reload, respawn,
two cold boots, and removal before CMI disables. Results and both boot logs
are saved under `target/smoke-paper-26.3/`.

Add `--client-witness` for in-game checks. Join `127.0.0.1:32951` on each boot
as `LootChestViewer`. The runner moves the player to the representative chest
and writes each observation stage to `client-stage.txt` in the run's evidence
directory. After checking the stage in game, record the observation in
`<stage>.observed.txt` in the same directory to advance the run.

Before changing the local server pin, hold `servers/.lootchest-paper-26.3-smoke.lock`,
verify stopped state and ports, and archive the old server JAR, PaperScript state
and LootChest fixture. Resolve the exact download URL and SHA-256 from Fill's
26.3 build record; do not select another Minecraft version or guess coordinates.
Keep only the pinned `Paper-26.3-<build>.jar` active and align `current_build`,
`current_channel`, `current_jar`, `current_sha256`, `expected_sha256`, both channel
settings and the example configuration. The launcher records the actual selected
JAR after startup. Build-41 backups for this migration are under
`archive/paper-26.3-stable/before-server-update/`.
The pre-207 server, plugin JARs, configurations, local world and previous build
evidence are retained under `archive/compat-2026-10-20/before-update/`.

Retain safe Paper defaults for piston/headless-piston/permanent-block-break
exploits, unsafe end-portal teleportation and tripwire placement validation;
keep oversized-component sanitizer exclusions empty. Automated command smoke
checks do not replace player inventory, protection, particles and visual hologram
checks described in the release process.

The shared server at
`/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3/` remains stopped and untouched.
Deployment is separate from compatibility testing and requires a separate
request, verified artifact, deployment lock and recoverable backup.
