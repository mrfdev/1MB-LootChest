# Local Paper test instances

`servers/Paper-26.2/` is the stopped rollback instance. Keep it intact. The
2026-09-25 upgrade cloned it to `servers/Paper-26.3/`, including its local world
and LootChest data. All runtime files remain Git-ignored.

The 26.3 launcher, `servers/Paper-26.3/1MB-minecraft.sh`, uses
`/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home` and requires Java 27.
Its active server JAR is `Paper-26.3-41.jar`, channel `ALPHA`, SHA-256
`2b77166ee61886a9bc9ab33dc9e4847fa3538b36d9ba6e5f2fa7ed90973aa748`.
The root POM pins the same build/API and checksum. Compile and unit-test with
JDK 25.0.4.1, retaining Java 25 bytecode.

The clone binds `127.0.0.1:32931` for Minecraft TCP; query UDP `32932` and
RCON TCP `32933` are separately assigned but disabled. PaperScript uses session
`lootchest-paper-26.3-32931`, server name `LootChest-Paper-26.3`, and explicit
`ALPHA` defaults for both channel fields. Automatic same-version build upgrades
are disabled. Before launching, check every assigned TCP/UDP port again.

Copied PaperScript state/cache/download/lock files, the `.paper` launcher state,
and old server JARs were moved inside the clone's
`archive/copied-26.2-launcher-state/`. The new state records the verified 26.3
JAR, checksum and absolute directory. Never restore copied 26.2 launcher state
into the active 26.3 instance.

Updated dependency JARs were copied from the shared server's plugins directory,
without importing shared configuration or player data:

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
./scripts/smoke-paper-26.3.py target/1MB-LootChest-v2.5.9.3-229-CMI-j25-26.3.jar
```

The smoke runner selects JDK 27 independently of the compiler environment. It
locks the local instance, verifies that it is stopped, backs up only its old
LootChest JAR, installs the exact candidate, and invokes the maintained launcher.
It requires clean source metadata, the exact Paper target and checksum, all saved
chests activated, active CMI holograms, command and lifecycle checks, a successful
shutdown, and released ports. Development checks may explicitly use
`LOOTCHEST_ALLOW_DIRTY=1`; such results are never release approval.
Logs and machine-readable results remain in `target/smoke-paper-26.3/`.
The copied fixture area in `minecraft:overworld`, from block `-64,-64` to
`15,-33`, is kept force-loaded (ten chunks) so the no-player smoke run can
inspect all 35 existing containers. The audit must report zero unavailable
locations as well as zero issues. This only affects the disposable 26.3 clone.

Retain safe Paper defaults for piston/headless-piston/permanent-block-break
exploits, unsafe end-portal teleportation and tripwire placement validation;
keep oversized-component sanitizer exclusions empty. Automated command smoke
checks do not replace player inventory, protection, particles and visual hologram
checks described in the release process.

The shared server at
`/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3/` is a deployment destination
only. Never start, restart or launch it as part of this upgrade. Before replacing
its LootChest JAR, acquire the shared deployment lock, verify stopped state,
inspect plugin identities, archive exactly the old LootChest artifact, install
and checksum the verified release, and confirm it remains stopped. Preserve
all other plugins, configuration, player data and pending unrelated deployments.
