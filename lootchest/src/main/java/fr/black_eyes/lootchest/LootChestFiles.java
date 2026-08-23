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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

import org.bukkit.configuration.ConfigurationSection;
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
    private CompletableFuture<?> transactionOperation = CompletableFuture.completedFuture(null);
    private YamlConfiguration config;
    private YamlConfiguration lang;
    private YamlConfiguration data;
    private SavedChestDataValidator.Result savedChestValidation;
    private volatile boolean initialized;
    private boolean reloadGate;
    private long dataEpoch;
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
        savedChestValidation = SavedChestDataValidator.validate(loadedData);
        initialized = true;
        logRejectedDefinitions(savedChestValidation);
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

    public Set<String> getLoadableChestNames() {
        requireInitialized();
        return savedChestValidation.acceptedNames();
    }

    public Map<String, List<String>> getRejectedChestDefinitions() {
        requireInitialized();
        return savedChestValidation.rejectedDefinitions();
    }

    public int getSavedChestDefinitionCount() {
        requireInitialized();
        return savedChestValidation.totalDefinitions();
    }

    public Set<String> getSavedChestNames() {
        requireInitialized();
        ConfigurationSection chests = data.getConfigurationSection("chests");
        return chests == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(chests.getValues(false).keySet()));
    }

    public List<String> getChestNameProblems(String chestName) {
        requireInitialized();
        return SavedChestDataValidator.validateNewName(chestName);
    }

    List<String> getSavedChestNameProblems(String chestName) {
        requireInitialized();
        return SavedChestDataValidator.validateName(chestName);
    }

    /**
     * Checks direct saved children without interpreting the name as a dotted
     * configuration path. Rejected definitions remain reserved so creation
     * cannot overwrite their preserved YAML.
     */
    public boolean hasSavedChestDefinition(String chestName) {
        requireInitialized();
        if (chestName == null) {
            return false;
        }
        ConfigurationSection chests = data.getConfigurationSection("chests");
        return chests != null && chests.getValues(false).containsKey(chestName);
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
        synchronized (writeLock) {
            ensureOpen();
            if (reloadGate) {
                logger.warning("Refused to save data.yml while a reload transaction is in progress.");
                return;
            }
        }

        SavedChestDataValidator.Result validation = SavedChestDataValidator.validate(data);
        String snapshot = data.saveToString();
        synchronized (writeLock) {
            ensureOpen();
            if (reloadGate) {
                logger.warning("Refused to save data.yml because a reload transaction started "
                        + "while the snapshot was being serialized.");
                return;
            }
            savedChestValidation = validation;
            queueSnapshotLocked(snapshot, dataPath, dataEpoch);
        }
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

    void reloadData() {
        requireInitialized();
        requireNoReloadTransaction("reload data.yml");
        flush();
        try {
            YamlConfiguration loadedData = loadDataWithRecovery();
            SavedChestDataValidator.Result validation = SavedChestDataValidator.validate(loadedData);
            data = loadedData;
            savedChestValidation = validation;
            logRejectedDefinitions(validation);
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Could not reload data.yml", e);
        }
    }

    /**
     * Closes the data-save gate and reads immutable reload inputs on the file
     * executor. YAML decoding is deliberately deferred to {@link
     * #prepareReload(ReloadRawBundle, Runnable)} because Bukkit object
     * deserialization must remain on the server thread.
     */
    public CompletableFuture<ReloadRawBundle> beginReloadAsync() {
        requireInitialized();
        CompletableFuture<Void> writeBarrier;
        long reloadEpoch;
        synchronized (writeLock) {
            ensureOpen();
            if (reloadGate) {
                throw new IllegalStateException("A LootChest reload transaction is already in progress");
            }
            reloadGate = true;
            reloadEpoch = ++dataEpoch;
            writeBarrier = pendingWrites;
        }

        CompletableFuture<ReloadRawBundle> operation = writeBarrier.thenApplyAsync(ignored -> {
            Throwable previousWriteFailure = writeFailure.getAndSet(null);
            if (previousWriteFailure != null) {
                throw new CompletionException(
                        new IllegalStateException("A queued YAML write failed before reload", previousWriteFailure));
            }
            try {
                List<RawBackup> backups = new ArrayList<>();
                for (Path backup : listBackupsNewestFirst()) {
                    backups.add(new RawBackup(
                            backup.getFileName().toString(),
                            java.nio.file.Files.readString(backup, StandardCharsets.UTF_8)));
                }
                return new ReloadRawBundle(
                        this,
                        reloadEpoch,
                        java.nio.file.Files.readString(configPath, StandardCharsets.UTF_8),
                        java.nio.file.Files.readString(langPath, StandardCharsets.UTF_8),
                        java.nio.file.Files.readString(dataPath, StandardCharsets.UTF_8),
                        List.copyOf(backups),
                        readBundledResource(DATA_FILE));
            } catch (IOException exception) {
                throw new CompletionException(
                        new IllegalStateException("Could not read LootChest YAML reload inputs", exception));
            }
        }, fileExecutor);
        trackTransactionOperation(operation);
        return operation;
    }

    /**
     * Decodes an isolated reload candidate without publishing it. The supplied
     * action is invoked only when the candidate config requests the current
     * runtime chest state. It must update the in-memory data configuration but
     * must not enqueue a file write.
     */
    public PreparedReload prepareReload(
            ReloadRawBundle rawBundle,
            Runnable snapshotRuntimeData) {
        requireInitialized();
        requireRawBundle(rawBundle);
        Objects.requireNonNull(snapshotRuntimeData, "snapshotRuntimeData");
        ensureReloadEpoch(rawBundle.epoch);

        try {
            YamlConfiguration loadedConfig = loadYaml(rawBundle.configText);
            YamlConfiguration loadedLang = loadYaml(rawBundle.langText);
            boolean retainRuntimeData = loadedConfig.getBoolean("SaveDataFileDuringReload", false);
            YamlConfiguration loadedData;
            SavedChestDataValidator.Result validation;
            String committedDataText = null;
            String recoverySource = null;
            boolean invalidPrimaryData = false;

            if (retainRuntimeData) {
                YamlConfiguration publishedData = data;
                YamlConfiguration isolatedRuntimeData = loadYaml(publishedData.saveToString());
                data = isolatedRuntimeData;
                try {
                    snapshotRuntimeData.run();
                    committedDataText = isolatedRuntimeData.saveToString();
                } finally {
                    data = publishedData;
                }
                loadedData = loadYaml(committedDataText);
                validateData(loadedData);
                validation = SavedChestDataValidator.validate(loadedData);
            } else {
                DataCandidate candidate = decodeDataCandidate(rawBundle);
                loadedData = candidate.configuration;
                validation = SavedChestDataValidator.validate(loadedData);
                invalidPrimaryData = candidate.invalidPrimary;
                recoverySource = candidate.recoverySource;
                if (invalidPrimaryData) {
                    committedDataText = candidate.rawText;
                }
            }

            return new PreparedReload(
                    this,
                    rawBundle.epoch,
                    rawBundle.configText,
                    rawBundle.langText,
                    rawBundle.dataText,
                    loadedConfig,
                    loadedLang,
                    loadedData,
                    validation,
                    committedDataText,
                    invalidPrimaryData,
                    recoverySource);
        } catch (InvalidConfigurationException | RuntimeException exception) {
            throw new IllegalStateException("Could not prepare LootChest YAML reload", exception);
        }
    }

    /**
     * Verifies that the operator did not modify any input during teardown, then
     * durably commits only the data file that actually needs replacement.
     */
    public CompletableFuture<Void> commitReloadAsync(PreparedReload preparedReload) {
        requirePreparedReload(preparedReload);
        synchronized (writeLock) {
            ensureOpen();
        }
        ensureReloadEpoch(preparedReload.epoch);
        preparedReload.startCommit();

        CompletableFuture<Void> writeBarrier;
        synchronized (writeLock) {
            writeBarrier = pendingWrites;
        }
        CompletableFuture<Void> operation;
        try {
            operation = writeBarrier.thenRunAsync(() -> {
            try {
                assertUnchanged(configPath, preparedReload.originalConfigText);
                assertUnchanged(langPath, preparedReload.originalLangText);
                assertUnchanged(dataPath, preparedReload.originalDataText);
                if (preparedReload.invalidPrimaryData) {
                    preparedReload.preservedInvalidData = preserveRawData(
                            DATA_FILE + ".invalid-",
                            preparedReload.originalDataText);
                }
                if (preparedReload.committedDataText != null) {
                    writeAtomicallyIfUnchanged(
                            dataPath,
                            preparedReload.committedDataText,
                            preparedReload.originalDataText);
                }
            } catch (IOException | RuntimeException exception) {
                throw new CompletionException(
                        new IllegalStateException("Could not commit the prepared LootChest reload", exception));
            }
            }, fileExecutor);
        } catch (RuntimeException submissionFailure) {
            preparedReload.commitFailed();
            throw submissionFailure;
        }
        CompletableFuture<Void> completedOperation = operation.whenComplete((ignored, failure) -> {
            if (failure == null) {
                preparedReload.commitSucceeded();
            } else {
                preparedReload.commitFailed();
            }
        });
        trackTransactionOperation(completedOperation);
        return completedOperation;
    }

    /** Publishes objects only after {@link #commitReloadAsync} is durable. */
    public void publishReload(PreparedReload preparedReload) {
        requirePreparedReload(preparedReload);
        ensureReloadEpoch(preparedReload.epoch);
        preparedReload.publish();
        config = preparedReload.config;
        lang = preparedReload.lang;
        data = preparedReload.data;
        savedChestValidation = preparedReload.validation;
        finishReloadGate(preparedReload.epoch);

        if (preparedReload.invalidPrimaryData) {
            logger.warning("Reload recovered invalid data.yml from " + preparedReload.recoverySource
                    + "; preserved the invalid candidate as "
                    + preparedReload.preservedInvalidData.getFileName() + ".");
        }
        logRejectedDefinitions(savedChestValidation);
    }

    /**
     * Preserves the exact uncommitted data candidate on the file executor. The
     * save gate remains closed until {@link #finishAbortReload} is called, and
     * remains closed if preservation fails.
     */
    public CompletableFuture<Path> abortReloadAsync(PreparedReload preparedReload) {
        requirePreparedReload(preparedReload);
        synchronized (writeLock) {
            ensureOpen();
        }
        ensureReloadEpoch(preparedReload.epoch);
        preparedReload.startAbort();
        CompletableFuture<Path> operation;
        try {
            operation = CompletableFuture.supplyAsync(() -> {
            try {
                String currentDataText = java.nio.file.Files.readString(dataPath, StandardCharsets.UTF_8);
                if (!currentDataText.equals(preparedReload.originalDataText)) {
                    preparedReload.recordConflictPreservation(preserveRawData(
                            DATA_FILE + ".reload-conflict-",
                            currentDataText));
                }
                Path preserved = preserveRawData(
                        DATA_FILE + ".reload-aborted-",
                        preparedReload.originalDataText);
                return preserved;
            } catch (IOException | RuntimeException exception) {
                throw new CompletionException(new IllegalStateException(
                        "Could not preserve the uncommitted data.yml reload candidate",
                        exception));
            }
            }, fileExecutor);
        } catch (RuntimeException submissionFailure) {
            preparedReload.abortFailed();
            throw submissionFailure;
        }
        CompletableFuture<Path> completedOperation = operation.whenComplete((preserved, failure) -> {
            if (failure == null) {
                preparedReload.abortSucceeded(preserved);
            } else {
                preparedReload.abortFailed();
            }
        });
        trackTransactionOperation(completedOperation);
        return completedOperation;
    }

    public void finishAbortReload(PreparedReload preparedReload) {
        requirePreparedReload(preparedReload);
        Path preserved = preparedReload.finishAbort();
        finishReloadGate(preparedReload.epoch);
        logger.warning("Reload was aborted; preserved the candidate data.yml as "
                + preserved.getFileName() + ".");
        Path conflictCopy = preparedReload.conflictPreservation();
        if (conflictCopy != null) {
            logger.warning("data.yml changed after reload preparation; preserved that newer text as "
                    + conflictCopy.getFileName() + ".");
        }
    }

    /** Releases a gate when raw input reading or main-thread decoding failed. */
    void cancelReloadGate() {
        requireInitialized();
        synchronized (writeLock) {
            if (!reloadGate) {
                return;
            }
            reloadGate = false;
            dataEpoch++;
        }
    }

    public boolean isReloadInProgress() {
        synchronized (writeLock) {
            return reloadGate;
        }
    }

    public static final class ReloadRawBundle {
        private final LootChestFiles owner;
        private final long epoch;
        private final String configText;
        private final String langText;
        private final String dataText;
        private final List<RawBackup> backups;
        private final String bundledDataText;

        private ReloadRawBundle(
                LootChestFiles owner,
                long epoch,
                String configText,
                String langText,
                String dataText,
                List<RawBackup> backups,
                String bundledDataText) {
            this.owner = owner;
            this.epoch = epoch;
            this.configText = configText;
            this.langText = langText;
            this.dataText = dataText;
            this.backups = backups;
            this.bundledDataText = bundledDataText;
        }
    }

    public static final class PreparedReload {
        private final LootChestFiles owner;
        private final long epoch;
        private final String originalConfigText;
        private final String originalLangText;
        private final String originalDataText;
        private final YamlConfiguration config;
        private final YamlConfiguration lang;
        private final YamlConfiguration data;
        private final SavedChestDataValidator.Result validation;
        private final String committedDataText;
        private final boolean invalidPrimaryData;
        private final String recoverySource;
        private ReloadState state = ReloadState.PREPARED;
        private Path preservedInvalidData;
        private Path preservedAbortedData;
        private Path preservedConflictData;

        private PreparedReload(
                LootChestFiles owner,
                long epoch,
                String originalConfigText,
                String originalLangText,
                String originalDataText,
                YamlConfiguration config,
                YamlConfiguration lang,
                YamlConfiguration data,
                SavedChestDataValidator.Result validation,
                String committedDataText,
                boolean invalidPrimaryData,
                String recoverySource) {
            this.owner = owner;
            this.epoch = epoch;
            this.originalConfigText = originalConfigText;
            this.originalLangText = originalLangText;
            this.originalDataText = originalDataText;
            this.config = config;
            this.lang = lang;
            this.data = data;
            this.validation = validation;
            this.committedDataText = committedDataText;
            this.invalidPrimaryData = invalidPrimaryData;
            this.recoverySource = recoverySource;
        }

        private synchronized void startCommit() {
            requireState(ReloadState.PREPARED);
            state = ReloadState.COMMITTING;
        }

        private synchronized void commitSucceeded() {
            requireState(ReloadState.COMMITTING);
            state = ReloadState.COMMITTED;
        }

        private synchronized void commitFailed() {
            requireState(ReloadState.COMMITTING);
            state = ReloadState.PREPARED;
        }

        private synchronized void publish() {
            requireState(ReloadState.COMMITTED);
            state = ReloadState.PUBLISHED;
        }

        private synchronized void startAbort() {
            requireState(ReloadState.PREPARED);
            state = ReloadState.ABORTING;
        }

        private synchronized void abortSucceeded(Path preserved) {
            requireState(ReloadState.ABORTING);
            preservedAbortedData = preserved;
            state = ReloadState.ABORTED_IO;
        }

        private synchronized void abortFailed() {
            requireState(ReloadState.ABORTING);
            state = ReloadState.PREPARED;
        }

        private synchronized void recordConflictPreservation(Path preserved) {
            preservedConflictData = preserved;
        }

        private synchronized Path conflictPreservation() {
            return preservedConflictData;
        }

        private synchronized Path finishAbort() {
            requireState(ReloadState.ABORTED_IO);
            state = ReloadState.ABORTED;
            return preservedAbortedData;
        }

        synchronized boolean isCommittedAwaitingPublish() {
            return state == ReloadState.COMMITTED;
        }

        synchronized boolean isPreparedForAbort() {
            return state == ReloadState.PREPARED;
        }

        synchronized boolean isAbortReadyToFinish() {
            return state == ReloadState.ABORTED_IO;
        }

        private void requireState(ReloadState required) {
            if (state != required) {
                throw new IllegalStateException("Prepared reload is " + state
                        + ", expected " + required);
            }
        }
    }

    private enum ReloadState {
        PREPARED,
        COMMITTING,
        COMMITTED,
        PUBLISHED,
        ABORTING,
        ABORTED_IO,
        ABORTED
    }

    private record RawBackup(String fileName, String rawText) {
    }

    private record DataCandidate(
            YamlConfiguration configuration,
            String rawText,
            boolean invalidPrimary,
            String recoverySource) {
    }

    Path backupData() throws IOException {
        requireInitialized();
        requireNoReloadTransaction("back up data.yml");
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
        CompletableFuture<?> transaction;
        synchronized (writeLock) {
            if (closed) {
                return;
            }
            closed = true;
            writes = pendingWrites;
            transaction = transactionOperation;
        }

        try {
            writes.join();
            transaction.join();
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
            queueSnapshotLocked(snapshot, destination, null);
        }
    }

    private void queueSnapshotLocked(String snapshot, Path destination, Long expectedDataEpoch) {
        pendingWrites = pendingWrites
                .handle((ignored, previousFailure) -> null)
                .thenRunAsync(() -> {
                    synchronized (writeLock) {
                        if (expectedDataEpoch != null && expectedDataEpoch != dataEpoch) {
                            return;
                        }
                    }
                    try {
                        writeAtomically(destination, snapshot);
                    } catch (IOException e) {
                        writeFailure.compareAndSet(null, e);
                        logger.log(Level.SEVERE, "Could not save " + destination.getFileName(), e);
                    }
                }, fileExecutor);
    }

    private void trackTransactionOperation(CompletableFuture<?> operation) {
        synchronized (writeLock) {
            transactionOperation = operation;
        }
    }

    private void requireNoReloadTransaction(String action) {
        synchronized (writeLock) {
            if (reloadGate) {
                throw new IllegalStateException("Cannot " + action
                        + " while a reload transaction is in progress");
            }
        }
    }

    private DataCandidate decodeDataCandidate(ReloadRawBundle rawBundle)
            throws InvalidConfigurationException {
        try {
            YamlConfiguration primary = loadYaml(rawBundle.dataText);
            validateData(primary);
            return new DataCandidate(primary, rawBundle.dataText, false, null);
        } catch (InvalidConfigurationException | RuntimeException invalidPrimary) {
            for (RawBackup backup : rawBundle.backups) {
                try {
                    YamlConfiguration recovered = loadYaml(backup.rawText);
                    validateData(recovered);
                    return new DataCandidate(
                            recovered,
                            backup.rawText,
                            true,
                            "numbered backup " + backup.fileName);
                } catch (InvalidConfigurationException | RuntimeException invalidBackup) {
                    // Continue newest-first. Per-child schema errors are not
                    // document failures and therefore never reach this path.
                }
            }

            try {
                YamlConfiguration cleanData = loadYaml(rawBundle.bundledDataText);
                validateData(cleanData);
                return new DataCandidate(
                        cleanData,
                        rawBundle.bundledDataText,
                        true,
                        "the bundled empty data.yml");
            } catch (InvalidConfigurationException | RuntimeException invalidBundledData) {
                InvalidConfigurationException failure = new InvalidConfigurationException(
                        "Neither data.yml, a numbered backup, nor the bundled data.yml is valid");
                failure.initCause(invalidPrimary);
                failure.addSuppressed(invalidBundledData);
                throw failure;
            }
        }
    }

    private YamlConfiguration loadYaml(String contents)
            throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        configuration.loadFromString(contents);
        return configuration;
    }

    private String readBundledResource(String resourceName) throws IOException {
        try (InputStream resource = resourceLoader.apply(resourceName)) {
            if (resource == null) {
                throw new IOException("Missing embedded resource " + resourceName);
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertUnchanged(Path path, String expectedContents) throws IOException {
        String currentContents = java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
        if (!currentContents.equals(expectedContents)) {
            throw new IllegalStateException(path.getFileName()
                    + " changed while the reload transaction was in progress");
        }
    }

    private Path preserveRawData(String prefix, String contents) throws IOException {
        Path preserved = java.nio.file.Files.createTempFile(dataFolder, prefix, ".yml");
        java.nio.file.Files.writeString(
                preserved,
                contents,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return preserved;
    }

    private void requireRawBundle(ReloadRawBundle rawBundle) {
        Objects.requireNonNull(rawBundle, "rawBundle");
        if (rawBundle.owner != this) {
            throw new IllegalArgumentException("Raw reload bundle belongs to a different LootChestFiles instance");
        }
    }

    private void requirePreparedReload(PreparedReload preparedReload) {
        requireInitialized();
        Objects.requireNonNull(preparedReload, "preparedReload");
        if (preparedReload.owner != this) {
            throw new IllegalArgumentException("Prepared reload belongs to a different LootChestFiles instance");
        }
    }

    private void ensureReloadEpoch(long expectedEpoch) {
        synchronized (writeLock) {
            if (!reloadGate || dataEpoch != expectedEpoch) {
                throw new IllegalStateException("LootChest reload transaction is no longer active");
            }
        }
    }

    private void finishReloadGate(long expectedEpoch) {
        synchronized (writeLock) {
            if (!reloadGate || dataEpoch != expectedEpoch) {
                throw new IllegalStateException("LootChest reload transaction is no longer active");
            }
            reloadGate = false;
        }
    }

    private YamlConfiguration loadDataWithRecovery()
            throws IOException, InvalidConfigurationException {
        try {
            YamlConfiguration loaded = loadYaml(dataPath);
            validateData(loaded);
            return loaded;
        } catch (IOException | InvalidConfigurationException | RuntimeException invalidData) {
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
                } catch (IOException | InvalidConfigurationException | RuntimeException backupFailure) {
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

    private void logRejectedDefinitions(SavedChestDataValidator.Result validation) {
        if (validation.rejectedDefinitions().isEmpty()) {
            return;
        }

        logger.warning("Found " + validation.rejectedDefinitions().size()
                + " malformed saved LootChest definition(s). They will not be loaded, "
                + "but their data remains preserved in data.yml; no backup was restored.");
        int logged = 0;
        for (Map.Entry<String, List<String>> rejected : validation.rejectedDefinitions().entrySet()) {
            if (logged == 25) {
                logger.warning((validation.rejectedDefinitions().size() - logged)
                        + " additional malformed definition(s) were omitted from this log summary.");
                break;
            }
            logger.warning("Rejected saved LootChest '" + printableName(rejected.getKey()) + "': "
                    + String.join("; ", rejected.getValue()));
            logged++;
        }
    }

    private String printableName(String chestName) {
        StringBuilder printable = new StringBuilder();
        chestName.codePoints().forEach(character -> {
            if (Character.isISOControl(character)) {
                printable.append('?');
            } else {
                printable.appendCodePoint(character);
            }
        });
        return printable.toString();
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

    private void writeAtomicallyIfUnchanged(
            Path destination,
            String contents,
            String expectedContents) throws IOException {
        Path temporary = java.nio.file.Files.createTempFile(
                destination.getParent(),
                destination.getFileName() + ".reload-",
                ".tmp");
        try {
            java.nio.file.Files.writeString(
                    temporary,
                    contents,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            // This final comparison is immediately adjacent to promotion. It
            // cannot provide a cross-process CAS on a portable filesystem, but
            // it minimizes the remaining editor race and never skips a known
            // conflict.
            assertUnchanged(destination, expectedContents);
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
        } finally {
            java.nio.file.Files.deleteIfExists(temporary);
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
