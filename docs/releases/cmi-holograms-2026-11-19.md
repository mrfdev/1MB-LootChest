# CMI hologram compatibility on Paper 26.3 build 256

The 2026-11-19 check used the production LootChest build `2.5.9.3-231`, clean
source `fe314dd3b3ea`, on a fresh, loopback-only Paper server. Read-only SHA-256
checks matched all four local inputs to the installed production JARs. The
fixture used generated worlds, configuration and chest data.

| Runtime input | Version | SHA-256 |
| --- | --- | --- |
| Paper | 26.3 build 256 `STABLE`, commit `1eda666` | `c86baf83b1fb0eb0d572f39484c242210896c6adf9cc9342d5c16c4de70c9bdc` |
| LootChest | 2.5.9.3-231, source `fe314dd3b3ea` | `c82567907e13424d67cc7719198ee6eb27e42660d9912f2770a9732df358d8b8` |
| CMI | 9.8.11.2 | `1dd5a0d621f33ebd7c1968e7b47b4b5d665fdd13e2b449f4addb92fbe39dc80e` |
| CMILib | 1.6.1.3 | `856962ace9555b3425d91070cfc41dd1b64c416b21687466c24ecbef85a1d7ed` |

Production and the disposable server both reported Oracle HotSpot
`27.0.1.1+4-22`. Paper's checksum also matched its
[build 256 download record](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/256).

## Version warning and deferred chests

Maven embeds the root POM's tested versions in `lootchest-build.properties`,
which `BuildInfo.load()` reads. `Main.startCmiHolograms()` requires both
dependencies enabled and a CMI hologram manager, then compares their exact
`PluginMeta` versions with the tested pair. Build 231 warns once per startup
when the pair differs from `9.8.11.0` with `1.6.1.0` and continues using the available
manager. The separate saved-chest startup path defers definitions whose world
is unavailable before they can create blocks or holograms.

## Verification

The smoke profile is documented in [local test setup](../test-server.md#cmi-compatibility-smoke).
The control used CMI `9.8.11.0` and CMILib `1.6.1.0` on the same Paper 256
and LootChest 231 binaries and passed the two-boot server-side checks. It
created 26 registry entries, advanced the countdown in place, returned to 26
entries after reload and respawn, and removed them on despawn and shutdown.

For the production pair, the fixture used 30 synthetic chest definitions, with
four assigned to an unloaded world. The vanilla Java 26.3 client observed one
representative chest while the probe checked the complete CMI registry. The
first visual pass exposed a missing height setting in the fixture. The
disposable reload used the production value of `1.2`, which put the countdown
above the chest. The runner now seeds that setting when creating a fixture.

| Check | Result with CMI 9.8.11.2 and CMILib 1.6.1.3 |
| --- | --- |
| Startup | 26 of 30 definitions activated, four deferred, zero rejected or failed. Exactly 26 transient CMI registry entries. |
| Countdown | `Smoke Chest Alpha` changed from `59m 57s` to `59m 55s` on the same registered object. Consecutive client packets also carried changing countdown text. |
| `/lc reload` | One registry entry per active chest. Vanilla rendered one unobstructed countdown over the representative chest. |
| `/lc despawnall` | Zero LootChest holograms in the registry and no countdown tasks. Vanilla removed the representative display. |
| `/lc respawnall` | One registry entry per active chest again. Vanilla rendered one countdown over the representative chest. |
| Shutdown | Zero LootChest holograms and countdown tasks after LootChest disabled while CMI was still enabled. The client removed its display before disconnect. |
| Cold restart | 26 registry entries again, with the same four definitions deferred. Vanilla rendered exactly one representative countdown with advancing text. |

The client packet trace received all 26 fixture labels as the viewer moved
into tracking range on boot one. It saw at most one text-display entity at a
time per position and received a removal for each added display. On both
stops, the client received the final removal before disconnect. Build 231
logged one warning per boot across both startup and reload cycles.

## Candidate metadata

Build `2.5.9.3-232` adds `9.8.11.2` with `1.6.1.3` to the exact tested pairs
and retains the original `9.8.11.0` with `1.6.1.0` pair. Mixing the versions
across the two pairs still triggers the warning. The maintained compile target
is Paper API `26.3.build.241-stable` and Java 25 bytecode. Full gameplay
approval is separate from this hologram validation.

| Candidate build evidence | Value |
| --- | --- |
| Source | `c3cd1da41071`, clean |
| Artifact | `1MB-LootChest-v2.5.9.3-232-CMI-j25-26.3.jar` |
| SHA-256 | `fd2e004993a326572b41994f7e74f7b659743033ddbea3acf7f5f8215ff53bb0` |
| Maven and compiler | Oracle JDK `25.0.5.1`, `/Library/Java/JavaVirtualMachines/jdk-25.0.5.1.jdk/Contents/Home` |
| Test forks and Paper | Oracle JDK `27.0.1.1`, `/Library/Java/JavaVirtualMachines/jdk-27.0.1.1.jdk/Contents/Home` |
| Bytecode | Java 25, class major 69 for all 84 plugin and 29 test classes |
| Automated suites | 120 unit tests, 16 integration tests and 27 launcher and smoke-runner tests passed |
| Paper 256 compatibility smoke | Both cold boots passed with the production CMI pair. The probe matched each of the 26 hologram positions to its chest, and LootChest emitted zero untested-pair warnings. |
| Paper 241 integrated smoke | Both cold boots passed with CMI `9.8.11.0`, CMILib `1.6.1.0` and AutoSell `1.0.4-834`. |
| Paper 241 standalone smoke | Both cold boots passed with optional plugins absent. The runner restored the local optional JARs afterward. |

The local evidence is under `.scratch/cmi-production-pair/evidence/`, including
both deployed-artifact boot logs, the client screenshots and packet trace,
the older-pair control, and the candidate build and smoke logs.
