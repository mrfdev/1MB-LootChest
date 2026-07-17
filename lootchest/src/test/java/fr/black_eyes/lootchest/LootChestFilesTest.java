package fr.black_eyes.lootchest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LootChestFilesTest {
    private static final Map<String, String> DEFAULT_RESOURCES = Map.of(
            "config.yml", "# bundled config\nsetting: default\n",
            "lang.yml", "# bundled language\nmessage: default\n",
            "data.yml", "chests: {}\n");

    @TempDir
    Path dataFolder;

    @Test
    void copiesAndLoadsMissingBundledFiles() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();

            assertEquals("default", files.getConfig().getString("setting"));
            assertEquals("default", files.getLang().getString("message"));
            assertTrue(files.getData().isConfigurationSection("chests"));
            assertTrue(java.nio.file.Files.isRegularFile(dataFolder.resolve("config.yml")));
            assertTrue(java.nio.file.Files.isRegularFile(dataFolder.resolve("lang.yml")));
            assertTrue(java.nio.file.Files.isRegularFile(dataFolder.resolve("data.yml")));
        }
    }

    @Test
    void preservesExistingValuesAndCommentsWhileAddingDefaults() throws Exception {
        java.nio.file.Files.createDirectories(dataFolder);
        java.nio.file.Files.writeString(
                dataFolder.resolve("config.yml"),
                "# keep this local comment\nsetting: local\n",
                StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(
                dataFolder.resolve("lang.yml"),
                "message: local\n",
                StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(
                dataFolder.resolve("data.yml"),
                "chests: {}\n",
                StandardCharsets.UTF_8);

        try (LootChestFiles files = newFiles()) {
            files.initialize();
            files.setConfig("setting", "must-not-overwrite");
            files.setConfig("new-setting", 42);
            files.setLang("new-message", "<gold>Hello");
            files.saveConfig();
            files.saveLang();
            files.flush();
            files.reloadConfig();

            assertEquals("local", files.getConfig().getString("setting"));
            assertEquals(42, files.getConfig().getInt("new-setting"));
            assertEquals("<gold>Hello", files.getLang().getString("new-message"));
            assertTrue(java.nio.file.Files.readString(dataFolder.resolve("config.yml"))
                    .contains("# keep this local comment"));
        }
    }

    @Test
    void reloadsConfigLanguageAndDataFromDisk() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();

            java.nio.file.Files.writeString(
                    dataFolder.resolve("config.yml"),
                    "setting: changed\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("lang.yml"),
                    "message: changed\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("data.yml"),
                    "chests:\n  reloaded:\n    type: BARREL\n",
                    StandardCharsets.UTF_8);

            files.reloadConfig();
            files.reloadData();

            assertEquals("changed", files.getConfig().getString("setting"));
            assertEquals("changed", files.getLang().getString("message"));
            assertEquals("BARREL", files.getData().getString("chests.reloaded.type"));
        }
    }

    @Test
    void recoversInvalidDataFromNewestValidNumberedBackup() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            files.getData().set("chests.recovery.type", "COPPER_CHEST");
            files.saveData();
            files.backupData();
        }

        java.nio.file.Files.writeString(
                dataFolder.resolve("data.yml"),
                "chests: [broken",
                StandardCharsets.UTF_8);

        try (LootChestFiles files = newFiles()) {
            files.initialize();

            assertEquals(
                    "COPPER_CHEST",
                    files.getData().getString("chests.recovery.type"));
            try (Stream<Path> entries = java.nio.file.Files.list(dataFolder)) {
                assertTrue(entries.anyMatch(path ->
                        path.getFileName().toString().startsWith("data.yml.invalid-")));
            }
        }
    }

    @Test
    void retainsOnlyTenNumberedBackups() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            for (int index = 0; index < 12; index++) {
                files.getData().set("chests.backup.value", index);
                files.saveData();
                files.backupData();
            }
        }

        Path backups = dataFolder.resolve("backups");
        try (Stream<Path> entries = java.nio.file.Files.list(backups)) {
            assertEquals(10, entries.count());
        }
        assertFalse(java.nio.file.Files.exists(backups.resolve("0data.yml")));
        assertFalse(java.nio.file.Files.exists(backups.resolve("1data.yml")));
        assertTrue(java.nio.file.Files.exists(backups.resolve("11data.yml")));
    }

    @Test
    void rejectsInvalidConfigWithoutOverwritingIt() throws Exception {
        String invalidConfig = "setting: [broken";
        java.nio.file.Files.createDirectories(dataFolder);
        java.nio.file.Files.writeString(
                dataFolder.resolve("config.yml"),
                invalidConfig,
                StandardCharsets.UTF_8);

        try (LootChestFiles files = newFiles()) {
            assertThrows(InvalidConfigurationException.class, files::initialize);
        }

        assertEquals(
                invalidConfig,
                java.nio.file.Files.readString(dataFolder.resolve("config.yml")));
    }

    private LootChestFiles newFiles() {
        Logger logger = Logger.getLogger("LootChestFilesTest-" + dataFolder.getFileName());
        logger.setUseParentHandlers(false);
        Function<String, InputStream> resources = name -> {
            String contents = DEFAULT_RESOURCES.get(name);
            return contents == null
                    ? null
                    : new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        };
        return new LootChestFiles(dataFolder, resources, logger);
    }
}
