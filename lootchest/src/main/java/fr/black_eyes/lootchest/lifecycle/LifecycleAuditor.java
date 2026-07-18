package fr.black_eyes.lootchest.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Mat;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.ChestSnapshot;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.ContainerState;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.Report;

/** Captures live Bukkit state for the pure, read-only lifecycle audit. */
public final class LifecycleAuditor {
    private LifecycleAuditor() {
    }

    public static Report inspect(Main plugin) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("LootChest lifecycle audits must run on the server thread.");
        }

        List<ChestSnapshot> snapshots = new ArrayList<>();
        for (Lootchest chest : plugin.getLootChest().values()) {
            snapshots.add(snapshot(plugin, chest));
        }
        return LifecycleAudit.inspect(snapshots, plugin.getLootChestLocationIndex().size());
    }

    private static ChestSnapshot snapshot(Main plugin, Lootchest chest) {
        Location location = chest.getActualLocation();
        World world = location == null ? null : location.getWorld();
        String expectedType = chest.getType() == null ? "none" : chest.getType().name();
        String actualType = "not inspected";
        ContainerState containerState = ContainerState.UNAVAILABLE;
        String locationKey = null;
        String locationDisplay = chest.getWorld() + " (location unavailable)";
        boolean indexMatches = false;

        if (location != null && world != null) {
            UUID worldId = world.getUID();
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            locationKey = worldId + ":" + x + ":" + y + ":" + z;
            locationDisplay = world.getName() + " " + x + ", " + y + ", " + z;
            indexMatches = plugin.getLootChestLocationIndex().get(worldId, x, y, z) == chest;

            if (world.isChunkLoaded(x >> 4, z >> 4)) {
                Material actualMaterial = world.getBlockAt(x, y, z).getType();
                actualType = actualMaterial.name();
                if (Mat.matchesContainerType(chest.getType(), actualMaterial)) {
                    containerState = ContainerState.PRESENT;
                } else if (actualMaterial.isAir()) {
                    containerState = ContainerState.ABSENT;
                } else {
                    containerState = ContainerState.WRONG_TYPE;
                }
            }
        }

        boolean particleExpected = Main.configs != null
                && Main.configs.partEnable
                && chest.getParticle() != null;
        boolean particleActive = location != null
                && plugin.getPart().containsKey(chest.getParticleLocation());
        boolean respawnTaskExpected = chest.getTime() >= 0;

        return new ChestSnapshot(
                chest.getName(),
                locationKey,
                locationDisplay,
                containerState,
                expectedType,
                actualType,
                indexMatches,
                chest.getHologram().isExpectedActive(),
                chest.getHologram().isActive(),
                particleExpected,
                particleActive,
                respawnTaskExpected,
                LootChestUtils.hasRespawnTask(chest));
    }
}
