package fr.black_eyes.lootchest.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {
    @Test
    void declaresOnlyMaintainedRuntimeIntegrations() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(stream);
            YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));

            assertEquals(
                    List.of("CMI", "CMILib", "Multiverse-Core", "Bolt"),
                    descriptor.getStringList("softdepend"));
        } catch (Exception exception) {
            throw new AssertionError("Bundled plugin.yml could not be loaded", exception);
        }
    }
}
