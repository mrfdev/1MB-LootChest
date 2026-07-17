package fr.black_eyes.lootchest.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CompatibilityMigrationsTest {
    @Test
    void migratesLegacyConfigAndPreservesUnrelatedValues() throws Exception {
        YamlConfiguration config = yaml("""
                Particles:
                  respawn_ticks: 5
                RemoveChestAfterFirstOpenning: true
                Fall_Effect:
                  Enabled: true
                  Let_Block_Above_Chest_After_Fall: true
                  Optionnal_Color_If_Block_Is_Wool: BLUE
                respawn_notify:
                  bungee_broadcast: true
                  per_world_message: true
                  natural_respawn:
                    enabled: true
                    message: keep-this-message
                EnableLootin: true
                Prevent_Chest_Spawn_In_Protected_Places: true
                custom-setting: keep-me
                """);

        assertTrue(CompatibilityMigrations.migrateConfig(config));

        assertEquals(20, config.getInt("Particles.respawn_ticks"));
        assertFalse(config.isSet("RemoveChestAfterFirstOpenning"));
        assertTrue(config.getBoolean("RemoveChestAfterFirstOpening"));
        assertFalse(config.getBoolean("Fall_Effect.Enabled"));
        assertFalse(config.isSet("Fall_Effect.Let_Block_Above_Chest_After_Fall"));
        assertFalse(config.isSet("Fall_Effect.Optionnal_Color_If_Block_Is_Wool"));
        assertFalse(config.isSet("respawn_notify.bungee_broadcast"));
        assertFalse(config.isSet("EnableLootin"));
        assertFalse(config.isSet("Prevent_Chest_Spawn_In_Protected_Places"));
        assertTrue(config.getBoolean("respawn_notify.per_world_message"));
        assertTrue(config.getBoolean("respawn_notify.natural_respawn.enabled"));
        assertEquals(
                "keep-this-message",
                config.getString("respawn_notify.natural_respawn.message"));
        assertEquals("keep-me", config.getString("custom-setting"));
        assertFalse(CompatibilityMigrations.migrateConfig(config));
    }

    @Test
    void modernConfigValuesRemainUntouched() throws Exception {
        YamlConfiguration config = yaml("""
                Particles:
                  respawn_ticks: 40
                RemoveChestAfterFirstOpening: false
                Fall_Effect:
                  Enabled: false
                """);

        assertFalse(CompatibilityMigrations.migrateConfig(config));
        assertEquals(40, config.getInt("Particles.respawn_ticks"));
        assertFalse(config.getBoolean("RemoveChestAfterFirstOpening"));
    }

    @Test
    void bundledConfigKeepsLocalNotificationDefaultsWithoutProxySettings() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(stream);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));

            assertFalse(config.isSet("respawn_notify.bungee_broadcast"));
            assertFalse(config.isSet("EnableLootin"));
            assertFalse(config.isSet("Prevent_Chest_Spawn_In_Protected_Places"));
            assertTrue(config.isSet("respawn_notify.per_world_message"));
            assertTrue(config.isSet("respawn_notify.message_on_chest_take"));
            assertTrue(config.getBoolean("respawn_notify.natural_respawn.enabled"));
            assertTrue(config.getBoolean("respawn_notify.respawn_with_command.enabled"));
            assertTrue(config.getBoolean("respawn_notify.respawn_all_with_command.enabled"));
            assertTrue(config.getBoolean("respawn_notify.respawn_all_with_command_in_world.enabled"));
        } catch (Exception exception) {
            throw new AssertionError("Bundled config.yml could not be loaded", exception);
        }
    }

    @Test
    void removesRetiredLanguageEntriesWithoutChangingCurrentHelp() throws Exception {
        YamlConfiguration language = yaml("""
                Menu:
                  chances:
                    lore: "Chance: 25%"
                  main:
                    enable_fall: old
                    disable_fall: old
                enabledFallEffect: old
                disabledFallEffect: old
                help:
                  - "/lc info"
                  - "/lc togglefall test"
                  - "/lc removeAllHolo"
                  - "/lc list"
                """);

        assertTrue(CompatibilityMigrations.migrateLanguage(language));

        assertEquals("Chance: 25", language.getString("Menu.chances.lore"));
        assertEquals(List.of("/lc info", "/lc list"), language.getStringList("help"));
        assertNull(language.get("enabledFallEffect"));
        assertNull(language.get("disabledFallEffect"));
        assertNull(language.get("Menu.main.enable_fall"));
        assertNull(language.get("Menu.main.disable_fall"));
        assertFalse(CompatibilityMigrations.migrateLanguage(language));
    }

    @Test
    void disablesRemovedFallEffectForEverySavedChestAndPreservesData() throws Exception {
        YamlConfiguration data = yaml("""
                chests:
                  old:
                    fall: true
                    type: COPPER_CHEST
                    holo: "<gold>Old"
                    chance:
                      0: 42
                  missing-flag:
                    type: BARREL
                    particle: FLAME
                """);

        assertTrue(CompatibilityMigrations.migrateSavedChestData(data));

        assertFalse(data.getBoolean("chests.old.fall"));
        assertFalse(data.getBoolean("chests.missing-flag.fall"));
        assertEquals("COPPER_CHEST", data.getString("chests.old.type"));
        assertEquals("<gold>Old", data.getString("chests.old.holo"));
        assertEquals(42, data.getInt("chests.old.chance.0"));
        assertEquals("BARREL", data.getString("chests.missing-flag.type"));
        assertEquals("FLAME", data.getString("chests.missing-flag.particle"));
        assertFalse(CompatibilityMigrations.migrateSavedChestData(data));
    }

    private YamlConfiguration yaml(String source) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(source);
        return configuration;
    }
}
