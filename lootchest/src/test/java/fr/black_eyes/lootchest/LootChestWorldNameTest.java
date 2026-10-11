package fr.black_eyes.lootchest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;

import org.bukkit.World;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LootChestWorldNameTest {
    @ParameterizedTest
    @ValueSource(strings = {"spawn", "custom world", "spawn_nether"})
    void newlyCreatedChestUsesTheServerWorldNameInsteadOfItsDebugKey(String name) {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[] {World.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    // Paper 26.3's debug representation uses the dimension key,
                    // which can differ from the server's saved world name.
                    case "toString" -> "CraftWorld{key=minecraft:overworld}";
                    default -> throw new AssertionError("Unexpected world access: " + method.getName());
                });
        assertEquals(name, LootChestUtils.getWorldName(world));
    }
}
