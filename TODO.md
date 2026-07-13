# LootChest Paper Modernization

This roadmap starts from the known-good Paper 26.2 / Java 25 CMI build on `master`.
Each stage should leave a buildable, testable plugin and should be committed separately.

- [x] **1. Low-risk modernization**
  - [x] Compile against the latest Paper 26.2 API while retaining Java 25 bytecode.
  - [x] Let Paper provide the matching Adventure API instead of pinning a conflicting version.
  - [x] Standardize Maven and resource encoding on UTF-8.
  - [x] Remove dead metrics code and dependencies that have no runtime use.
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
  - [ ] Make Bungee messaging safe when no player is online.
- [ ] **4. Optional-dependency cleanup**
  - [ ] Update WorldGuard to its current Paper-compatible API and remove the WorldGuard 6 path.
  - [ ] Remove obsolete or unused direct dependencies and compatibility integrations.
  - [ ] Keep CMI/CMILib integration runtime-optional and document the supported versions.
- [ ] **5. Remove version-specific NMS falling packages**
  - [ ] Prototype the falling-package effect with supported Paper display/entity APIs.
  - [ ] Preserve the current appearance, timing, cleanup, and collision behavior.
  - [ ] Remove reflection-based adapter selection and the legacy `fall_effect` modules after parity testing.
- [ ] **6. Replace the shaded configuration framework**
  - [ ] Replace `SimpleJavaPlugin` with a small local configuration/language manager on plain `JavaPlugin`.
  - [ ] Preserve existing YAML files, defaults, comments where possible, and migration behavior.
  - [ ] Remove the shaded framework dependency after reload and failure-path testing.
- [ ] **7. Automated compatibility coverage**
  - [ ] Add tests for config and saved chest-data migration.
  - [ ] Add tests for particle fallback and supported container classification.
  - [ ] Add regression coverage for emptying, breaking, despawning, reloading, and hologram cleanup.
  - [ ] Add a repeatable Paper 26.2 smoke test for enable, commands, reload, falling effect, and clean shutdown.
