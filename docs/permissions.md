# Permissions

Permissions are registered explicitly in `plugin.yml`. Administrative nodes
default to operators. Permission plugins such as LuckPerms can grant individual
commands to selected groups.

| Permission | Default | Purpose |
| --- | --- | --- |
| `lootchest.info` | Everyone | Use `/lc info`. |
| `lootchest.admin` | Operator | Full administrative access through the runtime admin override. |
| `lootchest.*` | Operator | Grants every administrative subcommand. |
| `lootchest.copy` | Operator | Copy Lootbox definitions. |
| `lootchest.create` | Operator | Create Lootboxes. |
| `lootchest.despawn` | Operator | Despawn one Lootbox. |
| `lootchest.despawnall` | Operator | Despawn all or world-specific Lootboxes. |
| `lootchest.edit` | Operator | Open the editor. |
| `lootchest.getname` | Operator | Identify a targeted Lootbox. |
| `lootchest.give` | Operator | Give rolled rewards directly to a player. |
| `lootchest.list` | Operator | List internal Lootbox names. |
| `lootchest.locate` | Operator | List eligible available Lootbox locations. |
| `lootchest.maxfilledslots` | Operator | Change the reward slot limit. |
| `lootchest.randomspawn` | Operator | Change random spawning. |
| `lootchest.reload` | Operator | Reload configuration and data. |
| `lootchest.remove` | Operator | Permanently remove a Lootbox definition. |
| `lootchest.respawn` | Operator | Respawn one Lootbox. |
| `lootchest.respawnall` | Operator | Respawn all or world-specific Lootboxes. |
| `lootchest.setholo` | Operator | Change hologram text. |
| `lootchest.setpos` | Operator | Move a Lootbox. |
| `lootchest.setprotection` | Operator | Change spawn protection. |
| `lootchest.settime` | Operator | Change respawn time. |
| `lootchest.tp` | Operator | Teleport to a Lootbox. |

Regular interaction with a spawned Lootbox does not require one of these command
permissions. Grant `lootchest.locate` separately when a player rank should be able
to discover announced locations.
