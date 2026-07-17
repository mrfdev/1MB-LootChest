package fr.black_eyes.lootchest;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns LootChest's three YAML files without relying on a shaded plugin framework.
 */
public final class LootChestFiles implements AutoCloseable {
    private static final String CONFIG_FILE = "config.yml";
    private static final String LANG_FILE = "lang.yml";
    private static final String DATA_FILE = "data.yml";
    private static final Pattern BACKUP_NAME = Pattern.compile("^(\\d+)data\\.yml$");
    private static final int MAX_BACKUPS = 10;

    private final Path dataFolder;
    private final Path configPath;
    private final Path langPath;
    private final Path dataPath;
    private final Path backupFolder;
    private final Function<String, InputStream> resourceLoader;
    private final Logger logger;
    private final ExecutorService fileExecutor;
    private final Object writeLock = new Object();
    private final AtomicReference<Throwable> writeFailure = new AtomicReference<>();

    private CompletableFuture<Void> pendingWrites = CompletableFuture.completedFuture(null);
    private YamlConfiguration config;
    private YamlConfiguration lang;
    private YamlConfiguration data;
    private volatile boolean initialized;
    private boolean closed;

    public LootChestFiles(JavaPlugin plugin) {
        this(
                plugin.getDataFolder().toPath(),
                plugin::getResource,
                plugin.getLogger());
    }

    LootChestFiles(
            Path dataFolder,
            Function<String, InputStream> resourceLoader,
            Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.configPath = dataFolder.resolve(CONFIG_FILE);
        this.langPath = dataFolder.resolve(LANG_FILE);
        this.dataPath = dataFolder.resolve(DATA_FILE);
        this.backupFolder = dataFolder.resolve("backups");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.fileExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "LootChest-file-io");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void initialize() throws IOException, InvalidConfigurationException {
        ensureOpen();
        if (initialized) {
            throw new IllegalStateException("LootChest files are already initialized");
        }

        java.nio.file.Files.createDirectories(dataFolder);
        copyResourceIfMissing(CONFIG_FILE, configPath);
        copyResourceIfMissing(LANG_FILE, langPath);
        copyResourceIfMissing(DATA_FILE, dataPath);

        YamlConfiguration loadedConfig = loadYaml(configPath);
        YamlConfiguration loadedLang = loadYaml(langPath);
        YamlConfiguration loadedData = loadDataWithRecovery();

        config = loadedConfig;
        lang = loadedLang;
        data = loadedData;
        initialized = true;
    }

    public FileConfiguration getConfig() {
        requireInitialized();
        return config;
    }

    public FileConfiguration getLang() {
        requireInitialized();
        return lang;
    }

