package fr.black_eyes.lootchest.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ChestLifecycleTest {
    @Test
    void openAndCloseAreIdempotentWithinOneSpawnGeneration() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);
        UUID playerId = UUID.randomUUID();

        ChestLifecycle.OpenToken token = lifecycle.open(playerId);

        assertNotNull(token);
        assertEquals(ChestLifecycle.State.OPEN, lifecycle.state());
        assertEquals(1, lifecycle.viewerCount());
        assertNull(lifecycle.open(playerId));
        assertTrue(lifecycle.close(token));
        assertEquals(ChestLifecycle.State.ACTIVE, lifecycle.state());
        assertEquals(0, lifecycle.viewerCount());
        assertFalse(lifecycle.close(token));
    }

    @Test
    void chestRemainsOpenUntilItsLastViewerCloses() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);
        ChestLifecycle.OpenToken first = lifecycle.open(UUID.randomUUID());
        ChestLifecycle.OpenToken second = lifecycle.open(UUID.randomUUID());

        assertTrue(lifecycle.close(first));
        assertEquals(ChestLifecycle.State.OPEN, lifecycle.state());
        assertEquals(1, lifecycle.viewerCount());
        assertTrue(lifecycle.close(second));
        assertEquals(ChestLifecycle.State.ACTIVE, lifecycle.state());
    }

    @Test
    void removalCanStartAndFinishOnlyOnce() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);

        ChestLifecycle.Transition removal =
                lifecycle.beginRemoval(ChestLifecycle.RemovalCause.EMPTY);

        assertNotNull(removal);
        assertEquals(ChestLifecycle.State.REMOVING, lifecycle.state());
        assertNull(lifecycle.beginRemoval(ChestLifecycle.RemovalCause.BREAK));
        assertTrue(lifecycle.completeRemoval(removal));
        assertEquals(ChestLifecycle.State.DESPAWNED, lifecycle.state());
        assertFalse(lifecycle.completeRemoval(removal));
    }

    @Test
    void removalInvalidatesEveryOpenViewer() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);
        ChestLifecycle.OpenToken first = lifecycle.open(UUID.randomUUID());
        ChestLifecycle.OpenToken second = lifecycle.open(UUID.randomUUID());

        ChestLifecycle.Transition removal =
                lifecycle.beginRemoval(ChestLifecycle.RemovalCause.BREAK);

        assertNotNull(removal);
        assertEquals(0, lifecycle.viewerCount());
        assertFalse(lifecycle.close(first));
        assertFalse(lifecycle.close(second));
    }

    @Test
    void staleDelayedRemovalCannotDeleteANewerSpawn() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);
        ChestLifecycle.Transition removal =
                lifecycle.beginRemoval(ChestLifecycle.RemovalCause.BREAK);

        ChestLifecycle.Transition spawn = lifecycle.beginSpawn();

        assertNotNull(spawn);
        assertTrue(lifecycle.completeSpawn(spawn));
        assertEquals(ChestLifecycle.State.ACTIVE, lifecycle.state());
        assertFalse(lifecycle.completeRemoval(removal));
        assertEquals(ChestLifecycle.State.ACTIVE, lifecycle.state());
    }

    @Test
    void closeFromAnOlderGenerationCannotAffectRespawnedChest() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);
        ChestLifecycle.OpenToken open = lifecycle.open(UUID.randomUUID());
        ChestLifecycle.Transition spawn = lifecycle.beginSpawn();

        assertTrue(lifecycle.completeSpawn(spawn));
        assertFalse(lifecycle.close(open));
        assertEquals(ChestLifecycle.State.ACTIVE, lifecycle.state());
        assertEquals(0, lifecycle.viewerCount());
    }

    @Test
    void failedSpawnReturnsToDespawnedAndCannotFinishTwice() {
        ChestLifecycle lifecycle = new ChestLifecycle(false);
        ChestLifecycle.Transition spawn = lifecycle.beginSpawn();

        assertTrue(lifecycle.failSpawn(spawn));
        assertEquals(ChestLifecycle.State.DESPAWNED, lifecycle.state());
        assertFalse(lifecycle.failSpawn(spawn));
    }

    @Test
    void deletedChestRejectsEveryLaterTransition() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);

        lifecycle.delete();

        assertEquals(ChestLifecycle.State.DELETED, lifecycle.state());
        assertNull(lifecycle.open(UUID.randomUUID()));
        assertNull(lifecycle.beginRemoval(ChestLifecycle.RemovalCause.DELETE));
        assertNull(lifecycle.beginSpawn());
    }

    @Test
    void reconciliationInvalidatesTokensFromThePreviousRuntimeView() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);
        ChestLifecycle.OpenToken open = lifecycle.open(UUID.randomUUID());
        long previousGeneration = lifecycle.generation();

        lifecycle.reconcile(false);

        assertEquals(ChestLifecycle.State.DESPAWNED, lifecycle.state());
        assertTrue(lifecycle.generation() > previousGeneration);
        assertFalse(lifecycle.close(open));
    }

    @Test
    void survivingPhysicalContainerCanBeReconciledAndRemovedAgain() {
        ChestLifecycle lifecycle = new ChestLifecycle(true);
        ChestLifecycle.Transition emptyRemoval =
                lifecycle.beginRemoval(ChestLifecycle.RemovalCause.EMPTY);

        assertTrue(lifecycle.completeRemoval(emptyRemoval));
        assertEquals(ChestLifecycle.State.DESPAWNED, lifecycle.state());

        lifecycle.reconcile(true);
        ChestLifecycle.Transition breakRemoval =
                lifecycle.beginRemoval(ChestLifecycle.RemovalCause.BREAK);

        assertNotNull(breakRemoval);
        assertTrue(lifecycle.completeRemoval(breakRemoval));
        assertEquals(ChestLifecycle.State.DESPAWNED, lifecycle.state());
    }

    @Test
    void emptyingPolicyMatchesConfiguredCollectionBehavior() {
        assertFalse(ChestLifecycle.shouldCollectAfterClose(false, false, false));
        assertFalse(ChestLifecycle.shouldCollectAfterClose(true, false, false));
        assertFalse(ChestLifecycle.shouldCollectAfterClose(false, true, false));
        assertTrue(ChestLifecycle.shouldCollectAfterClose(true, true, false));
        assertTrue(ChestLifecycle.shouldCollectAfterClose(false, false, true));
        assertTrue(ChestLifecycle.shouldCollectAfterClose(true, false, true));
    }

    @Test
    void breakingDropsEachRealItemOnceAndClearsTheInventory() {
        AtomicInteger clears = new AtomicInteger();
        String[] contents = {"diamond", null, "air", "emerald"};
        List<String> dropped = new ArrayList<>();

        ChestLifecycle.collectContents(
                contents,
                item -> item != null && !item.equals("air"),
                dropped::add,
                clears::incrementAndGet);

        assertEquals(List.of("diamond", "emerald"), dropped);
        assertEquals(1, clears.get());
    }

    @Test
    void despawnConfirmsBlockRemovalBeforeCleaningEffects() {
        AtomicInteger clears = new AtomicInteger();
        AtomicReference<Material> blockType = new AtomicReference<>(Material.CHEST);
        AtomicReference<Boolean> blockPhysics = new AtomicReference<>(true);
        Inventory inventory = inventory(true, clears);
        Container container = container(inventory);
        Block block = block(container, blockType, blockPhysics);
        Location particleLocation = location();
        Particle particle = Particle.FLAME;
        Map<Location, Particle> particles = new HashMap<>();
        AtomicInteger hologramRemovals = new AtomicInteger();
        particles.put(particleLocation, particle);

        boolean removed = ChestLifecycle.removePhysicalContainer(
                block,
                particleLocation,
                particles,
                hologramRemovals::incrementAndGet);

        assertTrue(removed);
        assertEquals(1, clears.get());
        assertEquals(Material.AIR, blockType.get());
        assertTrue(blockPhysics.get());
        assertTrue(particles.isEmpty());
        assertEquals(1, hologramRemovals.get());
    }

    @Test
    void despawnClearsNonEmptyInventoryBeforeRemovingContainer() {
        AtomicInteger clears = new AtomicInteger();
        AtomicReference<Material> blockType = new AtomicReference<>(Material.COPPER_CHEST);
        AtomicReference<Boolean> blockPhysics = new AtomicReference<>(true);
        Inventory inventory = inventory(false, clears);
        Block block = block(container(inventory), blockType, blockPhysics);

        boolean removed = ChestLifecycle.removePhysicalContainer(
                block,
                location(),
                new HashMap<>(),
                () -> {
                });

        assertTrue(removed);
        assertEquals(1, clears.get());
        assertEquals(Material.AIR, blockType.get());
        assertTrue(blockPhysics.get());
    }

    @Test
    void failedPhysicalRemovalKeepsEffectsForLifecycleRecovery() {
        Block block = proxy(Block.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getType" -> Material.COPPER_CHEST;
            case "setType" -> null;
            default -> defaultValue(method.getReturnType());
        });
        Location particleLocation = location();
        Map<Location, Particle> particles = new HashMap<>();
        AtomicInteger hologramRemovals = new AtomicInteger();
        particles.put(particleLocation, Particle.FLAME);

        boolean removed = ChestLifecycle.removePhysicalContainer(
                block,
                particleLocation,
                particles,
                hologramRemovals::incrementAndGet);

        assertFalse(removed);
        assertEquals(Map.of(particleLocation, Particle.FLAME), particles);
        assertEquals(0, hologramRemovals.get());
    }

    @Test
    void absentPhysicalContainerStillCleansParticleAndHologram() {
        Location particleLocation = location();
        Map<Location, Particle> particles = new HashMap<>();
        AtomicInteger hologramRemovals = new AtomicInteger();
        particles.put(particleLocation, Particle.FLAME);

        ChestLifecycle.removeEffects(
                particleLocation,
                particles,
                hologramRemovals::incrementAndGet);

        assertTrue(particles.isEmpty());
        assertEquals(1, hologramRemovals.get());
    }

    @Test
    void reloadCleansEveryHologramAndAllRuntimeIndexes() {
        Map<String, String> chests = new LinkedHashMap<>();
        Map<Location, Particle> particles = new HashMap<>();
        Map<Location, Long> protections = new HashMap<>();
        Location location = location();
        chests.put("one", "hologram-one");
        chests.put("two", "hologram-two");
        particles.put(location, Particle.FLAME);
        protections.put(location, 123L);
        List<String> removedHolograms = new ArrayList<>();

        ChestLifecycle.clearForReload(
                chests,
                removedHolograms::add,
                particles,
                protections);

        assertEquals(List.of("hologram-one", "hologram-two"), removedHolograms);
        assertTrue(chests.isEmpty());
        assertTrue(particles.isEmpty());
        assertTrue(protections.isEmpty());
    }

    private Inventory inventory(boolean empty, AtomicInteger clears) {
        return proxy(Inventory.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "isEmpty" -> empty;
            case "clear" -> {
                clears.incrementAndGet();
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    private Container container(Inventory inventory) {
        return proxy(Container.class, (ignored, method, arguments) -> {
            if (method.getName().equals("getInventory")) {
                return inventory;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private Block block(
            Container state,
            AtomicReference<Material> type,
            AtomicReference<Boolean> physics) {
        return proxy(Block.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getState" -> state;
            case "getType" -> type.get();
            case "setType" -> {
                type.set((Material) arguments[0]);
                if (arguments.length > 1) {
                    physics.set((Boolean) arguments[1]);
                }
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    private Location location() {
        return new Location(null, 12, 64, -8);
    }

    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler));
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }
}
