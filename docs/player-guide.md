# Lootbox Player Guide

## Introduction

Lootboxes are special containers placed around 1MoreBlock. Each one can contain a
different selection of rewards and can return after it has been collected.
Particles, announcements, and a floating name can help you spot an available box.

## How Players Use It

1. Find an available Lootbox in the world or follow a server announcement.
2. Open it and collect the contents, or break it when that location allows it.
3. The container may disappear after it is emptied or closed.
4. Wait for its configured respawn before collecting from that location again.

Rewards are randomized independently when staff configure item chances. A reward
shown on one visit is not guaranteed on the next visit.

## Quick Start

- Run `/lc info` for a short introduction, the installed version, useful commands,
  and a clickable link to this guide.
- Run `/lc locate` if your rank has been granted `lootchest.locate`. It lists
  currently available Lootboxes whose location announcement is enabled.
- Watch server announcements for newly respawned Lootboxes.

## Available Features

- Repeatable reward containers with staff-configured respawn times.
- Different container styles, including chests, barrels, shulker boxes, and copper chests.
- Randomized reward chances and reward limits for individual Lootboxes.
- Visual particles, names, and timers.
- Server-wide or current-world announcements depending on server configuration.

## Commands

| Command | What it does |
| --- | --- |
| `/lc info` | Shows Lootbox information, version, starting commands, and documentation. |
| `/lc locate` | Lists eligible available Lootboxes when your rank grants access. |
| `/lc help` | Shows commands you are permitted to use. Most entries are staff tools. |

All creation, editing, teleportation, forced respawn, and removal commands are
administrative. See the
[technical command reference](https://github.com/mrfdev/1MB-LootChest/blob/master/docs/commands.md)
for those tools.

## Rank Requirements

`/lc info` is available to everyone. `/lc locate` requires the
`lootchest.locate` permission and may be limited to selected ranks. Finding and
opening an available Lootbox does not itself require a Lootbox command permission.

## Rewards, Costs, Limits, and Cooldowns

- Lootbox rewards are repeatable after that box respawns; they are not a one-time account reward.
- Contents, item chances, and maximum rewards are configured separately for each Lootbox.
- Respawn time is configured by staff and can differ between locations.
- A newly spawned Lootbox can have a short protection period.
- Some Lootboxes require nearby hostile mobs to be cleared before they can be opened.
- Lootbox itself does not charge money, experience, or items to open a container.
- Automation and nearby-container placement can be restricted to protect the reward system.

## Important Notes

- A full inventory can cause directly granted rewards to drop nearby.
- Emptying or closing a Lootbox may remove its physical container until the next respawn.
- A missing container is not necessarily broken; it may be waiting for its timer.
- Particles and holograms are visual aids. The physical container and server messages
  remain authoritative.

## Placeholders

Lootbox does not currently provide public PlaceholderAPI placeholders.

## Related Features

CMI supplies the floating hologram display used by this 1MoreBlock edition. Server
regions and world rules can also control where random Lootboxes are allowed to appear.

## Technical Documentation

Administrators and developers can use the
[Lootbox technical overview](https://github.com/mrfdev/1MB-LootChest/blob/master/README.md).
The canonical public page is
[docs.1moreblock.com/custom-server-plugins/lootbox/](https://docs.1moreblock.com/custom-server-plugins/lootbox/).
