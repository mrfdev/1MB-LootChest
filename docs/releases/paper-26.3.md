# Paper 26.3 upgrade: 2.5.9.3, build 229

The upgrade continues snapshot commit `31d35e9a8673`, annotated tag
`v2.5.9.2-paper-26.2`, on `codex/paper-26.3`. The release source is identified by
the annotated tag `1mb-lootchest-v2.5.9.3-build229-paper-26.3`. Its tag annotation
records the final clean artifact checksum, source commit and smoke evidence.
Version and build were each incremented once; failed preparation attempts reused
build 229. No `master` promotion or gameplay-approved tag is implied.

The one distributable is
`1MB-LootChest-v2.5.9.3-229-CMI-j25-26.3.jar`. Plugin identity `LootChest`,
main class `fr.black_eyes.lootchest.Main`, command/permission identities and
`plugins/LootChest/` remain unchanged. The only runtime source change migrates
the exact bundled 26.2 help footer to 26.3, preserving customized text. The
existing container, reward, persistence and hologram behavior remains in place.

## Exact target and upstream review

Selected on 2026-09-25: Paper **26.3 build 41, ALPHA**, commit `a15fed9c16a5`.
The builds service listed only ALPHA builds for 26.3; experimental use was
explicitly authorized. No 26.2 fallback is configured. Compile/test API:
`io.papermc.paper:paper-api:26.3.build.41-alpha`.
Server JAR SHA-256, verified against the downloads service:
`2b77166ee61886a9bc9ab33dc9e4847fa3538b36d9ba6e5f2fa7ed90973aa748`.

Review began with the live [PaperMC documentation index](https://docs.papermc.io/llms.txt).
The [build metadata](https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/41),
[Maven metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml)
and [26.3 Javadocs](https://jd.papermc.io/paper/26.3/) agreed on build 41.
The Javadoc title explicitly identified `26.3.build.41-alpha` during review;
there is no separate build-coordinate URL in that Javadoc service.

The [project setup guide](https://docs.papermc.io/paper/dev/project-setup/)
confirms build-specific API coordinates. The [roadmap](https://docs.papermc.io/paper/dev/roadmap/)
discusses a future ItemStack interface and recommends factory creation; it does
not require an unrelated refactor for this update. Existing code compiles
without compiler or deprecation warnings against the selected API.
[Inventory click](https://jd.papermc.io/paper/26.3/org/bukkit/event/inventory/InventoryClickEvent.html),
[drag](https://jd.papermc.io/paper/26.3/org/bukkit/event/inventory/InventoryDragEvent.html)
and [scheduling](https://docs.papermc.io/paper/dev/scheduler/) contracts were
reviewed. Existing custom inventory ownership and deferred menu transitions are
retained. No NMS, reflection adapter or new asynchronous Bukkit access was added.

The [global configuration reference](https://docs.papermc.io/paper/reference/global-configuration/)
and [world reference](https://docs.papermc.io/paper/reference/world-configuration/)
were reviewed. Piston duplication, headless-piston and permanent-block-break
exploits, unsafe end portals and tripwire validation bypass remain disabled;
oversized-item sanitizer exclusions remain empty. World settings were retained.

## Verification and runtime

Compilation and unit tests use JDK `25.0.4.1+1-LTS-5` at the requested path,
selected through `JAVA_HOME` and `PATH`, with release/source/target 25.
All 80 plugin class files have major version **69**, including the release JAR
verified by the packaging tests. The test server uses JDK **27+35-2325**.

The clean Maven build and subsequent documentation drift verification passed
**106 unit tests and 12 release JAR checks**, with zero failures, errors or
skips. The artifact policy checks excluded retired updater, metrics, proxy,
DecentHolograms, NMS and configuration-framework classes. Drift coverage includes
POM/generated metadata, plugin.yml, public documentation, maintained agent
instructions, the CMI pair and bundled help target. The help migration check
also verifies custom text preservation and idempotence.

Development runtime smoke passed in the copied local instance: readiness,
version/build/source/API diagnostics, active CMI holograms, 106 available
particles, help/list/reload/despawn/respawn, save and clean shutdown. All **35/35**
existing saved containers activated; the loaded-world audit reported **35 present,
0 absent, 0 wrong, 0 unavailable, 0 issues**, with index **35/35** and no rejected,
deferred or failed definitions. TCP/UDP ports were released after shutdown.
The final clean release is rebuilt from the tag's source and must pass the same
smoke before tagging or deployment; its result and checksum are in the tag
annotation and local `target/smoke-paper-26.3/` evidence.

Early harness attempts caught preserved customized help wording and a changed
26.3 shutdown log marker. Both checks were corrected without modifying gameplay.
The audit now force-loads the ten fixture chunks so an unloaded world cannot
produce misleadingly clean results. Spotlight reads are distinguished from
server processes when checking stopped state.

Remaining third-party notices: OSHI cannot map macOS 27.0 to a codename, and
LuckPerms' Commodore library emits Java 27 reflective final-field mutation
warnings. Neither prevented plugin enable or clean shutdown. These were not
silenced by changing Java flags. No LootChest compatibility exception was seen.

Manual checks remain: player create/edit/open/empty/break flows, each supported
container type, click/shift/drag/double-click/hotbar/offhand/drop/creative paths,
close/quit/reconnect and stale menus, unauthorized Bolt access, visible particles
and holograms, full inventories and items carrying third-party metadata. This is
an automated compatibility release, not a fresh live gameplay approval.

## Instances, dependencies and deployment

The rollback directory is `/Users/floris/Projects/Codex/Floodgate/servers/Paper-26.2`;
all 735 file hashes were unchanged after the initial upgrade smoke tests. The
new directory is `/Users/floris/Projects/Codex/Floodgate/servers/Paper-26.3`.
It uses TCP 32931, reserved/disabled query UDP 32932 and RCON TCP 32933, and
PaperScript session `lootchest-paper-26.3-32931`. Copied launcher/download state
was archived and rebuilt for the new directory, target and channel. DiscordSRV
remains inactive. See [local setup](../test-server.md) for the dependency table.

Updated runtime JARs: CMI 9.8.10.1, CMILib 1.6.0.0, CoreProtect 25.0,
Jobs 5.2.6.6, LuckPerms 5.5.85 and PyroLib 1.6.0; Bolt 1.2.20 was added to the
local test stack. All came from copies of the supplied shared JARs, with no
shared configuration or player data imported. Public compile APIs remain CMI
9.8.6.4 and CMILib 1.5.9.6 after successful runtime compatibility verification.

The shared deployment destination is
`/Users/floris/MinecraftServer/test-1mb-3.14-mc-26.3/plugins`.
The replacement scope is only top-level
`1MB-LootChest-v2.5.9.2-227-CMI-j25-26.2.jar` and the obsolete project cache
`.paper-remapped/Lootchest-2.5.8.jar`, both identified by their `LootChest`
plugin manifests. Old copies must be archived recoverably outside `plugins/`.
Install the exact clean smoke-tested build-229 artifact, verify its destination
checksum and all unrelated plugin checksums, and record the backup location.
The shared server must remain stopped throughout; it is never a smoke target.
The deployment receipt is retained locally in `archive/paper-26.3-upgrade/`.
