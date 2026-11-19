# Paper, CMI and Oracle Java compatibility release, 20 October 2026

This records LootChest 2.5.9.3 / build 230 on `codex/cmi-paper-oct2026`, approved
and promoted to `master` on 2026-10-20. The previous build-175 candidate and
local server state were archived before replacement. No live or shared server
was changed. The later build-231 candidate is documented in the
[AutoSell verification record](autosell-2026-11-04.md).

## Approval record

The annotated `1mb-lootchest-v2.5.9.3-build230-approved` tag records manual
approval by mrfloris after in-game compatibility testing on 2026-10-20:

| Identity | Approved value |
| --- | --- |
| Source commit | `3dc612feb28e0c90694ed34ddc3bfe4b338d6ff5` |
| Artifact | `1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar` |
| SHA-256 | `e8a60ac9cc22ea974bac93c8c377f6e9459f14d818ebebf71dd3cba49b1464f5` |
| Previous approved rollback | Paper 26.2 build 225 |

The approval confirmed CMI hover/click messages and floating icons; the user
confirmed no further gameplay testing was needed for this artifact. The
`-paper-26.3` tag's earlier pending-approval note describes the preceding
automated verification. The later `-approved` tag records the approval outcome.
Neither that approval nor promotion deployed the JAR live.

Later Oracle toolchain verification at `563e4ca` reused build 230 with a different
checksum; it is not the artifact approved by this tag. Build 231 likewise needs
its own gameplay approval.

## Verified target

| Component | Selection |
| --- | --- |
| Paper | 26.3 build 207, STABLE |
| Paper API | `26.3.build.207-stable` |
| Paper SHA-256 | `2df3b846f70716907d380915cc026667a9cadedf99b1ec680b0c5ff0e6132526` |
| CMI | 9.8.11.0, from the user's Downloads |
| CMILib | 1.6.1.0, from the user's Downloads |
| Compiler | Oracle JDK `25.0.5+9-LTS-148`, release/source/target 25 |
| Runtime | Oracle JDK `27.0.1+9-62` |
| Artifact | `1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar` |

The provided public CMI/CMILib compile APIs remain 9.8.6.4 / 1.5.9.6.
Downloaded runtime hologram API signatures match the previously tested pair.
Other optional plugins were retained after their normal startup checks; no
unrelated dependency update was required.

Discovery began with the live [PaperMC index](https://docs.papermc.io/llms.txt).
The [exact build record](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/207)
supplied the verified server download and checksum; Maven metadata explicitly
listed the matching stable API. Hosted 26.3 Javadocs lagged at build 195, so API
contracts were checked against the published
[207 Javadoc artifact](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.3.build.207-stable/paper-api-26.3.build.207-stable-javadoc.jar).
The [Oracle 25.0.5](https://www.oracle.com/java/technologies/javase/25-0-5-relnotes.html)
and [27.0.1](https://www.oracle.com/java/technologies/javase/27-0-1-relnotes.html)
release notes were reviewed. Java bytecode remains version 25.

## Production fix found during testing

A saved `COPPER_CHEST` had naturally become `EXPOSED_COPPER_CHEST`. On a cold
boot before its respawn was due, the lifecycle audit recognized the physical
container but effect reactivation rejected its changed material. Its hologram
and particles therefore failed to return. This was an existing LootChest
inconsistency, not evidence of a changed CMI API.

`Lootchest.isGoodType(Block)` now uses the existing
`Mat.matchesContainerType` ownership rule, already used by the audit. All eight
copper oxidation/wax states represent the same container. Unrelated containers
remain rejected. The same correction applies to despawn/reload cleanup and
client block resends using that helper. No stored definitions are migrated.

The real-server probe was observed failing on `EXPOSED_COPPER_CHEST` before the
fix. It now exercises all eight states through the actual effect-reactivation
path, checks hologram registration with integrations present, rejects a barrel,
and restores the original block, inventory, metadata and effect state. The
original naturally oxidized container is also checked on cold startup before
any forced respawn.

## CMI changes and maintenance behavior

LootChest messages and `/lc list` / `/lc audit` clickable links use Paper
Adventure components directly, independently of CMILib RawMessage. The local
CMILib configuration uses the new default `LegacyRawMessage: false`.

CMI 9.8.11.0 restores floating `ICON:` items. `SICON:` is static. The local
fixture originally contained only text holograms, so two independent CMI demo
holograms were added near `spawn:-15,73,-55` for visual comparison. Blank `_`
lines provide space around the floating icon; existing LootChest data was not
rewritten to change icon semantics.

CMI's narrow `cmi hologram reload` / `cmi reload holograms` reloads permanent
CMI definitions and can discard transient third-party holograms. This also
exists in the previous CMI build; there is no public hologram-reload completion
event. Use `/lc reload` after a CMI hologram reload to recreate LootChest's
transient entries. Normal cold-start plugin order works without this extra step.
The separate local reload check records the observed limitation and recovery.

## Verification and evidence

The historical `mvn clean verify` run used Oracle JDK 25.0.5. Its suite passed
110 unit cases and 13 release-JAR checks, plus seven Python harness checks.
For current builds, use the validated `./mvnw` launcher and Oracle 25/27 workflow
in [the build guide](../build.md).

Integrated checks inspect CMI's real registry, transient settings, text writes,
advancing timers and complete cleanup on despawn. Both integrated and standalone
profiles exercise copper reactivation, metadata-rich rewards and nested shulker
contents, inventory/template isolation, rejected malformed reload and valid
retry, off-thread API refusal, saved definitions, two cold boots, clean shutdown
and released ports. The test-only probe is removed and excluded from releases.

Logs, toolchain versions, dependency hashes, regression evidence, and the
recoverable pre-update backup are under `archive/compat-2026-10-20/`.
Final clean-candidate smoke results are recorded in
`target/smoke-paper-26.3/<timestamp>/result.json` and retained in that archive.
Each result identifies the exact artifact checksum and source commit; only
`dirty: false` results establish the distributable candidate's automated status.

The generated Paper configuration retains safe exploit defaults and empty
oversized-component sanitizer exclusions. Existing OSHI macOS-version and
LuckPerms/Commodore reflective-final-field warnings remain visible. They did not
prevent startup or shutdown, but no warning-suppression flags were added.

## In-game approval

The steps below record the build-230 approval procedure.

Connect with Minecraft Java 26.3 to `127.0.0.1:32931` on this Mac once the
isolated server is running. `/lc tp newtest1` reaches the existing test area.
The floating/static icon comparison is nearby at approximately `-15,73,-55`.

Check `/lc list` click-to-edit and `/lc audit` click-to-teleport, hover text,
colors, existing text holograms, the icon comparison, and `/lc reload` while
standing still. Test CMI's new message backend with:

```text
/cmi ctellraw mrfloris <T>&aCMILib click test</T><H>&eRuns /lc info</H><C>/lc info</C>
```

Then cover representative containers, menu transitions and inventory actions
from the [release checklist](../release-process.md). The local server used for
these checks was a Java-client fixture; Bedrock protocol compatibility and
production-scale concurrency were not established by the automated probes.
The [approval record above](#approval-record) records the subsequent manual
decision and source promotion; no live deployment was performed.
