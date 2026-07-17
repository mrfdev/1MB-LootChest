# LootChest Paper Modernization

This roadmap starts from the known-good Paper 26.2 / Java 25 CMI build on `master`.
Each stage should leave a buildable, testable plugin and should be committed separately.

- [x] **Release baseline discipline**
  - [x] Preserve build 196 with the `1mb-lootchest-v2.5.9.1-build196-2026-snapshot` tag.
  - [x] Preserve manually approved build 198 with the `1mb-lootchest-v2.5.9.1-build198-approved` tag.
  - [x] Preserve manually approved build 199 with the `1mb-lootchest-v2.5.9.1-build199-approved` tag.
  - [x] Preserve manually approved build 200 with the `1mb-lootchest-v2.5.9.1-build200-approved` tag.
  - [x] Preserve build 203 as the final direct WorldGuard integration rollback with the `1mb-lootchest-v2.5.9.1-build203-last-worldguard-support` tag.
  - [x] Promote candidate builds to `master` only after Paper smoke tests and manual gameplay approval.
  - [x] Show the artifact build number, source commit, Paper target, and Java target at startup and in `/lc info`.
  - [x] Tag every live-approved artifact and retain the immediately previous rollback jar.

- [x] **1. Low-risk modernization**
  - [x] Compile against the latest Paper 26.2 API while retaining Java 25 bytecode.
  - [x] Let Paper provide the matching Adventure API instead of pinning a conflicting version.
  - [x] Standardize Maven and resource encoding on UTF-8.
  - [x] Remove dead metrics code and dependencies that have no runtime use.
  - [x] Remove the upstream update checker and its outbound request code.
  - [x] Replace straightforward deprecated Paper/Bukkit calls with their current equivalents.
  - [x] Remove obsolete Minecraft 1.x runtime branches while preserving config, chest data, commands, and behavior.
  - [x] Build and smoke-test the result on Paper 26.2.
- [x] **2. Scheduler and spawn-work cleanup**
  - [x] Replace deprecated async-to-sync scheduler chains with explicit Paper scheduler APIs.
  - [x] Keep Bukkit world/entity/inventory access on the server thread.
  - [x] Batch or stagger large chest reload/respawn work to avoid main-thread spikes.
  - [x] Track all plugin tasks in one registry and cancel them on reload and shutdown.
  - [x] Complete manual empty, break, hologram, reload, and respawn gameplay validation for build 199.
- [ ] **3. Listener and lifecycle hardening**
  - [x] Correct inventory raw-slot, drag, close, and quit handling.
  - [x] Track chest access from `InventoryOpenEvent` rather than fragile interaction assumptions.
  - [x] Audit explosion, hopper, piston, and protection handling for every supported container.
  - [ ] Model spawn, open, empty, break, despawn, and respawn as idempotent chest state transitions.
  - [ ] Index Lootboxes by world UUID and block coordinates instead of scanning every saved chest.
- [ ] **4. Remove unsupported platforms and integrations**
  - [x] Remove Bungee messaging, plugin channels, `SpigotConfig`, copied byte-stream helpers, and proxy config defaults.
  - [x] Remove Spigot, Bungee, Velocity, Folia, and Minecraft 1.x support claims from maintained documentation.
  - [x] Remove the unused direct WorldGuard random-spawn integration and its compile dependency; retain only soft load ordering for protection interoperability.
  - [ ] Inventory the actual 1MoreBlock protection plugins and remove every unused compatibility integration and Maven dependency.
  - [x] Keep CMI/CMILib integration runtime-optional and document the supported versions.
- [x] **5. Remove version-specific NMS falling packages**
  - [x] Remove the unused falling-package feature rather than replacing it with another animation.
  - [x] Force legacy config and saved chest flags to `false` without breaking existing YAML data.
  - [x] Remove the command, editor toggle, runtime animation code, reflection, NMS packets, Spigot snapshot, and SpecialSource.
  - [x] Delete all inactive legacy adapter source modules after the active build no longer references them.
  - [x] Smoke-test copied live data on Paper 26.2, including reload, respawn, migration, and clean shutdown.
  - [x] Complete manual empty/break/respawn gameplay validation before promoting the candidate.
- [ ] **6. Replace the shaded configuration framework**
  - [ ] Replace `SimpleJavaPlugin` with a small local configuration/language manager on plain `JavaPlugin`.
  - [ ] Preserve existing YAML files, defaults, comments where possible, and migration behavior.
  - [ ] Remove the shaded framework dependency after reload and failure-path testing.
- [ ] **7. Automated compatibility coverage**
  - [x] Add tests for config and saved chest-data migration.
  - [x] Add tests for particle fallback and supported container classification.
  - [x] Add regression coverage for emptying, breaking, despawning, reloading, and hologram cleanup.
  - [x] Add a repeatable Paper 26.2 smoke test for enable, commands, reload, respawn, and clean shutdown.
  - [ ] Assert release jars contain no updater, metrics, DecentHolograms, proxy, NMS, or legacy adapter classes.
  - [ ] Validate chest, trapped chest, barrel, shulker, and every copper chest state.
