# Paper 26.3 stable migration: 2.5.9.3, build 230

Verified on 2026-10-11. Build 230 replaces the development target from build
229's alpha API with Paper 26.3 STABLE. Minecraft remains 26.3, the semantic
version remains 2.5.9.3, and the build number increases once from 229 to 230.
The [2026-09-25 alpha upgrade record](paper-26.3.md) is historical evidence.
Manual gameplay approval and live deployment remain separate.

## Published target

| Component | Exact selection |
| --- | --- |
| Paper | 26.3 build 175, `STABLE` |
| Paper commit | `bd9daa8fba19cb288d690c1621ad1749de3e0a78` |
| Paper API | `io.papermc.paper:paper-api:26.3.build.175-stable`, provided |
| Server SHA-256 | `ece5afb6d0cdf0c930489618bf92341e33d2ebcd4f21e6d365daff6ed26dafb0` |
| Build | Maven 3.10.0, JDK `25.0.4.1+1-LTS-5` |
| Compiler release/source/target | 25 (class-file major 69) |
| Isolated runtime | JDK `27+35-2325` |
| CMI / CMILib compile APIs | `9.8.6.4` / `1.5.9.6`, provided |
| CMI / CMILib tested runtime pair | `9.8.10.1` / `1.6.0.0` |
| Artifact | `target/1MB-LootChest-v2.5.9.3-230-CMI-j25-26.3.jar` |

