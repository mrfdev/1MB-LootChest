# Integrations

All integrations are optional unless the corresponding feature is required.

## CMI and CMILib

This 1MoreBlock edition creates transient CMI holograms. They are not written into
CMI's permanent hologram files. When CMI is missing, disabled, or its hologram
manager fails, Lootbox disables holograms safely while keeping containers active.

The supported and gameplay-tested Paper 26.2 pair is:

| Component | Version |
| --- | --- |
| CMI runtime | `9.8.8.5` |
| CMILib runtime | `1.5.9.9` |
| Public CMI API used to compile | `9.8.6.4` |
| Public CMILib API used to compile | `1.5.9.6` |

CMI and CMILib are Maven `provided` dependencies and Paper soft dependencies.
Neither jar is bundled into Lootbox. Both may be omitted when holograms are not
needed; chests, loot, particles, commands, data, reload, and respawn continue to
work. `/lc info` reports whether the integration is active and the actual runtime
versions.

Other CMI/CMILib version pairs are unvalidated. Lootbox warns once at startup when
the installed pair differs from the supported pair, then attempts to use the
public hologram API. An API or linkage failure disables only holograms.

Restart Paper after installing or replacing CMI/CMILib so plugin load order and
hologram hooks are established normally.

## Region and Claim Plugins

When `Prevent_Chest_Spawn_In_Protected_Places` is enabled, random candidate
locations are rejected inside supported protected areas from:

- Residence
- Factions
- FactionsX
- Towny
- GriefPrevention

These checks affect random spawning. They do not replace each protection plugin's
own player interaction rules.

WorldGuard is intentionally not queried when Lootbox selects a random location.
WorldGuard may remain installed and independently controls chest access, block
breaking, explosions, and other region behavior through its own flags. It remains
a soft dependency only so its protection listeners load before Lootbox.

## Lootin

Set `EnableLootin: true` to register compatible spawned chest, copper chest, and
barrel containers with Lootin when that plugin is enabled. Test reward semantics
carefully before enabling it on a live server because per-player looting changes
how shared physical inventory behavior is experienced.

## World Managers

World-manager plugins listed as soft dependencies are used for load ordering. A
Lootbox is loaded only when its saved world is available. Use
`Cooldown_Before_Plugin_Start` when a world manager loads worlds after plugins.

## PlaceholderAPI

Lootbox does not register or require PlaceholderAPI placeholders.
