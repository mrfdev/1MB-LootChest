package fr.black_eyes.lootchest.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    void despawnClearsInventoryBlockParticleAndHologram() {
        AtomicInteger clears = new AtomicInteger();
        AtomicReference<Material> blockType = new AtomicReference<>(Material.CHEST);
        Inventory inventory = inventory(new ItemStack[0], clears);
        Container container = container(inventory);
        Block block = block(container, blockType);
        Location particleLocation = location();
        Particle particle = Particle.FLAME;
        Map<Location, Particle> particles = new HashMap<>();
        AtomicInteger hologramRemovals = new AtomicInteger();
        particles.put(particleLocation, particle);

        ChestLifecycle.removePhysicalContainer(
                block,
                particleLocation,
                particles,
                hologramRemovals::incrementAndGet);

        assertEquals(1, clears.get());
        assertEquals(Material.AIR, blockType.get());
        assertTrue(particles.isEmpty());
        assertEquals(1, hologramRemovals.get());
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

    private Inventory inventory(ItemStack[] contents, AtomicInteger clears) {
        return proxy(Inventory.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getContents" -> contents;
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

    private Block block(Container state, AtomicReference<Material> type) {
        return proxy(Block.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getState" -> state;
            case "setType" -> {
                type.set((Material) arguments[0]);
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