Discovery began with the live [PaperMC index](https://docs.papermc.io/llms.txt).
The [Fill build list](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds)
selected build 175 as the latest STABLE build, and its
[exact record](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/175)
supplied the download URL and checksum. The
[Maven metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)
contained `26.3.build.175-stable`; no coordinates were inferred from a naming guess.
The downloaded server's SHA-256 matched Fill.

The [hosted 26.3 Javadocs](https://jd.papermc.io/paper/26.3/) still identified
`26.3.build.157-beta` during this review, and the build-coordinate URL returned
404. Exact contracts were therefore checked in the published
[175-stable Javadoc artifact](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/26.3.build.175-stable/paper-api-26.3.build.175-stable-javadoc.jar),
whose title identifies `26.3.build.175-stable`. Official metadata and that Javadoc
artifact are retained only in the ignored local archive. Neither documentation
index nor `llms-full.txt` is vendored.

Paper's [runtime requirements](https://docs.papermc.io/paper/getting-started/)
specify Java 25 or newer. The existing Java 25 bytecode remains compatible; the
project's selected local runtime stays Java 27. A separate Java 25 server run
was not performed.

## Changes and compatibility review

- Root Maven properties pin the stable API, server build, channel and checksum.
  The reactor has no Gradle, paperweight/dev-bundle, dependency locks or CI
  workflows to update. `plugin.yml` keeps the valid `api-version: '26.3'` format.
- The ignored local PaperScript configuration, example and state use STABLE,
  build 175, its verified URL and checksum, with automatic upgrades disabled.
  Build 41 and previous launcher/fixture state were archived recoverably.
- A real-server creation test exposed a compatibility bug: Paper now formats
  `World.toString()` as `CraftWorld{key=minecraft:overworld}`. LootChest parsed
  the former `CraftWorld{name=...}` debug string, so new chests could be
  registered with an invalid runtime world name and fail to spawn. The helper
  now uses `World.getName()`; regression cases cover ordinary, spaced and nether
  world names. Existing saved definitions already used the actual Bukkit name,
  so no stored-data migration is required.
- The exact Javadocs describe `getName()` as obsolete in favor of dimension
  keys, but it remains supported. Retaining it here deliberately preserves the
  existing named-world storage and configuration contract. A future conversion
  to keys would require a separate data migration.
- The [roadmap](https://docs.papermc.io/paper/dev/roadmap/),
  [scheduling](https://docs.papermc.io/paper/dev/scheduler/),
  [inventory ownership](https://docs.papermc.io/paper/dev/custom-inventory-holder/),
  [events](https://docs.papermc.io/paper/dev/event-listeners/),
  [PDC](https://docs.papermc.io/paper/dev/pdc/), and
  [data components](https://docs.papermc.io/paper/dev/data-component-api/) were
  reviewed alongside exact API contracts. Custom inventory ownership, deferred
  menu transitions, cancellation-aware protection events, main-thread Bukkit
  access and the existing file executor/reload transaction remain intact.
- Plugin identity, commands, permissions, configuration and saved-data schema
  remain unchanged. LootChest has no LuckPerms API or command-dispatch integration;
  this migration introduces no player-name targeting or external identity lookup.
- README, agent instructions, installation, release process, test setup,
  troubleshooting and public documentation metadata now describe the stable
  target. Drift tests reject unstable API/channel pins and mismatched Java,
  server-checksum or documentation metadata. The smoke runner also works on the
  Mac's Python 3.9, without depending on Python 3.11's `hashlib.file_digest`.

The Paper global/world defaults were reviewed on the exact running build.
Piston duplication, headless pistons, permanent block breaking, unsafe end portals
and tripwire validation bypass remain disabled; sanitizer exclusions remain empty.

## Verification

Build and smoke evidence is retained under `archive/paper-26.3-stable/` and
`target/smoke-paper-26.3/`. The repeatable commands and test-fixture boundaries
are documented in [the local setup](../test-server.md).

The suite includes 110 unit cases, 13 release-JAR checks, and five Python harness
checks. World-name regression cases were observed failing before the fix and
passing afterward. The JAR checks verify Java 25 bytecode and exclude NMS,
retired integrations, updater/metrics code and the temporary runtime probe.

The server harness exercises new creation, plugin loading, info/help/list,
reload/despawn/respawn/audit, rejected malformed YAML with runtime retention,
valid retry, off-thread API refusal, two cold boots, saved definitions and clean
shutdown/port release. Its test-only metadata fixture exercises namespaced
third-party PDC, byte arrays, custom model data, enchantments, damage, glint
override and nested shulker contents through spawn, inventory delivery, reload
and restart, including isolation between a delivered item and its template.
The persistence comparison ignores respawn timestamps and YAML component-key
order, which Paper may vary between JVMs, while retaining all component values.

Final integrated and standalone results are recorded in each run's `result.json`.
The standalone profile temporarily archives optional local plugin JARs and
restores them afterward. Original fixtures contain 35 containers; the temporary
test plugin adds one persistent disposable fixture, for 36 audited containers.
All runtime work stays under `servers/Paper-26.3/`. The old `servers/Paper-26.2/`
and the shared server remain untouched.

The integration stack still emits an OSHI macOS codename warning and a
LuckPerms/Commodore reflective final-field mutation warning on Java 27. Neither
was suppressed by this migration. Review those dependencies before a future JDK
upgrade that enforces final-field restrictions.

## Remaining manual checks

No player client was available. These are required before live gameplay approval:

1. Join the isolated server with Java and Floodgate/Bedrock accounts. Give a test
   administrator the existing permissions using the actual server UUID. Confirm
   `/lc info` is public and an unprivileged player cannot create, edit, reload,
   give or respawn chests. Confirm names and Bedrock prefixes remain intact.
2. Create, edit, open, empty, break and respawn chest, trapped chest, barrel,
   shulker and copper variants. Exercise shift-click, drag, double-click, hotbar,
   offhand, drop and creative interactions, then close, quit, reconnect and reopen
   a menu after reload. Verify no stale menu changes or duplicated rewards.
3. Repeat with full inventories and real items carrying other plugins' metadata,
   filled containers and custom components. Check that overflow delivery and
   AutoSell interactions produce the intended items exactly once.
4. Lock a test Lootbox with Bolt and attempt open/break using an unauthorized
   account. Check no reward delivery, removal or respawn scheduling occurs.
   Visually verify particle choices, hologram placement and menu text.
5. Exercise rapid concurrent use, quit/kick/death/world changes and shutdown during
   reload. Forced process termination between persistence stages and disk-full
   injection were not performed; use a disposable copy with known data and
   backups, then inspect recovery and `/lc audit` after restart.

Automated API/inventory checks do not establish player-client behavior, full
inventory delivery, Bedrock protocol compatibility or live gameplay approval.
