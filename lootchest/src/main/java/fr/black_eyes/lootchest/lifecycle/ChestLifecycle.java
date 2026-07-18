package fr.black_eyes.lootchest.lifecycle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

/**
 * Server-thread-owned state machine plus shared physical-container operations.
 *
 * <p>Generation tokens make delayed work harmless after a newer spawn has
 * superseded it.
 */
public final class ChestLifecycle {
    public enum State {
        DESPAWNED,
        SPAWNING,
        ACTIVE,
        OPEN,
        REMOVING,
        DELETED
    }

    public enum RemovalCause {
        EMPTY,
        FIRST_OPEN,
        BREAK,
        EXPLOSION,
        COMMAND,
        DELETE
    }

    public record OpenToken(long generation, UUID playerId) {
    }

    public record Transition(long generation, State state, RemovalCause cause) {
    }

    private final Set<UUID> viewers = new HashSet<>();
    private long generation;
    private State state;
    private RemovalCause removalCause;

    public ChestLifecycle(boolean physicalContainerPresent) {
        state = physicalContainerPresent ? State.ACTIVE : State.DESPAWNED;
        generation = physicalContainerPresent ? 1L : 0L;
    }

    public State state() {
        return state;
    }

    public long generation() {
        return generation;
    }

    public int viewerCount() {
        return viewers.size();
    }

    public boolean isActive() {
        return state == State.ACTIVE || state == State.OPEN;
    }

    public boolean canOpen() {
        return isActive();
    }

    public OpenToken open(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!canOpen() || !viewers.add(playerId)) {
            return null;
        }
        state = State.OPEN;
        return new OpenToken(generation, playerId);
    }

    public boolean close(OpenToken token) {
        if (token == null
                || token.generation() != generation
                || !viewers.remove(token.playerId())) {
            return false;
        }
        if (state == State.OPEN && viewers.isEmpty()) {
            state = State.ACTIVE;
        }
        return true;
    }

    public Transition beginRemoval(RemovalCause cause) {
        Objects.requireNonNull(cause, "cause");
        if (!isActive()) {
            return null;
        }
        viewers.clear();
        state = State.REMOVING;
        removalCause = cause;
        return new Transition(generation, State.REMOVING, cause);
    }

    public boolean completeRemoval(Transition transition) {
        if (!isCurrent(transition)
                || transition.state() != State.REMOVING
                || transition.cause() != removalCause) {
            return false;
        }
        state = State.DESPAWNED;
        removalCause = null;
        return true;
    }

    public Transition beginSpawn() {
        if (state == State.SPAWNING || state == State.DELETED) {
            return null;
        }
        generation++;
        viewers.clear();
        removalCause = null;
        state = State.SPAWNING;
        return new Transition(generation, State.SPAWNING, null);
    }

    public boolean completeSpawn(Transition transition) {
        if (!isCurrent(transition) || transition.state() != State.SPAWNING) {
            return false;
        }
        state = State.ACTIVE;
        return true;
    }

    public boolean failSpawn(Transition transition) {
        if (!isCurrent(transition) || transition.state() != State.SPAWNING) {
            return false;
        }
        state = State.DESPAWNED;
        return true;
    }

    public boolean isCurrent(Transition transition) {
        return transition != null
                && transition.generation() == generation
                && transition.state() == state;
    }

    public boolean isCurrentGeneration(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    public void reconcile(boolean physicalContainerPresent) {
        generation++;
        viewers.clear();
        removalCause = null;
        state = physicalContainerPresent ? State.ACTIVE : State.DESPAWNED;
    }

    public void delete() {
        generation++;
        viewers.clear();
        removalCause = null;
        state = State.DELETED;
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
        block.setType(Material.AIR, false);
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
