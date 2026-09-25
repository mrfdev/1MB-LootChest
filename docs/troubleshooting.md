# Troubleshooting

## Lootbox Does Not Enable

Confirm Paper 26.3 build 41 (`ALPHA`) and Java 27 first. Compilation remains on JDK `25.0.4.1` with Java 25 bytecode. `UnsupportedClassVersionError` means the Java
runtime is too old. Remove duplicate LootChest/Lootbox jars and inspect the first
LootChest exception in `logs/latest.log` rather than later shutdown noise.

## Hologram Is Missing

- Confirm CMI `9.8.10.1` and CMILib `1.6.0.0` enabled before LootChest.
- Confirm `UseHologram: true`.
- Run `/lc reload`, then `/lc respawnall`.
- Run `/lc info` and check the `Holograms` status line.
- Look for `CMI and CMILib are not both enabled` or
  `LootChest holograms disabled after CMI error`.
- Use `/lc setholo <name> <text>`; `none`, `_`, and blank-like values disable it.

## Particle Is Missing

Open `/lc edit <name>` and choose from the generated particle menu. Only particles
safe for the running Paper API are listed. If a saved value was removed or requires
extra data, Lootbox warns once and uses `Particles.fallback_particle`.

After editing particle configuration, run `/lc reload`. Restart after changing
`Particles.enable` or `Particles.respawn_ticks`.

## Container Is Absent

The Lootbox may be waiting for its respawn timer or may have been removed after it
was emptied. Use `/lc list`, `/lc respawn <name>`, or `/lc respawnall`. If random
spawning is enabled, check the current position with administrative tools rather
than assuming it returned to the original block.

When the log says no valid respawn location was found, review water, support block,
height, border, region, and random-radius settings.

## Container Cannot Be Placed Nearby

Lootbox prevents supported containers from being placed next to an active saved
Lootbox location. Hopper placement and piston movement can also be blocked when
automation protection is enabled. This is expected reward-system protection.

## Empty Container Does Not Disappear

Confirm `RemoveEmptyChests: true`, then close the inventory after taking the final
item. Test with `/lc respawn <name>` and inspect the log for event exceptions. The
physical container can remain when removal settings are intentionally disabled.

## Reload Did Not Use Hand-Edited Data

With `SaveDataFileDuringReload: false`, `/lc reload` reads edited `data.yml`. With
it set to `true`, the in-memory definitions are saved first and can overwrite a
manual edit. The value read from the newly edited `config.yml` controls that same
reload, rather than taking effect one reload later. Stop Paper before major
manual data changes and keep a backup.

## Saved Definition Was Rejected

Lootbox keeps valid saved definitions active when one child in `data.yml` is
malformed. The rejected child remains present and reserved, is not replaced from
a numbered backup, and cannot be overwritten by `/lc create` or the developer
API. Run `/lc audit` for the saved-definition totals, then `/lc audit <name>` for
the exact invalid paths.

Correct the reported fields in `data.yml` with
`SaveDataFileDuringReload: false`, then run `/lc reload`. Required anchor fields
are `position.world` plus finite numeric `position.x`, `position.y`, and
`position.z`; horizontal coordinates must remain inside Minecraft's safe range,
and loaded-world Y values must fit that world's build height. Inventory keys
must resolve uniquely to slots from `0` through `26`, and their values must
remain Bukkit-serialized item stacks. A missing inventory is a valid empty
definition. Random radius must be a nonnegative integer. If a world is merely
unloaded, the definition is reported as deferred rather than malformed.

Whole-file backup recovery is reserved for YAML that cannot be parsed, contains
a Bukkit object that cannot be deserialized, or lacks a usable top-level
`chests` section. A `data.yml.invalid-<timestamp>` file therefore signals a
document-level recovery, not an ordinary per-child schema rejection.

If reload cannot safely remove every currently active container, it aborts
without publishing the candidate files. A disk-loaded candidate is copied to
`data.yml.reload-aborted-*.yml`, and already removed containers are restored from
full block-state snapshots without rerolling rewards. A conflicting block is
never overwritten during rollback; that definition remains inactive and is
reported by `/lc audit`. Restore the unavailable world or resolve the logged
cleanup error, then retry.

## Commands Are Denied

`/lc info` defaults to everyone. Other commands default to operators. Grant the
specific `lootchest.<subcommand>` permission or use `lootchest.admin`/
`lootchest.*` for trusted administrators. `/lc create`, `/lc edit`, `/lc getname`,
`/lc setpos`, and `/lc tp` must be run in-game.

## Privacy

The custom build contains neither the upstream update checker nor bStats metrics.
If another plugin reports metrics or update notices, identify the plugin name in
the message; Lootbox cannot originate either request.
