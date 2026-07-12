# Troubleshooting

## Lootbox Does Not Enable

Confirm Paper 26.2 and Java 25 first. `UnsupportedClassVersionError` means the Java
runtime is too old. Remove duplicate LootChest/Lootbox jars and inspect the first
LootChest exception in `logs/latest.log` rather than later shutdown noise.

## Hologram Is Missing

- Confirm compatible CMI and CMILib builds enabled before LootChest.
- Confirm `UseHologram: true`.
- Run `/lc reload`, then `/lc respawnall`.
- Look for `CMI is not enabled` or `LootChest holograms disabled after CMI error`.
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
manual edit. Stop Paper before major manual data changes and keep a backup.

## Commands Are Denied

`/lc info` defaults to everyone. Other commands default to operators. Grant the
specific `lootchest.<subcommand>` permission or use `lootchest.admin`/
`lootchest.*` for trusted administrators. `/lc create`, `/lc edit`, `/lc getname`,
`/lc setpos`, and `/lc tp` must be run in-game.

## No Proxy Announcement

Confirm proxy support is enabled, `respawn_notify.bungee_broadcast: true`, and a
player is online. Otherwise use global or per-world announcements.

## Privacy

The custom build defaults its update checker off and does not start bStats metrics.
If another plugin reports metrics or update notices, identify the plugin name in
the message before changing Lootbox configuration.
