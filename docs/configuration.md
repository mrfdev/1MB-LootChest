# Configuration

Lootbox stores global behavior in `plugins/LootChest/config.yml`, translatable
messages and menu labels in `lang.yml`, and persistent Lootbox definitions in
`data.yml`. The folder name remains `LootChest` for update compatibility.

## General Settings

| Key | Default | Meaning |
| --- | --- | --- |
| `CheckForUpdates` | `false` | Run the upstream update check during startup. The 1MoreBlock build keeps it off. |
| `Debug` | `false` | Log verbose adapter details such as the selected falling-package implementation. |
| `ConsoleMessages` | `true` | Enable normal plugin startup/status messages. |
| `EnableLootin` | `false` | Integrate compatible spawned containers with Lootin. |
| `Cooldown_Before_Plugin_Start` | `0` | Delay chest loading, in seconds, to allow worlds to load. |
| `UseHologram` | `true` | Request CMI holograms. They are disabled safely when CMI is unavailable. |
| `Hologram_distance_to_chest` | `1` | Vertical hologram offset. |
| `RemoveEmptyChests` | `true` | Remove the physical container when it is closed empty. |
| `RemoveChestAfterFirstOpening` | `false` | Remove the physical container after its first completed opening. |
| `Destroy_Naturally_Instead_Of_Removing_Chest` | `true` | Drop the container item when a Lootbox is collected or broken. |
| `Protect_From_Explosions` | `false` | Keep Lootboxes out of explosion block removal. |
| `PreventHopperPlacingUnderLootChest` | `true` | Block nearby hopper placement and movement. |
| `Radius_Without_Monsters_For_Opening_Chest` | `0` | Require no hostile mobs within this radius; `0` disables the check. |
| `respawn_protection_time_in_second_by_default` | `0` | Default interaction protection after spawn. |
| `Minimum_Number_Of_Players_For_Natural_Spawning` | `0` | Minimum online players for timed spawning. |
| `Minimum_Number_Of_Players_For_Command_Spawning` | `0` | Minimum online players for bulk command spawning. |

## Rewards and Respawning

| Key | Default | Meaning |
| --- | --- | --- |
| `default_reset_time` | `10` | Default respawn interval in minutes. |
| `default_item_chance` | `100` | Default percentage chance assigned to each saved item. |
| `Max_Filled_Slots_By_Default` | `0` | Default maximum rolled slots; `0` means no extra limit. |
| `save_Chest_Locations_At_Every_Spawn` | `false` | Persist new random locations immediately after spawning. |
| `SaveDataFileDuringReload` | `false` | When true, save memory to disk before reload; when false, reload edited `data.yml`. |

Reward contents, chances, respawn time, current/anchor position, random radius,
hologram text, particle, message toggles, protection, container type, and last reset
time are stored per Lootbox in `data.yml`.

## Random Spawn Safety

| Key | Default | Meaning |
| --- | --- | --- |
| `use_players_locations_for_randomspawn` | `false` | Center random spawning on an online player instead of the saved origin. |
| `allow_spawning_on_water` | `false` | Permit water spawn locations. |
| `spawn_on_non_solid_blocks` | `false` | Permit candidate locations on non-solid support blocks. |
| `Minimum_Height_For_Random_Spawn` | `0` | Lower random-spawn Y boundary. |
| `Max_Height_For_Random_Spawn` | `200` | Upper random-spawn Y boundary. |
| `WorldBorder_Check_For_Spawn` | `true` | Reject locations outside the world border. |
| `Prevent_Chest_Spawn_In_Protected_Places` | `false` | Consult supported claim/region plugins before random spawning. |

## Particles

| Key | Default | Meaning |
| --- | --- | --- |
| `Particles.enable` | `true` | Enable chest particle tasks and the editor entry. |
| `Particles.default_particle` | `FLAME` | Particle assigned to newly created Lootboxes. |
| `Particles.fallback_particle` | `FLAME` | Safe replacement for removed, invalid, or payload-requiring saved particles. |
| `Particles.number` | `10` | Particles spawned per update. |
| `Particles.respawn_ticks` | `20` | Scheduler interval in ticks. |
| `Particles.radius` | `0.3` | Position spread on each axis. |
| `Particles.speed` | `0.05` | Paper particle extra/speed value. |

The editor list is generated from the running Paper API and includes only particles
whose data type is `Void`. This prevents options that require color, block, item,
trail, or vibration payloads from silently failing. Legacy saved names are mapped
when a safe equivalent exists.

## Falling Package

| Key | Default | Meaning |
| --- | --- | --- |
| `Fall_Effect.Block` | `CHEST` | Material rendered by the falling package. |
| `Fall_Effect.Height` | `50` | Starting height above the destination. |
| `Fall_Effect.Enabled` | `true` | Default effect state for newly created Lootboxes. |
| `Fall_Effect.Enable_Fireworks` | `true` | Add fireworks during the descent. |
| `Fall_Effect.Speed` | `0.8` | Downward animation speed. |

Paper 26.2 uses the bundled native `v_26_2` adapter.

## Timer and Notifications

`Timer_on_hologram` controls the optional CMI countdown and its MiniMessage format.
Available timer substitutions are `%Hours`, `%Minutes`, `%Seconds`, `%Hsep`, `%Msep`,
`%Ssep`, and `%Hologram`.

`respawn_notify` controls natural, command, bulk, world-only, and collection
messages. Notification text supports MiniMessage plus `[Chest]`, `[World]`, `[x]`,
`[y]`, and `[z]` where the corresponding message path supplies them.

## Reload and Restart Behavior

Use `/lc reload` for `config.yml`, `lang.yml`, and `data.yml`. It rebuilds the
particle catalog and respawns loaded Lootboxes. Restart Paper after adding/removing
CMI or other integration plugins, changing `Particles.enable`, or changing the
particle scheduler interval so startup-only hooks and tasks are recreated cleanly.

MiniMessage formatting is accepted throughout locale, menus, notifications, and
hologram text. Existing legacy color codes remain readable for migration.
