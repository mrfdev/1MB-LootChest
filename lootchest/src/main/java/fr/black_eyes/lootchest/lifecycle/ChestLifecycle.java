package fr.black_eyes.lootchest.lifecycle;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Shared, testable lifecycle operations used by collection, despawn, and reload. */
public final class ChestLifecycle {
    private ChestLifecycle() {
    }

    public static boolean shouldCollectAfterClose(
            boolean inventoryEmpty,
            boolean removeEmptyChests,
            boolean removeAfterFirstOpening) {
        return removeAfterFirstOpening || (removeEmptyChests && inventoryEmpty);
    }

    public static void collectBreakContents(
            Inventory inventory,
            Consumer<ItemStack> dropItem) {
        collectContents(
                inventory.getContents(),
                item -> item != null && item.getType() != Material.AIR,
                dropItem,
                inventory::clear);
    }

    static <T> void collectContents(
            T[] contents,
            Predicate<T> shouldDrop,
            Consumer<T> dropItem,
            Runnable clearInventory) {
        for (T item : contents) {
            if (shouldDrop.test(item)) {
                dropItem.accept(item);
            }
        }
        clearInventory.run();
    }

    public static void removePhysicalContainer(
            Block block,
            Location particleLocation,
            Map<Location, Particle> activeParticles,
            Runnable hologramCleanup) {
        BlockState state = block.getState();
        if (state instanceof InventoryHolder inventoryHolder) {
            inventoryHolder.getInventory().clear();
        }
        block.setType(Material.AIR);
        removeEffects(particleLocation, activeParticles, hologramCleanup);
    }

    public static void removeEffects(
            Location particleLocation,
            Map<Location, Particle> activeParticles,
            Runnable hologramCleanup) {
        activeParticles.remove(particleLocation);
        hologramCleanup.run();
    }

    public static <T> void clearForReload(
            Map<String, T> chests,
            Consumer<T> hologramCleanup,
            Map<?, ?> activeParticles,
            Map<?, ?> activeProtections) {
        new ArrayList<>(chests.values()).forEach(hologramCleanup);
        chests.clear();
        activeParticles.clear();
        activeProtections.clear();
    }
}
