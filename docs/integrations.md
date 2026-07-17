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

## Bolt

Bolt `1.2.20` is the supported 1MoreBlock block-protection integration. Bolt is a
Paper soft dependency and remains a separate server plugin; its classes are not
compiled into or bundled with Lootbox.

Lootbox handles protected opens and breaks through Paper's event contract. Its
container lifecycle handlers run after normal protection checks and ignore events
that Bolt has cancelled. An unauthorized Bolt action therefore cannot mark a
Lootbox opened, collect its rewards, remove it, or start its respawn lifecycle.

Revalidate a locked Lootbox with an unauthorized account whenever Bolt or Paper is
updated.

## Other Protection Plugins

This edition has no direct WorldGuard, Residence, Factions, FactionsX, Towny,
GriefPrevention, or Lootin integration. Random spawn locations are controlled by
the normal Lootbox world, radius, height, terrain, water, and world-border rules.

WorldGuard may remain installed for the server's regions, but Lootbox neither
queries its API nor declares it as a dependency. The 1MoreBlock deployment places
randomized Lootboxes only in staff-selected WorldGuard regions.

PlotSquared may remain installed. Lootbox has no PlotSquared API integration, and
the 1MoreBlock deployment does not place Lootboxes in plot worlds.

## World Managers

Multiverse-Core is a soft dependency for world load ordering. A Lootbox is loaded
only when its saved world is available. Use `Cooldown_Before_Plugin_Start` when a
world manager loads worlds after plugins.

## Vault

Vault may remain installed for the wider 1MoreBlock plugin stack. Lootbox does not
currently call Vault economy, chat, or permissions APIs, so it has no direct Maven
or Paper dependency on Vault.

## PlaceholderAPI

Lootbox does not register or require PlaceholderAPI placeholders.
