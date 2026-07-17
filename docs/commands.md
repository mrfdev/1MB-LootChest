# Commands

The registered command is `/lootchest`; `/lc` is its short alias. Subcommands use
the matching `lootchest.<subcommand>` permission. `/lc info` is public; all other
permissions default to server operators unless explicitly granted.

| Command | Context | Description |
| --- | --- | --- |
| `/lc` or `/lc help` | Console/player | Show the configured help list. |
| `/lc info` | Console/player | Show the 1MoreBlock Lootbox introduction, version, and canonical docs link. |
| `/lc create <name>` | Player | Register the filled supported container being targeted and open its editor. |
| `/lc edit <name>` | Player | Open the graphical editor. |
| `/lc copy <source> <destination>` | Console/player | Copy settings and rewards into an existing Lootbox. |
| `/lc give <name> <player>` | Console/player | Roll that Lootbox's configured contents into an online player's inventory. |
| `/lc getname` | Player | Identify the targeted Lootbox. |
| `/lc list` | Console/player | List loaded internal Lootbox names. In-game names are clickable and open their editor. |
| `/lc locate` | Console/player | List available boxes with natural location announcements enabled. |
| `/lc respawn <name>` | Console/player | Force one Lootbox to respawn. |
| `/lc respawnall [world]` | Console/player | Force every Lootbox, optionally in one world, to respawn. |
| `/lc despawn <name>` | Console/player | Hide one physical container while retaining its saved definition. |
| `/lc despawnall [world]` | Console/player | Hide all physical containers, optionally in one world. |
| `/lc remove <name>` | Console/player | Permanently delete the saved Lootbox definition and its display. |
| `/lc reload` | Console/player | Reload configuration, locale, and chest data, then respawn loaded boxes. |
| `/lc randomspawn <name> <radius>` | Console/player | Set the random spawn radius; use `0` to disable random positioning. |
| `/lc maxfilledslots <name> <number>` | Console/player | Limit how many reward slots can be populated; use `0` for no limit. |
| `/lc setholo <name> <text>` | Console/player | Set MiniMessage-capable hologram text. `none` disables that hologram. |
| `/lc setpos <name>` | Player | Move the Lootbox origin to the player's current block and facing. |
| `/lc setprotection <name> <seconds>` | Console/player | Set post-spawn interaction protection. |
| `/lc settime <name> <minutes>` | Console/player | Set the respawn interval in minutes and force a respawn. |
| `/lc tp <name>` | Player | Teleport to the Lootbox's current location. |

## Editor Workflow

`/lc create` and `/lc edit` expose menus for contents, item chances, respawn time,
container type, particle, copying, and per-box message toggles. Text supplied
to `/lc setholo` accepts MiniMessage colors such as `<gold>` and hex colors such as
`<#f9e2af>`.

Command names and internal chest names are tab-completed when the sender has the
corresponding permission.
