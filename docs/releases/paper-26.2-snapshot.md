# Paper 26.2 snapshot, 2026-09-25

The annotated `v2.5.9.2-paper-26.2` tag preserves the working source snapshot
before the Paper 26.3 upgrade, including the outstanding CMI runtime metadata
alignment and its documentation drift checks. Version `2.5.9.2`, build `228`,
compiles against `io.papermc.paper:paper-api:26.2.build.84-stable` and emits Java 25
bytecode. Plugin identity remains `LootChest`, main class
`fr.black_eyes.lootchest.Main`, data folder `plugins/LootChest/`.

On 2026-09-25, `mvn -o clean verify` with JDK `25.0.4.1` passed 106 unit tests
and 12 release JAR integration checks, with no failures, errors or skips.
The named artifact is `1MB-LootChest-v2.5.9.2-228-CMI-j25-26.2.jar`.
No Paper 26.3 runtime testing is claimed for this snapshot.

Historical smoke logs from 2026-09-15 show Paper 26.2 build 84 starting on
Java 25.0.4.1 and Java 26.0.2.1, activating all 28 saved container fixtures,
processing info/help/list/reload/despawn/respawn commands and stopping cleanly.
Those logs identify a dirty source build (`f7d560c5d3d7-dirty`) and CMI 9.8.8.5
with CMILib 1.5.9.9, so they are evidence for the prior working tree, not fresh
runtime validation of this snapshot or of the documented CMI 9.8.9.9 pair.

The stopped maintained instance remains intact at `servers/Paper-26.2/`.
It contains LootChest build 226, CMI 9.8.9.9 and CMILib 1.5.9.9; PaperScript
records build 84 installed and build 121 staged, which the existing launcher
prefers. It must not be relabelled as a build-228 test. The separate historical
build-228 test instances remain under `servers/jdk-25.0.4.1-verification/`.

Prior artifacts, reports and smoke logs are preserved locally under
`archive/paper-26.2-snapshot-20260925/`. Servers, worlds, configurations, databases,
logs, dependency JARs and compiled artifacts stay outside Git.
The historical live approval remains
`1mb-lootchest-v2.5.9.1-build225-approved`; this snapshot is not a new gameplay
approval or a promotion of `master`.
