package fr.black_eyes.lootchest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
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
    void refreshesLoadableNamesWhenRuntimeDataIsSaved() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            files.getData().set("chests.created.type", "CHEST");
            files.getData().set("chests.created.position.world", "world");
            files.getData().set("chests.created.position.x", 1);
            files.getData().set("chests.created.position.y", 64);
            files.getData().set("chests.created.position.z", 2);

            files.saveData();

            assertTrue(files.getLoadableChestNames().contains("created"));
            files.getData().set("chests.created", null);
            files.saveData();
            assertFalse(files.getLoadableChestNames().contains("created"));
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
    void reportsMalformedChestWithoutReplacingOrDeletingSavedData() throws Exception {
        String malformedData = """
                chests:
                  valid:
                    type: BARREL
                    position:
                      world: world
                      x: 10.0
                      y: 64.0
                      z: -5.0
                  broken:
                    type: CHEST
                    position:
                      world: world
                      x: 1.0
                      y: 65.0
                      z: 2.0
                    inventory:
                      bad-slot: STONE
                """;
        java.nio.file.Files.createDirectories(dataFolder);
        java.nio.file.Files.writeString(
                dataFolder.resolve("data.yml"),
                malformedData,
                StandardCharsets.UTF_8);

        CapturingHandler logs = new CapturingHandler();
        Logger logger = Logger.getLogger("LootChestFilesTest-malformed-" + dataFolder.getFileName());
        logger.setUseParentHandlers(false);
        logger.addHandler(logs);

        try (LootChestFiles files = newFiles(logger)) {
            files.initialize();

            assertTrue(files.getData().isConfigurationSection("chests.valid"));
            assertEquals("STONE", files.getData().getString("chests.broken.inventory.bad-slot"));
            assertTrue(files.getLoadableChestNames().contains("valid"));
            assertFalse(files.getLoadableChestNames().contains("broken"));
            assertTrue(files.getRejectedChestDefinitions().containsKey("broken"));
            assertTrue(logs.contains("broken"));
            assertTrue(logs.contains("inventory.bad-slot"));
            assertTrue(logs.contains("0..26"));
        }

        assertEquals(malformedData, java.nio.file.Files.readString(dataFolder.resolve("data.yml")));
        try (Stream<Path> entries = java.nio.file.Files.list(dataFolder)) {
            assertFalse(entries.anyMatch(path ->
                    path.getFileName().toString().startsWith("data.yml.invalid-")));
        }
    }

    @Test
    void refreshesRejectedDefinitionsAfterDiskReload() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            java.nio.file.Files.writeString(
                    dataFolder.resolve("data.yml"),
                    """
                            chests:
                              repaired:
                                position:
                                  world: world
                                  x: broken
                                  y: 64
                                  z: 2
                            """,
                    StandardCharsets.UTF_8);
            files.reloadData();
            assertTrue(files.getRejectedChestDefinitions().containsKey("repaired"));

            java.nio.file.Files.writeString(
                    dataFolder.resolve("data.yml"),
                    """
                            chests:
                              repaired:
                                position:
                                  world: world
                                  x: 1
                                  y: 64
                                  z: 2
                            """,
                    StandardCharsets.UTF_8);
            files.reloadData();

            assertTrue(files.getRejectedChestDefinitions().isEmpty());
            assertTrue(files.getLoadableChestNames().contains("repaired"));
        }
    }

    @Test
    void prepareReloadDoesNotPublishAnyCandidateWhenLanguageParsingFails() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            java.nio.file.Files.writeString(
                    dataFolder.resolve("config.yml"),
                    "setting: candidate\nSaveDataFileDuringReload: false\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("lang.yml"),
                    "message: [broken",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("data.yml"),
                    validSavedDefinition("candidate"),
                    StandardCharsets.UTF_8);
            AtomicBoolean runtimeSaveCalled = new AtomicBoolean();
            LootChestFiles.ReloadRawBundle raw = files.beginReloadAsync().join();

            assertThrows(
                    IllegalStateException.class,
                    () -> files.prepareReload(raw, () -> runtimeSaveCalled.set(true)));

            assertEquals("default", files.getConfig().getString("setting"));
            assertEquals("default", files.getLang().getString("message"));
            assertTrue(files.getSavedChestNames().isEmpty());
            assertTrue(files.getLoadableChestNames().isEmpty());
            assertFalse(runtimeSaveCalled.get());
            files.cancelReloadGate();
            assertFalse(files.isReloadInProgress());
            files.getData().set("chests.after-failure.type", "CHEST");
            files.getData().set("chests.after-failure.position.world", "world");
            files.getData().set("chests.after-failure.position.x", 1);
            files.getData().set("chests.after-failure.position.y", 64);
            files.getData().set("chests.after-failure.position.z", 2);
            files.saveData();
            files.flush();
            assertTrue(java.nio.file.Files.readString(dataFolder.resolve("data.yml"))
                    .contains("after-failure:"));
        }
    }

    @Test
    void candidateFalseLoadsDiskDataOnlyWhenCommitted() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            java.nio.file.Files.writeString(
                    dataFolder.resolve("config.yml"),
                    "setting: candidate\nSaveDataFileDuringReload: false\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("lang.yml"),
                    "message: candidate\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("data.yml"),
                    validSavedDefinition("candidate"),
                    StandardCharsets.UTF_8);
            AtomicBoolean runtimeSaveCalled = new AtomicBoolean();

            String candidateText = java.nio.file.Files.readString(dataFolder.resolve("data.yml"));
            LootChestFiles.ReloadRawBundle raw = files.beginReloadAsync().join();
            LootChestFiles.PreparedReload prepared = files.prepareReload(
                    raw,
                    () -> runtimeSaveCalled.set(true));

            assertEquals("default", files.getConfig().getString("setting"));
            assertTrue(files.getSavedChestNames().isEmpty());
            files.commitReloadAsync(prepared).join();
            assertEquals("default", files.getConfig().getString("setting"));
            assertTrue(files.getSavedChestNames().isEmpty());
            assertEquals(candidateText, java.nio.file.Files.readString(dataFolder.resolve("data.yml")));
            files.publishReload(prepared);
            assertEquals("candidate", files.getConfig().getString("setting"));
            assertEquals("candidate", files.getLang().getString("message"));
            assertTrue(files.getLoadableChestNames().contains("candidate"));
            assertFalse(runtimeSaveCalled.get());
            assertThrows(IllegalStateException.class, () -> files.abortReloadAsync(prepared));
        }
    }

    @Test
    void candidateTrueImmediatelyRetainsRuntimeData() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            files.getData().set("chests.runtime.position.world", "world");
            files.getData().set("chests.runtime.position.x", 1);
            files.getData().set("chests.runtime.position.y", 64);
            files.getData().set("chests.runtime.position.z", 2);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("config.yml"),
                    "setting: candidate\nSaveDataFileDuringReload: true\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("lang.yml"),
                    "message: candidate\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("data.yml"),
                    validSavedDefinition("disk-edit"),
                    StandardCharsets.UTF_8);
            AtomicBoolean runtimeSaveCalled = new AtomicBoolean();

            LootChestFiles.ReloadRawBundle raw = files.beginReloadAsync().join();
            LootChestFiles.PreparedReload prepared = files.prepareReload(raw, () -> {
                runtimeSaveCalled.set(true);
            });
            files.getData().set("chests.late-mutation.type", "CHEST");
            files.commitReloadAsync(prepared).join();
            assertEquals("default", files.getConfig().getString("setting"));
            files.publishReload(prepared);

            assertTrue(runtimeSaveCalled.get());
            assertTrue(files.getLoadableChestNames().contains("runtime"));
            assertFalse(files.getSavedChestNames().contains("disk-edit"));
            assertFalse(files.getSavedChestNames().contains("late-mutation"));
            assertTrue(java.nio.file.Files.readString(dataFolder.resolve("data.yml"))
                    .contains("runtime:"));
            assertFalse(java.nio.file.Files.readString(dataFolder.resolve("data.yml"))
                    .contains("late-mutation:"));
        }
    }

    @Test
    void abortPreservesThePreparedDiskCandidateWithoutPublishingIt() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            java.nio.file.Files.writeString(
                    dataFolder.resolve("config.yml"),
                    "setting: candidate\nSaveDataFileDuringReload: false\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("lang.yml"),
                    "message: candidate\n",
                    StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(
                    dataFolder.resolve("data.yml"),
                    validSavedDefinition("candidate"),
                    StandardCharsets.UTF_8);
            LootChestFiles.ReloadRawBundle raw = files.beginReloadAsync().join();
            LootChestFiles.PreparedReload prepared = files.prepareReload(raw, () -> {
                throw new AssertionError("runtime data must not be saved");
            });
            java.nio.file.Files.writeString(
                    dataFolder.resolve("data.yml"),
                    validSavedDefinition("later-overwrite"),
                    StandardCharsets.UTF_8);

            Path preserved = files.abortReloadAsync(prepared).join();
            files.finishAbortReload(prepared);

            assertEquals("default", files.getConfig().getString("setting"));
            assertTrue(files.getSavedChestNames().isEmpty());
            String recovery = java.nio.file.Files.readString(preserved);
            assertTrue(recovery.contains("candidate:"));
            assertFalse(recovery.contains("later-overwrite:"));
            assertThrows(IllegalStateException.class, () -> files.commitReloadAsync(prepared));
            assertThrows(IllegalStateException.class, () -> files.abortReloadAsync(prepared));
        }
    }

    @Test
    void reloadKeepsValidAndMalformedSiblingsByteIdenticalWithoutWholeFileRecovery() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            java.nio.file.Files.writeString(
                    dataFolder.resolve("config.yml"),
                    "SaveDataFileDuringReload: false\n",
                    StandardCharsets.UTF_8);
            String candidate = """
                    # keep operator formatting
                    chests:
                      valid:
                        type: BARREL
                        position: {world: world, x: 1, y: 64, z: 2}
                      malformed:
                        type: CHEST
                        position: {world: world, x: nope, y: 64, z: 2}
                    """;
            java.nio.file.Files.writeString(dataFolder.resolve("data.yml"), candidate, StandardCharsets.UTF_8);

            LootChestFiles.ReloadRawBundle raw = files.beginReloadAsync().join();
            LootChestFiles.PreparedReload prepared = files.prepareReload(raw, () -> {
                throw new AssertionError("runtime data must not be saved");
            });
            files.commitReloadAsync(prepared).join();
            files.publishReload(prepared);

            assertTrue(files.getLoadableChestNames().contains("valid"));
            assertFalse(files.getLoadableChestNames().contains("malformed"));
            assertTrue(files.getRejectedChestDefinitions().containsKey("malformed"));
            assertEquals(candidate, java.nio.file.Files.readString(dataFolder.resolve("data.yml")));
            try (Stream<Path> entries = java.nio.file.Files.list(dataFolder)) {
                assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith("data.yml.invalid-")));
            }
        }
    }

    @Test
    void commitRejectsAnExternalDataEditAndAbortPreservesTheOriginalCandidate() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            java.nio.file.Files.writeString(
                    dataFolder.resolve("config.yml"),
                    "SaveDataFileDuringReload: false\n",
                    StandardCharsets.UTF_8);
            String original = "# original candidate\n" + validSavedDefinition("candidate");
            String externalEdit = "# later operator edit\n" + validSavedDefinition("later");
            java.nio.file.Files.writeString(dataFolder.resolve("data.yml"), original, StandardCharsets.UTF_8);

            LootChestFiles.ReloadRawBundle raw = files.beginReloadAsync().join();
            LootChestFiles.PreparedReload prepared = files.prepareReload(raw, () -> {
                throw new AssertionError("runtime data must not be saved");
            });
            java.nio.file.Files.writeString(dataFolder.resolve("data.yml"), externalEdit, StandardCharsets.UTF_8);

            assertThrows(
                    java.util.concurrent.CompletionException.class,
                    () -> files.commitReloadAsync(prepared).join());
            assertEquals("default", files.getConfig().getString("setting"));
            assertTrue(files.getSavedChestNames().isEmpty());
            assertEquals(externalEdit, java.nio.file.Files.readString(dataFolder.resolve("data.yml")));

            Path preserved = files.abortReloadAsync(prepared).join();
            files.finishAbortReload(prepared);
            assertEquals(original, java.nio.file.Files.readString(preserved));
            assertEquals(externalEdit, java.nio.file.Files.readString(dataFolder.resolve("data.yml")));
        }
    }

    @Test
    void reloadRecoveryCommitsAnExactBackupAndPreservesTheInvalidPrimary() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            java.nio.file.Files.writeString(
                    dataFolder.resolve("config.yml"),
                    "SaveDataFileDuringReload: false\n",
                    StandardCharsets.UTF_8);
            String invalidPrimary = "# preserve me exactly\nchests: [broken\n";
            String exactBackup = "# exact newest backup\n" + validSavedDefinition("recovered");
            java.nio.file.Files.writeString(dataFolder.resolve("data.yml"), invalidPrimary, StandardCharsets.UTF_8);
            java.nio.file.Files.createDirectories(dataFolder.resolve("backups"));
            java.nio.file.Files.writeString(
                    dataFolder.resolve("backups/7data.yml"),
                    exactBackup,
                    StandardCharsets.UTF_8);

            LootChestFiles.ReloadRawBundle raw = files.beginReloadAsync().join();
            LootChestFiles.PreparedReload prepared = files.prepareReload(raw, () -> {
                throw new AssertionError("runtime data must not be saved");
            });
            assertEquals(invalidPrimary, java.nio.file.Files.readString(dataFolder.resolve("data.yml")));
            files.commitReloadAsync(prepared).join();
            assertTrue(files.getSavedChestNames().isEmpty());
            assertEquals(exactBackup, java.nio.file.Files.readString(dataFolder.resolve("data.yml")));
            files.publishReload(prepared);

            assertTrue(files.getLoadableChestNames().contains("recovered"));
            try (Stream<Path> entries = java.nio.file.Files.list(dataFolder)) {
                List<Path> invalidCopies = entries
                        .filter(path -> path.getFileName().toString().startsWith("data.yml.invalid-"))
                        .toList();
                assertEquals(1, invalidCopies.size());
                assertEquals(invalidPrimary, java.nio.file.Files.readString(invalidCopies.getFirst()));
            }
        }
    }

    @Test
    void reloadGateRefusesSaveAndDoesNotRepublishValidation() throws Exception {
        try (LootChestFiles files = newFiles()) {
            files.initialize();
            LootChestFiles.ReloadRawBundle raw = files.beginReloadAsync().join();
            files.getData().set("chests.unsafely-mutated.position.x", "not-a-number");

            files.saveData();
            files.flush();

            assertTrue(files.getRejectedChestDefinitions().isEmpty());
            assertEquals("chests: {}\n", java.nio.file.Files.readString(dataFolder.resolve("data.yml")));
            LootChestFiles.PreparedReload prepared = files.prepareReload(raw, () -> {
                throw new AssertionError("runtime data must not be saved");
            });
            files.abortReloadAsync(prepared).join();
            files.finishAbortReload(prepared);
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
        return newFiles(logger);
    }

    private String validSavedDefinition(String chestName) {
        return """
                chests:
                  %s:
                    position:
                      world: world
                      x: 1
                      y: 64
                      z: 2
                """.formatted(chestName);
    }

    private LootChestFiles newFiles(Logger logger) {
        Function<String, InputStream> resources = name -> {
            String contents = DEFAULT_RESOURCES.get(name);
            return contents == null
                    ? null
                    : new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        };
        return new LootChestFiles(dataFolder, resources, logger);
    }

    private static final class CapturingHandler extends Handler {
        private final StringBuilder messages = new StringBuilder();

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                messages.append(record.getMessage()).append('\n');
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        private boolean contains(String text) {
            return messages.toString().contains(text);
        }
    }
}
