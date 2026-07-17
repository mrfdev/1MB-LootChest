# Integrations

All integrations are optional unless the corresponding feature is required.

## CMI and CMILib

This 1MoreBlock edition creates transient CMI holograms. They are not written into
CMI's permanent hologram files. When CMI is missing, disabled, or its hologram
manager fails, Lootbox disables holograms safely while keeping containers active.

Restart Paper after installing or replacing CMI/CMILib so plugin load order and
hologram hooks are established normally.

## Region and Claim Plugins

When `Prevent_Chest_Spawn_In_Protected_Places` is enabled, random candidate
locations are rejected inside supported protected areas from:

- WorldGuard
- Residence
- Factions
- FactionsX
- Towny
- GriefPrevention

These checks affect random spawning. They do not replace each protection plugin's
own player interaction rules.

## Lootin

Set `EnableLootin: true` to register compatible spawned chest, copper chest, and
barrel containers with Lootin when that plugin is enabled. Test reward semantics
carefully before enabling it on a live server because per-player looting changes
how shared physical inventory behavior is experienced.

## Proxy Messaging

`respawn_notify.bungee_broadcast` sends configured announcements through the
`BungeeCord` plugin messaging channel. Proxy support must be enabled in the server
configuration, and at least one player must be online for plugin messages to be
carried. Without proxy broadcasting, messages can be global or limited to the
Lootbox's world.

## World Managers

World-manager plugins listed as soft dependencies are used for load ordering. A
Lootbox is loaded only when its saved world is available. Use
`Cooldown_Before_Plugin_Start` when a world manager loads worlds after plugins.

## PlaceholderAPI

Lootbox does not register or require PlaceholderAPI placeholders.
