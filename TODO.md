# LootChest Paper Modernization

This roadmap starts from the known-good Paper 26.2 / Java 25 CMI build on `master`.
Each stage should leave a buildable, testable plugin and should be committed separately.

- [ ] **Release baseline discipline**
  - [x] Preserve build 196 with the `1mb-lootchest-v2.5.9.1-build196-2026-snapshot` tag.
  - [ ] Promote candidate builds to `master` only after Paper smoke tests and manual gameplay approval.
  - [ ] Show the artifact build number, source commit, Paper target, and Java target at startup and in `/lc info`.
  - [ ] Tag every live-approved artifact and retain the immediately previous rollback jar.

- [x] **1. Low-risk modernization**
  - [x] Compile against the latest Paper 26.2 API while retaining Java 25 bytecode.
  - [x] Let Paper provide the matching Adventure API instead of pinning a conflicting version.
  - [x] Standardize Maven and resource encoding on UTF-8.
  - [x] Remove dead metrics code and dependencies that have no runtime use.
  - [x] Remove the upstream update checker and its outbound request code.
  - [x] Replace straightforward deprecated Paper/Bukkit calls with their current equivalents.
  - [x] Remove obsolete Minecraft 1.x runtime branches while preserving config, chest data, commands, and behavior.
  - [x] Build and smoke-test the result on Paper 26.2.
- [ ] **2. Scheduler and spawn-work cleanup**
  - [ ] Replace deprecated async-to-sync scheduler chains with explicit Paper scheduler APIs.
  - [ ] Keep Bukkit world/entity/inventory access on the server thread.
  - [ ] Batch or stagger large chest reload/respawn work to avoid main-thread spikes.
- [ ] **3. Listener and lifecycle hardening**
  - [ ] Correct inventory raw-slot, drag, close, and quit handling.
  - [ ] Track chest access from `InventoryOpenEvent` rather than fragile interaction assumptions.
  - [ ] Audit explosion, hopper, piston, and protection handling for every supported container.
  - [ ] Model spawn, open, empty, break, despawn, and respawn as idempotent chest state transitions.
  - [ ] Index Lootboxes by world UUID and block coordinates instead of scanning every saved chest.
- [ ] **4. Remove unsupported platforms and integrations**
  - [ ] Remove Bungee messaging, plugin channels, `SpigotConfig`, copied byte-stream helpers, and proxy config defaults.
  - [ ] Remove Spigot, Bungee, Velocity, Folia, and Minecraft 1.x support claims from maintained documentation.
  - [ ] Update WorldGuard to its current Paper-compatible API and remove the WorldGuard 6 path.
  - [ ] Inventory the actual 1MoreBlock protection plugins and remove every unused compatibility integration and Maven dependency.
  - [ ] Keep CMI/CMILib integration runtime-optional and document the supported versions.
- [ ] **5. Remove version-specific NMS falling packages**
  - [x] Remove the unused falling-package feature rather than replacing it with another animation.
  - [x] Force legacy config and saved chest flags to `false` without breaking existing YAML data.
  - [x] Remove the command, editor toggle, runtime animation code, reflection, NMS packets, Spigot snapshot, and SpecialSource.
  - [x] Delete all inactive legacy adapter source modules after the active build no longer references them.
  - [x] Smoke-test copied live data on Paper 26.2, including reload, respawn, migration, and clean shutdown.
  - [ ] Complete manual empty/break/respawn gameplay validation before promoting the candidate.
- [ ] **6. Replace the shaded configuration framework**
  - [ ] Replace `SimpleJavaPlugin` with a small local configuration/language manager on plain `JavaPlugin`.
  - [ ] Preserve existing YAML files, defaults, comments where possible, and migration behavior.
  - [ ] Remove the shaded framework dependency after reload and failure-path testing.
- [ ] **7. Automated compatibility coverage**
  - [ ] Add tests for config and saved chest-data migration.
  - [ ] Add tests for particle fallback and supported container classification.
  - [ ] Add regression coverage for emptying, breaking, despawning, reloading, and hologram cleanup.
  - [ ] Add a repeatable Paper 26.2 smoke test for enable, commands, reload, respawn, and clean shutdown.
  - [ ] Assert release jars contain no updater, metrics, DecentHolograms, proxy, NMS, or legacy adapter classes.
  - [ ] Validate chest, trapped chest, barrel, shulker, and every copper chest state.