    public FileConfiguration getData() {
        requireInitialized();
        return data;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setConfig(String path, Object value) {
        setIfMissing(config, path, value, CONFIG_FILE);
    }

    public void setLang(String path, Object value) {
        setIfMissing(lang, path, value, LANG_FILE);
    }

    public void saveConfig() {
        queueSave(config, configPath);
    }

    public void saveLang() {
        queueSave(lang, langPath);
    }

    public void saveData() {
        queueSave(data, dataPath);
    }

    public void reloadConfig() {
        requireInitialized();
        flush();
        try {
            YamlConfiguration loadedConfig = loadYaml(configPath);
            YamlConfiguration loadedLang = loadYaml(langPath);
            config = loadedConfig;
            lang = loadedLang;
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Could not reload config.yml and lang.yml", e);
        }
    }

    public void reloadData() {
        requireInitialized();
        flush();
        try {
            data = loadDataWithRecovery();
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Could not reload data.yml", e);
        }
    }

    public Path backupData() throws IOException {
        requireInitialized();
        flush();
        java.nio.file.Files.createDirectories(backupFolder);

        long nextIndex = listBackups().stream()
                .mapToLong(this::backupIndex)
                .max()
                .orElse(-1L) + 1L;
        Path backup = backupFolder.resolve(nextIndex + DATA_FILE);
        java.nio.file.Files.copy(dataPath, backup);
        pruneBackups();
        return backup;
    }

    /**
     * Waits until queued snapshots are durable. Used before reload and shutdown.
     */
    public void flush() {
        CompletableFuture<Void> writes;
        synchronized (writeLock) {
            writes = pendingWrites;
        }
        writes.join();

        Throwable failure = writeFailure.getAndSet(null);
        if (failure != null) {
            throw new IllegalStateException("One or more LootChest YAML writes failed", failure);
        }
    }

    @Override
    public void close() {
        CompletableFuture<Void> writes;
        synchronized (writeLock) {
            if (closed) {
                return;
            }
            closed = true;
            writes = pendingWrites;
        }

        try {
            writes.join();
            Throwable failure = writeFailure.getAndSet(null);
            if (failure != null) {
                logger.log(Level.SEVERE, "One or more LootChest YAML writes failed", failure);
            }
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not finish LootChest YAML writes", e);
        } finally {
            fileExecutor.shutdown();
            try {
                if (!fileExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    fileExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fileExecutor.shutdownNow();
            }
        }
    }

    private void setIfMissing(
            YamlConfiguration configuration,
            String path,
            Object value,
            String fileName) {
        requireInitialized();
        if (configuration.isSet(path)) {
            return;
        }
        configuration.set(path, value);
        logger.info("Added missing '" + path + "' to " + fileName);
    }

    private void queueSave(YamlConfiguration configuration, Path destination) {
        requireInitialized();
        String snapshot = configuration.saveToString();

        synchronized (writeLock) {
            ensureOpen();
            pendingWrites = pendingWrites
                    .handle((ignored, previousFailure) -> null)
                    .thenRunAsync(() -> {
                        try {
                            writeAtomically(destination, snapshot);
                        } catch (IOException e) {
                            writeFailure.compareAndSet(null, e);
                            logger.log(Level.SEVERE, "Could not save " + destination.getFileName(), e);
                        }
                    }, fileExecutor);
        }
    }

    private YamlConfiguration loadDataWithRecovery()
            throws IOException, InvalidConfigurationException {
        try {
            YamlConfiguration loaded = loadYaml(dataPath);
            validateData(loaded);
            return loaded;
        } catch (IOException | InvalidConfigurationException invalidData) {
            Path preserved = preserveInvalidData();
            logger.log(
                    Level.WARNING,
                    "data.yml is invalid; preserved it as " + preserved.getFileName()
                            + " and searching numbered backups.",
                    invalidData);

            for (Path backup : listBackupsNewestFirst()) {
                try {
                    YamlConfiguration recovered = loadYaml(backup);
                    validateData(recovered);
                    java.nio.file.Files.copy(
                            backup,
                            dataPath,
                            StandardCopyOption.REPLACE_EXISTING);
                    logger.warning("Recovered data.yml from " + backup.getFileName());
                    return recovered;
                } catch (IOException | InvalidConfigurationException backupFailure) {
                    logger.warning("Skipped invalid LootChest backup " + backup.getFileName());
                }
            }

            copyResource(DATA_FILE, dataPath, true);
            YamlConfiguration cleanData = loadYaml(dataPath);
            validateData(cleanData);
            logger.warning("No valid backup was available; initialized an empty data.yml.");
            return cleanData;
        }
    }

    private Path preserveInvalidData() throws IOException {
        String suffix = ".invalid-" + Instant.now().toEpochMilli();
        Path preserved = dataFolder.resolve(DATA_FILE + suffix);
        java.nio.file.Files.copy(dataPath, preserved, StandardCopyOption.REPLACE_EXISTING);
        return preserved;
    }

    private void validateData(YamlConfiguration configuration)
            throws InvalidConfigurationException {
        if (!configuration.isConfigurationSection("chests")) {
            throw new InvalidConfigurationException("data.yml is missing the 'chests' section");
        }
    }

    private YamlConfiguration loadYaml(Path path)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        try (Reader reader = java.nio.file.Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            configuration.load(reader);
        }
        return configuration;
    }

    private void copyResourceIfMissing(String resourceName, Path destination)
            throws IOException {
        if (!java.nio.file.Files.exists(destination)) {
            copyResource(resourceName, destination, false);
        }
    }

    private void copyResource(String resourceName, Path destination, boolean replace)
            throws IOException {
        try (InputStream resource = resourceLoader.apply(resourceName)) {
            if (resource == null) {
                throw new IOException("Missing embedded resource " + resourceName);
            }
            if (replace) {
                java.nio.file.Files.copy(
                        resource,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                java.nio.file.Files.copy(resource, destination);
            }
        }
    }

    private void writeAtomically(Path destination, String contents) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        java.nio.file.Files.writeString(
                temporary,
                contents,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            java.nio.file.Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            java.nio.file.Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<Path> listBackups() throws IOException {
        if (!java.nio.file.Files.isDirectory(backupFolder)) {
            return List.of();
        }
        try (Stream<Path> entries = java.nio.file.Files.list(backupFolder)) {
            return entries
                    .filter(path -> BACKUP_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingLong(this::backupIndex))
                    .toList();
        }
    }

    private List<Path> listBackupsNewestFirst() throws IOException {
        return listBackups().reversed();
    }

    private void pruneBackups() throws IOException {
        List<Path> backups = listBackups();
        int removeCount = Math.max(0, backups.size() - MAX_BACKUPS);
        for (int index = 0; index < removeCount; index++) {
            java.nio.file.Files.deleteIfExists(backups.get(index));
        }
    }

    private long backupIndex(Path path) {
        Matcher matcher = BACKUP_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a LootChest backup: " + path);
        }
        return Long.parseLong(matcher.group(1));
    }

    private void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("LootChest files are not initialized");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("LootChest files are already closed");
        }
    }
}
