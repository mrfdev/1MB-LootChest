package fr.black_eyes.lootchest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.Zrips.CMI.CMI;
import fr.black_eyes.lootchest.commands.SubCommand;
import fr.black_eyes.lootchest.compat.CompatibilityMigrations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import fr.black_eyes.lootchest.commands.CommandHandler;
import fr.black_eyes.lootchest.listeners.DeleteListener;
import fr.black_eyes.lootchest.listeners.UiListener;
import fr.black_eyes.lootchest.index.BlockLocationIndex;
import fr.black_eyes.lootchest.particles.ParticleCatalog;
import fr.black_eyes.lootchest.scheduler.TaskRegistry;
import fr.black_eyes.lootchest.ui.ChestUi;
import fr.black_eyes.lootchest.ui.UiHandler;
import lombok.Getter;
import lombok.Setter;

import static fr.black_eyes.lootchest.Constants.DATA_CHEST_PATH;


public class Main extends JavaPlugin {
	public static final String MENU_MAIN_TYPE = "Menu.main.type";
	private static final String PARTICLE_TASK = "particles";
	private static final String STARTUP_TASK = "startup";
	private static final String CHEST_LOAD_TASK = "chest-load";
	private static final String CHEST_SPAWN_TASK = "chest-spawn";
	private static final String CHEST_BULK_TASK = "chest-bulk";
	private static final String CHEST_RELOAD_DEACTIVATE_TASK = "chest-reload-deactivate";
	private static final String CHEST_RELOAD_ROLLBACK_TASK = "chest-reload-rollback";
	private enum ChestDefinitionLoadResult {
		LOADED,
		WORLD_UNAVAILABLE,
		FAILED
	}
	@Getter private final HashMap<Location, Long> protection = new HashMap<>();
	@Getter private final LinkedHashMap<String, Particle> particles = new LinkedHashMap<>();
	@Getter private final HashMap<Location, Particle> part = new HashMap<>();
	@Getter private ParticleCatalog particleCatalog;
	private final Set<Particle> failedParticles = EnumSet.noneOf(Particle.class);
	@Setter public static Config configs;
	@Getter private HashMap<String, Lootchest> lootChest;
	@Getter private BlockLocationIndex<Lootchest> lootChestLocationIndex;
	@Getter @Setter private static Main instance;
	@Getter private LootChestUtils utils;
	@Getter private boolean cmiHologramsAvailable;
	@Getter private UiHandler uiHandler;
	@Getter private TaskRegistry taskRegistry;
	@Getter private LootChestFiles configFiles;
	private DeleteListener deleteListener;
	@Getter private BuildInfo buildInfo = BuildInfo.unknown();
	@Getter private String hologramIntegrationStatus = "disabled during startup";
	@Getter private boolean chestWorkInProgress;
	private LootChestFiles.PreparedReload pendingReload;
	private CompletableFuture<?> reloadFileOperation;
	private boolean snapshottingReloadData;
	private List<ReloadRuntimeState> reloadRuntimeStates = List.of();
	private IdentityHashMap<Lootchest, ReloadPhysicalSnapshot> reloadTouched = new IdentityHashMap<>();
	private boolean reloadRuntimeSuspended;
	private boolean cmiVersionWarningLogged;
	private final Set<String> locationIndexMismatchWarnings = new HashSet<>();
	@Getter private final Set<String> unavailableChestDefinitions = new LinkedHashSet<>();
	@Getter private final Map<String, String> failedChestDefinitions = new LinkedHashMap<>();


	@Override
	public void onDisable() {
		if (uiHandler != null) {
			uiHandler.closeAll(ChestUi.CloseReason.SHUTDOWN);
		}
		if (deleteListener != null) {
			deleteListener.clearTrackedInventories();
		}
		if (taskRegistry != null) {
			taskRegistry.cancelAll();
		}
		settleReloadDuringShutdown();
		if (taskRegistry != null) {
			taskRegistry.cancelAll();
		}
		if (lootChest != null) {
			lootChest.values().forEach(chest -> chest.getHologram().remove());
		}
		if (lootChest != null && configFiles != null && configFiles.isInitialized()
				&& !configFiles.isReloadInProgress()) {
			try {
				LootChestUtils.saveAllChests();
				configFiles.flush();
				configFiles.backupData();
				Messages.log("<#a6e3a1>Backed up data file for rollback.");
			} catch (IOException | RuntimeException e) {
				getLogger().log(Level.SEVERE, "Could not finish saving LootChest data", e);
			}
		} else if (configFiles != null && configFiles.isInitialized()
				&& configFiles.isReloadInProgress()) {
			getLogger().warning("Skipped the normal shutdown data save because a reload transaction "
					+ "is still being preserved or committed.");
		}
		if (configFiles != null) {
			configFiles.close();
		}
		if (lootChestLocationIndex != null) {
			lootChestLocationIndex.clear();
			locationIndexMismatchWarnings.clear();
		}
	}

	private void settleReloadDuringShutdown() {
		if (pendingReload == null || configFiles == null || !configFiles.isInitialized()) {
			return;
		}

		if (reloadFileOperation != null) {
			try {
				reloadFileOperation.join();
			} catch (RuntimeException operationFailure) {
				getLogger().log(
						Level.WARNING,
						"The in-flight reload file operation failed while shutdown was settling it.",
						unwrapCompletionFailure(operationFailure));
			} finally {
				reloadFileOperation = null;
			}
		}

		if (pendingReload.isCommittedAwaitingPublish()) {
			try {
				configFiles.publishReload(pendingReload);
				pendingReload = null;
				clearReloadRollbackState();
				activateCommittedReloadForShutdown();
				return;
			} catch (RuntimeException | LinkageError activationFailure) {
				getLogger().log(
						Level.SEVERE,
						"A committed reload could not be made physically durable during shutdown.",
						activationFailure);
			}
		}
		if (pendingReload == null) {
			return;
		}

		try {
			if (pendingReload.isPreparedForAbort()) {
				configFiles.abortReloadAsync(pendingReload).join();
			}
			if (pendingReload.isAbortReadyToFinish()) {
				configFiles.finishAbortReload(pendingReload);
				pendingReload = null;
			}
		} catch (RuntimeException abortFailure) {
			getLogger().log(
					Level.SEVERE,
					"Could not preserve and abort the reload candidate during shutdown.",
					unwrapCompletionFailure(abortFailure));
		}

		if (reloadRuntimeSuspended) {
			for (ReloadRuntimeState runtimeState : reloadRuntimeStates) {
				restoreReloadRuntimeState(runtimeState, reloadTouched);
			}
		}
		clearReloadRollbackState();
	}

	private void activateCommittedReloadForShutdown() {
		deleteListener.clearTrackedInventories();
		taskRegistry.cancelAll();
		clearRuntimeOwnershipAfterTeardown();
		setConfigs(Config.getInstance(configFiles.getConfig()));
		reloadParticleCatalog();
		// Only physical durability matters now. Integrations and visual tasks are
		// deliberately suppressed because the plugin is already disabling.
		configs.usehologram = false;
		unavailableChestDefinitions.clear();
		failedChestDefinitions.clear();
		for (String chestName : configFiles.getLoadableChestNames()) {
			try {
				if (loadChestDefinition(chestName) == ChestDefinitionLoadResult.LOADED) {
					spawnLoadedChestSafely(lootChest.get(chestName), true);
				}
			} catch (RuntimeException | LinkageError activationFailure) {
				recordDefinitionFailure(chestName, "shutdown activation", activationFailure);
				getLogger().log(
						Level.SEVERE,
						"Could not materialize committed LootChest '"
								+ printableChestName(chestName) + "' during shutdown.",
						activationFailure);
			}
		}
	}

	private void clearReloadRollbackState() {
		reloadRuntimeStates = List.of();
		reloadTouched = new IdentityHashMap<>();
		reloadRuntimeSuspended = false;
	}

	@Override
	public void onEnable() {
		setInstance(this);
		buildInfo = BuildInfo.load(getLogger());

		getLogger().info("Loading config files...");
		configFiles = new LootChestFiles(this);
		try {
			configFiles.initialize();
		} catch (IOException | InvalidConfigurationException | RuntimeException e) {
			getLogger().log(
					Level.SEVERE,
					"Configuration or data files could not be initialized. LootChest will stop.",
					e);
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		lootChest = new HashMap<>();
		lootChestLocationIndex = new BlockLocationIndex<>();
		taskRegistry = new TaskRegistry(this);

		Messages.log("config files loaded");
		Messages.log("Server version: " + Bukkit.getMinecraftVersion());
		Messages.log(
				"<#a6e3a1>Release: <#89dceb>[Artifact] <#6c7086>| <#a6e3a1>build <#89dceb>[Build] "
						+ "<#6c7086>| <#a6e3a1>source <#89dceb>[Source] <#6c7086>| "
						+ "<#a6e3a1>Paper <#89dceb>[Paper] build [PaperBuild] [PaperChannel] "
						+ "<#6c7086>(API [PaperApi]) | "
						+ "<#a6e3a1>Java <#89dceb>[Java]",
				"[Artifact]", buildInfo.artifactName(),
				"[Build]", buildInfo.buildNumber(),
				"[Source]", buildInfo.sourceDisplay(),
				"[Paper]", buildInfo.paperTarget(),
				"[PaperBuild]", buildInfo.paperBuild(),
				"[PaperChannel]", buildInfo.paperChannel(),
				"[PaperApi]", buildInfo.paperApi(),
				"[Java]", buildInfo.javaTarget());
		// Add newly introduced defaults without removing local settings.
		updateOldConfig();
		configFiles.reloadConfig();
		utils = new LootChestUtils();

		uiHandler = new UiHandler(this);
		registerEvents(uiHandler);
		registerCommands();
		
		//load config
		setConfigs(Config.getInstance(configFiles.getConfig()));
		startCmiHolograms();
 
	        Messages.log("Starting particles...");
        
		reloadParticleCatalog();
        if(configs.partEnable) {
			//loop de tous les coffres tous les 1/4 (modifiable dans la config) de secondes pour faire spawn des particules
			//loop of all chests every 1/4 (editable in config) of seconds to spawn particles 
			startParticles();
		}
    	
    	//Loads all chests asynchronously
    	loadChests();
        
	}

	private void startCmiHolograms() {
		cmiHologramsAvailable = false;
		if (!configs.usehologram) {
			hologramIntegrationStatus = "disabled by configuration";
			return;
		}

		PluginManager pluginManager = Bukkit.getPluginManager();
		Plugin cmiPlugin = pluginManager.getPlugin("CMI");
		Plugin cmiLibPlugin = pluginManager.getPlugin("CMILib");
		if (cmiPlugin == null || cmiLibPlugin == null
				|| !cmiPlugin.isEnabled() || !cmiLibPlugin.isEnabled()) {
			configs.usehologram = false;
			hologramIntegrationStatus = "disabled (optional CMI/CMILib unavailable)";
			getLogger().warning("CMI and CMILib are not both enabled; LootChest will continue without holograms.");
			return;
		}

		try {
			CMI cmi = CMI.getInstance();
			if (cmi == null || cmi.getHologramManager() == null) {
				configs.usehologram = false;
				hologramIntegrationStatus = "disabled (CMI hologram manager unavailable)";
				getLogger().warning("CMI's hologram manager is unavailable; LootChest holograms are disabled.");
				return;
			}
			String cmiVersion = cmiPlugin.getPluginMeta().getVersion();
			String cmiLibVersion = cmiLibPlugin.getPluginMeta().getVersion();
			cmiHologramsAvailable = true;
			hologramIntegrationStatus = "CMI " + cmiVersion + " / CMILib " + cmiLibVersion;
			Messages.log(
					"<#a6e3a1>Using CMI holograms: <#89dceb>CMI [Cmi] <#6c7086>/ <#89dceb>CMILib [CmiLib]",
					"[Cmi]", cmiVersion,
					"[CmiLib]", cmiLibVersion);
			if (!cmiVersionWarningLogged
					&& !"unknown".equals(buildInfo.cmiTestedVersion())
					&& (!buildInfo.cmiTestedVersion().equals(cmiVersion)
					|| !buildInfo.cmiLibTestedVersion().equals(cmiLibVersion))) {
				cmiVersionWarningLogged = true;
				getLogger().warning("CMI holograms are running with an unvalidated version pair. "
						+ "This build is supported with CMI " + buildInfo.cmiTestedVersion()
						+ " and CMILib " + buildInfo.cmiLibTestedVersion() + ".");
			}
		} catch (RuntimeException | LinkageError e) {
			configs.usehologram = false;
			cmiHologramsAvailable = false;
			hologramIntegrationStatus = "disabled (CMI integration error)";
			getLogger().warning("CMI holograms failed to start; LootChest holograms are disabled. "
					+ e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private void registerEvents(UiHandler uiHandler) {
		PluginManager pluginManager = Bukkit.getPluginManager();
		deleteListener = new DeleteListener();
		pluginManager.registerEvents(deleteListener, this);
		pluginManager.registerEvents(new UiListener(uiHandler), this);
	}

	private void registerCommands() {
		CommandHandler cmdHandler = new CommandHandler(this, "lootchest");
		String commandsPackage = "fr/black_eyes/lootchest/commands/commands/";
		// get all class names in the commands package instead of hardcoding them
		for (String command : LootChestUtils.getClassesFromJARFile("fr/black_eyes/lootchest/commands/commands/")) {
            try {
                cmdHandler.addSubCommand((SubCommand) Class.forName(commandsPackage.replace("/", ".") + command).getConstructor().newInstance());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException | ClassNotFoundException e) {
				Messages.log("<#f38ba8>Error while registering command " + command);
            }
        }

	}
	
	/**
	 * Loop all chests every 1/4 of second (configurable in config.yml) and spawns particles around it.
	 * Servers with bad performances (or with 400 chests) should disable particles.
	 */
	private void startParticles() {
		taskRegistry.runRepeating(PARTICLE_TASK, () -> {
				if (!configs.partEnable) {
					return;
				}
				for (Map.Entry<Location, Particle> entry : part.entrySet()) {
					Location location = entry.getKey();
					Particle particle = entry.getValue();
					if (particle == null || location.getWorld() == null
							|| !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
						continue;
					}
					try {
						particleCatalog.display(particle, location, configs.partNumber,
								configs.PART_radius, configs.PART_speed, location.getWorld().getPlayers());
					} catch (RuntimeException exception) {
						if (failedParticles.add(particle)) {
							getLogger().warning("Particle " + particle.name() + " failed to spawn; using "
									+ particleCatalog.getFallback().name() + ". "
									+ exception.getClass().getSimpleName() + ": " + exception.getMessage());
						}
						entry.setValue(particleCatalog.getFallback());
					}
				}
			}, 0L, configs.partRespawnTicks);
	}
    		
	/**
	 * Loads all chests after the configured startup delay.
	 */
	private void loadChests() {
		long countdown = configs.cooldownBeforePluginStart;
    	if(countdown>0) 
			Messages.log("Chests will load in "+ countdown + " seconds.");

		// Preserve the established startup timing while moving task ownership into the registry.
		taskRegistry.runLater(STARTUP_TASK, () -> {
			Messages.log("Loading chests...");
			loadChestDefinitions(false, () -> Messages.log("Plugin loaded"));
		}, countdown + 20L);
	}

	public boolean reloadLootChests(Runnable completion, Runnable failure) {
		return reloadLootChests(completion, failure, failure);
	}

	public boolean reloadLootChests(
			Runnable completion,
			Runnable failure,
			Runnable committedActivationFailure) {
		Objects.requireNonNull(completion, "completion");
		Objects.requireNonNull(failure, "failure");
		Objects.requireNonNull(committedActivationFailure, "committedActivationFailure");
		if (pendingReload != null
				|| reloadFileOperation != null
				|| chestWorkInProgress
				|| configFiles.isReloadInProgress()
				|| taskRegistry.hasTask(CHEST_RELOAD_DEACTIVATE_TASK)
				|| taskRegistry.hasTask(CHEST_RELOAD_ROLLBACK_TASK)) {
			return false;
		}

		CompletableFuture<LootChestFiles.ReloadRawBundle> readOperation;
		try {
			readOperation = configFiles.beginReloadAsync();
			uiHandler.closeAll(ChestUi.CloseReason.RELOAD);
		} catch (RuntimeException | LinkageError exception) {
			configFiles.cancelReloadGate();
			getLogger().log(Level.SEVERE, "LootChest reload preflight could not start.", exception);
			return false;
		}

		chestWorkInProgress = true;
		reloadFileOperation = readOperation;
		completeReloadFileOperation(readOperation, (rawBundle, readFailure) -> {
			if (readFailure != null) {
				configFiles.cancelReloadGate();
				chestWorkInProgress = false;
				getLogger().log(
						Level.SEVERE,
						"Could not read LootChest reload inputs; the running state was retained.",
						unwrapCompletionFailure(readFailure));
				failure.run();
				return;
			}

			LootChestFiles.PreparedReload preparedReload;
			try {
				snapshottingReloadData = true;
				preparedReload = configFiles.prepareReload(
						rawBundle,
						LootChestUtils::writeAllChestsToMemory);
			} catch (RuntimeException | LinkageError exception) {
				configFiles.cancelReloadGate();
				chestWorkInProgress = false;
				getLogger().log(
						Level.SEVERE,
						"LootChest reload decoding failed; the running state was retained.",
						exception);
				failure.run();
				return;
			} finally {
				snapshottingReloadData = false;
			}
			pendingReload = preparedReload;

			if (!canDeactivateLoadedChestsForReload()) {
				getLogger().severe("LootChest reload was aborted because every previous container "
						+ "could not be removed safely.");
				abortPreparedReloadAsync(
						preparedReload,
						List.of(),
						new IdentityHashMap<>(),
						false,
						failure);
				return;
			}

			List<ReloadRuntimeState> runtimeStates;
			try {
				deleteListener.clearTrackedInventories();
				runtimeStates = captureReloadRuntimeStates();
			} catch (RuntimeException | LinkageError exception) {
				getLogger().log(Level.SEVERE, "Could not snapshot runtime reload ownership.", exception);
				abortPreparedReloadAsync(
						preparedReload,
						List.of(),
						new IdentityHashMap<>(),
						false,
						failure);
				return;
			}

			reloadRuntimeStates = runtimeStates;
			reloadTouched = new IdentityHashMap<>();
			reloadRuntimeSuspended = true;
			taskRegistry.cancelAll();
			beginReloadTeardown(
					preparedReload,
					runtimeStates,
					completion,
					failure,
					committedActivationFailure);
		});
		return true;
	}

	private void beginReloadTeardown(
			LootChestFiles.PreparedReload preparedReload,
			List<ReloadRuntimeState> runtimeStates,
			Runnable completion,
			Runnable failure,
			Runnable committedActivationFailure) {
		IdentityHashMap<Lootchest, ReloadPhysicalSnapshot> touched = reloadTouched;
		boolean[] failed = {false};
		taskRegistry.runBatched(
				CHEST_RELOAD_DEACTIVATE_TASK,
				runtimeStates,
				configs.chestsPerTick,
				runtimeState -> {
					if (failed[0]) {
						return;
					}
					Lootchest chest = runtimeState.chest();
					try {
						ReloadPhysicalSnapshot snapshot = captureReloadPhysicalSnapshot(chest);
						touched.put(chest, snapshot);
						chest.despawnForReload();
					} catch (RuntimeException | LinkageError exception) {
						ReloadPhysicalSnapshot captured = touched.get(chest);
						if (captured != null && restoreImmediatelyAfterFailedDespawn(captured)) {
							touched.remove(chest);
						}
						failed[0] = true;
						taskRegistry.cancel(CHEST_RELOAD_DEACTIVATE_TASK);
						getLogger().log(
								Level.SEVERE,
								"Could not remove the previous physical container for LootChest '"
										+ printableChestName(chest.getName()) + "' during reload.",
								exception);
						abortPreparedReloadAsync(
								preparedReload,
								runtimeStates,
								touched,
								true,
								failure);
					}
				},
				() -> {
					if (!failed[0]) {
						commitPreparedReloadAsync(
								preparedReload,
								runtimeStates,
								touched,
								completion,
								failure,
								committedActivationFailure);
					}
				});
	}

	private void commitPreparedReloadAsync(
			LootChestFiles.PreparedReload preparedReload,
			List<ReloadRuntimeState> runtimeStates,
			IdentityHashMap<Lootchest, ReloadPhysicalSnapshot> touched,
			Runnable completion,
			Runnable failure,
			Runnable committedActivationFailure) {
		CompletableFuture<Void> commitOperation;
		try {
			commitOperation = configFiles.commitReloadAsync(preparedReload);
		} catch (RuntimeException exception) {
			getLogger().log(Level.SEVERE, "Could not start the prepared reload commit.", exception);
			abortPreparedReloadAsync(preparedReload, runtimeStates, touched, true, failure);
			return;
		}

		reloadFileOperation = commitOperation;
		completeReloadFileOperation(commitOperation, (ignored, commitFailure) -> {
			if (commitFailure != null) {
				getLogger().log(
						Level.SEVERE,
						"Could not commit the prepared LootChest reload; restoring runtime ownership.",
						unwrapCompletionFailure(commitFailure));
				abortPreparedReloadAsync(preparedReload, runtimeStates, touched, true, failure);
				return;
			}

			try {
				configFiles.publishReload(preparedReload);
				pendingReload = null;
				clearReloadRollbackState();
				finishCommittedReload(completion, committedActivationFailure);
			} catch (RuntimeException | LinkageError exception) {
				pendingReload = null;
				getLogger().log(
						Level.SEVERE,
						"The candidate data was committed but could not be published or activated.",
						exception);
				taskRegistry.cancelAll();
				clearRuntimeOwnershipAfterTeardown();
				chestWorkInProgress = false;
				committedActivationFailure.run();
			}
		});
	}

	private boolean canDeactivateLoadedChestsForReload() {
		for (Lootchest chest : lootChest.values()) {
			try {
				Location actualLocation = chest.getActualLocation();
				if (!LootChestUtils.isWorldLoaded(chest.getWorld())
						|| actualLocation == null
						|| actualLocation.getWorld() == null
						|| Bukkit.getWorld(actualLocation.getWorld().getUID()) == null) {
					getLogger().severe("Cannot safely reload while the current world for LootChest '"
							+ printableChestName(chest.getName()) + "' is unavailable.");
					return false;
				}
			} catch (RuntimeException | LinkageError exception) {
				getLogger().log(
						Level.SEVERE,
						"Could not validate the current container for LootChest '"
								+ printableChestName(chest.getName()) + "' before reload.",
						exception);
				return false;
			}
		}
		return true;
	}

	private void finishCommittedReload(Runnable completion, Runnable failure) {
		try {
			deleteListener.clearTrackedInventories();
			taskRegistry.cancelAll();
			clearRuntimeOwnershipAfterTeardown();

			setConfigs(Config.getInstance(configFiles.getConfig()));
			startCmiHolograms();
			reloadParticleCatalog();
			if (configs.partEnable) {
				startParticles();
			}

			Messages.log("Loading chests...");
			loadChestDefinitions(true, completion);
		} catch (RuntimeException | LinkageError exception) {
			getLogger().log(
					Level.SEVERE,
					"The candidate files were committed, but runtime reload activation failed. "
							+ "All old runtime ownership is being cleared so the failure remains fail-closed.",
					exception);
			taskRegistry.cancelAll();
			clearRuntimeOwnershipAfterTeardown();
			unavailableChestDefinitions.clear();
			failedChestDefinitions.clear();
			String detail = "reload initialization failed: " + exception.getClass().getSimpleName()
					+ ": " + printableFailureDetail(exception.getMessage());
			configFiles.getLoadableChestNames().forEach(name -> failedChestDefinitions.put(name, detail));
			chestWorkInProgress = false;
			failure.run();
		}
	}

	private void clearRuntimeOwnershipAfterTeardown() {
		for (Lootchest chest : new ArrayList<>(lootChest.values())) {
			try {
				chest.getHologram().remove();
			} catch (RuntimeException | LinkageError exception) {
				getLogger().log(
						Level.WARNING,
						"Could not remove a LootChest hologram while clearing reload state.",
						exception);
			}
		}
		lootChest.clear();
		lootChestLocationIndex.clear();
		locationIndexMismatchWarnings.clear();
		part.clear();
		protection.clear();
	}

	private void abortPreparedReloadAsync(
			LootChestFiles.PreparedReload preparedReload,
			List<ReloadRuntimeState> runtimeStates,
			IdentityHashMap<Lootchest, ReloadPhysicalSnapshot> touched,
			boolean runtimeSuspended,
			Runnable failure) {
		CompletableFuture<java.nio.file.Path> abortOperation;
		try {
			abortOperation = configFiles.abortReloadAsync(preparedReload);
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.SEVERE,
					"Could not start preservation of the uncommitted data.yml candidate.",
					exception);
			finishAbortedRuntime(runtimeStates, touched, runtimeSuspended, failure);
			return;
		}

		reloadFileOperation = abortOperation;
		completeReloadFileOperation(abortOperation, (preserved, abortFailure) -> {
			if (abortFailure != null) {
				getLogger().log(
						Level.SEVERE,
						"Could not preserve the uncommitted data.yml candidate after reload was aborted. "
								+ "Further data saves and reloads remain blocked.",
						unwrapCompletionFailure(abortFailure));
			} else {
				try {
					configFiles.finishAbortReload(preparedReload);
					if (pendingReload == preparedReload) {
						pendingReload = null;
					}
				} catch (RuntimeException exception) {
					getLogger().log(Level.SEVERE, "Could not finish the reload abort transaction.", exception);
				}
			}
			finishAbortedRuntime(runtimeStates, touched, runtimeSuspended, failure);
		});
	}

	private void finishAbortedRuntime(
			List<ReloadRuntimeState> runtimeStates,
			IdentityHashMap<Lootchest, ReloadPhysicalSnapshot> touched,
			boolean runtimeSuspended,
			Runnable failure) {
		if (!runtimeSuspended) {
			chestWorkInProgress = false;
			failure.run();
			return;
		}

		taskRegistry.runBatched(
				CHEST_RELOAD_ROLLBACK_TASK,
				runtimeStates,
				configs.chestsPerTick,
				runtimeState -> restoreReloadRuntimeState(runtimeState, touched),
				() -> {
					if (configs.partEnable) {
						startParticles();
					}
					chestWorkInProgress = false;
					clearReloadRollbackState();
					failure.run();
				});
	}

	private List<ReloadRuntimeState> captureReloadRuntimeStates() {
		List<ReloadRuntimeState> states = new ArrayList<>(lootChest.size());
		for (Lootchest chest : lootChest.values()) {
			Location particleLocation = chest.getParticleLocation().clone();
			states.add(new ReloadRuntimeState(
					chest,
					particleLocation,
					part.containsKey(particleLocation),
					part.get(particleLocation),
					chest.getHologram().isActive(),
					LootChestUtils.hasRespawnTask(chest)));
		}
		return states;
	}

	private ReloadPhysicalSnapshot captureReloadPhysicalSnapshot(Lootchest chest) {
		Location location = Objects.requireNonNull(
				chest.getActualLocation(),
				"LootChest actual location").clone();
		Block block = location.getBlock();
		BlockState physicalState = null;
		if (chest.isGoodType(block)) {
			physicalState = block.getState(true);
			if (!(physicalState instanceof InventoryHolder inventoryHolder)) {
				throw new IllegalStateException("Matching LootChest block has no inventory state");
			}
			if (inventoryHolder.getInventory().getSize() != chest.getInv().getSize()) {
				throw new IllegalStateException(
						"Paired or unexpected-size containers cannot be transactionally reloaded");
			}
		}
		return new ReloadPhysicalSnapshot(chest, location, physicalState);
	}

	private void restoreReloadRuntimeState(
			ReloadRuntimeState runtimeState,
			IdentityHashMap<Lootchest, ReloadPhysicalSnapshot> touched) {
		Lootchest chest = runtimeState.chest();
		try {
			boolean physicalRestored = true;
			if (touched.containsKey(chest)) {
				physicalRestored = restoreReloadPhysicalSnapshot(touched.get(chest));
			}

			part.remove(runtimeState.particleLocation());
			if (!physicalRestored) {
				chest.getHologram().remove();
				LootChestUtils.cancelReSpawn(chest);
				failedChestDefinitions.put(
						chest.getName(),
						"reload rollback could not safely restore the previous physical container");
				return;
			}

			if (runtimeState.particleTracked()) {
				part.put(runtimeState.particleLocation(), runtimeState.trackedParticle());
			}
			if (runtimeState.hologramActive()) {
				chest.getHologram().setLoc(chest.getActualLocation());
			} else if (!runtimeState.hologramActive()) {
				chest.getHologram().remove();
			}
			LootChestUtils.cancelReSpawn(chest);
			if (runtimeState.respawnTaskActive()) {
				LootChestUtils.scheduleReSpawn(chest);
			}
		} catch (RuntimeException | LinkageError exception) {
			failedChestDefinitions.put(
					chest.getName(),
					"reload rollback failed: " + exception.getClass().getSimpleName());
			getLogger().log(
					Level.SEVERE,
					"Could not restore LootChest '" + printableChestName(chest.getName())
							+ "' after the reload was aborted; it remains inactive.",
					exception);
		}
	}

	private boolean restoreReloadPhysicalSnapshot(ReloadPhysicalSnapshot snapshot) {
		if (snapshot.physicalState() == null) {
			return true;
		}
		Block currentBlock = snapshot.location().getBlock();
		if (currentBlock.getType() != Material.AIR) {
			getLogger().severe("Refused to overwrite a non-air block while rolling back LootChest '"
					+ printableChestName(snapshot.chest().getName()) + "'.");
			return false;
		}
		if (!snapshot.physicalState().update(true, false)) {
			getLogger().severe("Paper could not restore the captured block state for LootChest '"
					+ printableChestName(snapshot.chest().getName()) + "'.");
			return false;
		}
		return snapshot.chest().isGoodType(snapshot.location().getBlock());
	}

	private boolean restoreImmediatelyAfterFailedDespawn(ReloadPhysicalSnapshot snapshot) {
		if (snapshot.physicalState() == null) {
			return true;
		}
		Block currentBlock = snapshot.location().getBlock();
		if (currentBlock.getType() != Material.AIR
				&& currentBlock.getType() != snapshot.physicalState().getType()) {
			return false;
		}
		try {
			return snapshot.physicalState().update(true, false)
					&& snapshot.chest().isGoodType(snapshot.location().getBlock());
		} catch (RuntimeException | LinkageError restorationFailure) {
			getLogger().log(
					Level.SEVERE,
					"Immediate rollback failed for LootChest '"
							+ printableChestName(snapshot.chest().getName()) + "'.",
					restorationFailure);
			return false;
		}
	}

	private <T> void completeReloadFileOperation(
			CompletableFuture<T> operation,
			java.util.function.BiConsumer<T, Throwable> continuation) {
		operation.whenComplete((value, operationFailure) -> {
			try {
				Bukkit.getScheduler().runTask(this, () -> {
					if (!isEnabled() || reloadFileOperation != operation) {
						return;
					}
					reloadFileOperation = null;
					continuation.accept(value, operationFailure);
				});
			} catch (RuntimeException schedulingFailure) {
				if (isEnabled()) {
					getLogger().log(
							Level.SEVERE,
							"Could not return a reload file operation to the server thread.",
							schedulingFailure);
				}
			}
		});
	}

	private Throwable unwrapCompletionFailure(Throwable failure) {
		Throwable current = failure;
		while ((current instanceof java.util.concurrent.CompletionException
				|| current instanceof java.util.concurrent.ExecutionException)
				&& current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	private record ReloadRuntimeState(
			Lootchest chest,
			Location particleLocation,
			boolean particleTracked,
			Particle trackedParticle,
			boolean hologramActive,
			boolean respawnTaskActive) {
	}

	private record ReloadPhysicalSnapshot(
			Lootchest chest,
			Location location,
			BlockState physicalState) {
	}

	public boolean runBatchedChestOperation(
			Collection<Lootchest> chests,
			Consumer<Lootchest> operation,
			Runnable completion
	) {
		if (chestWorkInProgress || taskRegistry.hasTask(CHEST_BULK_TASK)) {
			return false;
		}
		chestWorkInProgress = true;
		taskRegistry.runBatched(
				CHEST_BULK_TASK,
				new ArrayList<>(chests),
				configs.chestsPerTick,
				operation,
				() -> {
					chestWorkInProgress = false;
					completion.run();
				});
		return true;
	}

	private void loadChestDefinitions(boolean forceSpawn, Runnable completion) {
		chestWorkInProgress = true;
		long startedAt = System.currentTimeMillis();
		List<String> chestNames = new ArrayList<>(configFiles.getLoadableChestNames());
		unavailableChestDefinitions.clear();
		failedChestDefinitions.clear();

		taskRegistry.runBatched(
				CHEST_LOAD_TASK,
				chestNames,
				configs.chestsPerTick,
				chestName -> {
					ChestDefinitionLoadResult result;
					try {
						result = loadChestDefinition(chestName);
					} catch (RuntimeException | LinkageError exception) {
						result = ChestDefinitionLoadResult.FAILED;
						recordDefinitionFailure(chestName, "load", exception);
						containFailedActivation(chestName);
						getLogger().log(
								Level.SEVERE,
								"Could not activate saved LootChest '" + printableChestName(chestName)
										+ "'. Its definition remains preserved and inactive.",
								exception);
					}
					if (result == ChestDefinitionLoadResult.WORLD_UNAVAILABLE) {
						unavailableChestDefinitions.add(chestName);
					}
				},
				() -> {
					Messages.log("Starting LootChest timers in batches...");
					taskRegistry.runBatched(
							CHEST_SPAWN_TASK,
							new ArrayList<>(lootChest.values()),
							configs.chestsPerTick,
							chest -> spawnLoadedChestSafely(chest, forceSpawn),
							() -> {
								logDefinitionLoadSummary(startedAt);
								chestWorkInProgress = false;
								completion.run();
							});
				});
	}

	private ChestDefinitionLoadResult loadChestDefinition(String chestName) {
		String worldName = configFiles.getData().getString(DATA_CHEST_PATH + chestName + ".position.world");
		String randomWorldName = worldName;
		boolean savedRandomPosition = configFiles.getData().getInt(
				DATA_CHEST_PATH + chestName + ".randomradius") > 0
				&& configFiles.getData().isSet(DATA_CHEST_PATH + chestName + ".randomPosition.x");
		if (savedRandomPosition) {
			randomWorldName = configFiles.getData().getString(DATA_CHEST_PATH + chestName + ".randomPosition.world");
		}
		if (worldName != null
				&& LootChestUtils.isWorldLoaded(randomWorldName)
				&& LootChestUtils.isWorldLoaded(worldName)) {
			World positionWorld = Bukkit.getWorld(worldName);
			World randomPositionWorld = Bukkit.getWorld(randomWorldName);
			double positionY = configFiles.getData().getDouble(
					DATA_CHEST_PATH + chestName + ".position.y");
			if (!isBlockYWithinWorld(positionWorld, positionY)) {
				String detail = "position.y is outside the loaded world's build height";
				recordDefinitionFailure(
						chestName,
						"load",
						new IllegalArgumentException(detail));
				getLogger().warning("Could not activate saved LootChest '"
						+ printableChestName(chestName) + "': " + detail + ".");
				return ChestDefinitionLoadResult.FAILED;
			}
			if (savedRandomPosition) {
				double randomY = configFiles.getData().getDouble(
						DATA_CHEST_PATH + chestName + ".randomPosition.y");
				if (!isBlockYWithinWorld(randomPositionWorld, randomY)) {
					String detail = "randomPosition.y is outside the loaded world's build height";
					recordDefinitionFailure(
							chestName,
							"load",
							new IllegalArgumentException(detail));
					getLogger().warning("Could not activate saved LootChest '"
							+ printableChestName(chestName) + "': " + detail + ".");
					return ChestDefinitionLoadResult.FAILED;
				}
			}
			Lootchest chest = new Lootchest(chestName);
			lootChest.put(chestName, chest);
			trackLootChestLocation(chest);
			return ChestDefinitionLoadResult.LOADED;
		}
		String unavailableWorld = !LootChestUtils.isWorldLoaded(worldName) ? worldName : randomWorldName;
		getLogger().warning("Could not load LootChest '" + printableChestName(chestName)
				+ "': world '" + printableText(Objects.toString(unavailableWorld, "null"), 120)
				+ "' is not loaded.");
		return ChestDefinitionLoadResult.WORLD_UNAVAILABLE;
	}

	private boolean isBlockYWithinWorld(World world, double y) {
		if (world == null || !Double.isFinite(y)) {
			return false;
		}
		double blockY = Math.floor(y);
		return blockY >= world.getMinHeight() && blockY < world.getMaxHeight();
	}

	private void spawnLoadedChestSafely(Lootchest chest, boolean forceSpawn) {
		try {
			spawnLoadedChest(chest, forceSpawn);
		} catch (RuntimeException | LinkageError exception) {
			recordDefinitionFailure(chest.getName(), "spawn", exception);
			boolean contained = containFailedActivation(chest.getName());
			getLogger().log(
					Level.SEVERE,
					"Could not spawn saved LootChest '" + printableChestName(chest.getName()) + "'. "
							+ (contained
									? "Its runtime state was removed and its saved definition remains preserved."
									: "Cleanup also failed, so runtime ownership was retained for safety."),
					exception);
		}
	}

	private boolean containFailedActivation(String chestName) {
		Lootchest chest = lootChest.get(chestName);
		if (chest == null) {
			return true;
		}

		boolean contained = true;
		try {
			LootChestUtils.cancelReSpawn(chest);
			chest.despawn();
		} catch (RuntimeException | LinkageError cleanupFailure) {
			contained = false;
			getLogger().log(
					Level.SEVERE,
					"Could not fully contain failed LootChest '" + printableChestName(chestName)
							+ "'; keeping it registered rather than leaving an unmanaged container.",
					cleanupFailure);
		}

		if (contained) {
			lootChest.remove(chestName, chest);
			untrackLootChestLocation(chest);
		} else {
			trackLootChestLocation(chest);
		}
		return contained;
	}

	private void recordDefinitionFailure(String chestName, String phase, Throwable exception) {
		failedChestDefinitions.put(
				chestName,
				phase + " failed: " + exception.getClass().getSimpleName() + ": "
						+ printableFailureDetail(exception.getMessage()));
	}

	private void logDefinitionLoadSummary(long startedAt) {
		int rejected = configFiles.getRejectedChestDefinitions().size();
		int unavailable = unavailableChestDefinitions.size();
		int failed = failedChestDefinitions.size();
		getLogger().info("Activated " + lootChest.size() + " of "
				+ configFiles.getSavedChestDefinitionCount() + " saved LootChests in "
				+ (System.currentTimeMillis() - startedAt) + " milliseconds; rejected "
				+ rejected + " malformed, deferred " + unavailable
				+ " because a world is unavailable, and failed " + failed + " unexpectedly.");
		if (rejected > 0) {
			getLogger().warning("Rejected definitions remain preserved in data.yml and were not replaced from backup.");
		}
	}

	private void rebuildLootChestLocationIndex() {
		lootChestLocationIndex.clear();
		locationIndexMismatchWarnings.clear();
		lootChest.values().forEach(this::trackLootChestLocation);
		if (configs != null && configs.debug) {
			Messages.log(
					"<#89b4fa>Guarded location index tracks [Indexed] of [Loaded] loaded LootChests; "
							+ "the exact-location scan remains the fallback and debug verifier.",
					"[Indexed]", Integer.toString(lootChestLocationIndex.size()),
					"[Loaded]", Integer.toString(lootChest.size()));
		}
	}

	public void trackLootChestLocation(Lootchest chest) {
		if (chest == null || lootChestLocationIndex == null
				|| lootChest == null || !lootChest.containsValue(chest)) {
			return;
		}
		Location location = chest.getActualLocation();
		if (location == null || location.getWorld() == null) {
			return;
		}
		lootChestLocationIndex.put(
				chest,
				location.getWorld().getUID(),
				location.getBlockX(),
				location.getBlockY(),
				location.getBlockZ());
	}

	public void untrackLootChestLocation(Lootchest chest) {
		if (lootChestLocationIndex != null) {
			lootChestLocationIndex.remove(chest);
		}
	}

	boolean canPersistLootChest(Lootchest chest) {
		if (chest == null || configFiles == null || !configFiles.isInitialized()) {
			return false;
		}
		if (!snapshottingReloadData
				&& (pendingReload != null
				|| reloadFileOperation != null
				|| configFiles.isReloadInProgress())) {
			getLogger().warning("Refused to persist LootChest state while a reload transaction is in progress.");
			return false;
		}

		String name = chest.getName();
		boolean alreadySaved = configFiles.hasSavedChestDefinition(name);
		List<String> nameProblems = alreadySaved
				? configFiles.getSavedChestNameProblems(name)
				: configFiles.getChestNameProblems(name);
		if (!nameProblems.isEmpty()) {
			getLogger().warning("Refused to persist LootChest with an unsafe name ("
					+ String.join("; ", nameProblems) + ").");
			return false;
		}
		if (isInactiveSavedChestDefinition(name)) {
			getLogger().warning("Refused to overwrite inactive saved LootChest definition '"
					+ printableChestName(name) + "'.");
			return false;
		}

		Lootchest registeredByName = lootChest == null ? null : lootChest.get(name);
		if (alreadySaved && registeredByName != chest) {
			getLogger().warning("Refused to overwrite reserved saved LootChest definition '"
					+ printableChestName(name) + "'.");
			return false;
		}
		if (lootChest != null && lootChest.containsValue(chest) && registeredByName != chest) {
			getLogger().warning("Refused to persist a registered LootChest under a different name.");
			return false;
		}
		try {
			Location position = chest.getPosition();
			Location actualPosition = chest.getActualLocation();
			if (chest.getType() == null || !Mat.isLootChestMaterial(chest.getType())
					|| chest.getRadius() < 0
					|| chest.getTime() < Integer.MIN_VALUE
					|| chest.getTime() > Integer.MAX_VALUE
					|| chest.getMaxFilledSlots() == null
					|| !isSafeRuntimeLocation(position)
					|| !isSafeRuntimeLocation(actualPosition)) {
				getLogger().warning("Refused to persist LootChest '" + printableChestName(name)
						+ "' because its runtime state is incomplete or unsafe.");
				return false;
			}
		} catch (RuntimeException exception) {
			getLogger().log(
					Level.WARNING,
					"Refused to persist LootChest '" + printableChestName(name)
							+ "' because its runtime state could not be inspected.",
					exception);
			return false;
		}
		return true;
	}

	boolean canPersistAllLootChests() {
		return snapshottingReloadData
				|| (pendingReload == null
				&& reloadFileOperation == null
				&& !configFiles.isReloadInProgress());
	}

	private boolean isSafeRuntimeLocation(Location location) {
		if (location == null
				|| location.getWorld() == null
				|| !Double.isFinite(location.getX())
				|| !Double.isFinite(location.getY())
				|| !Double.isFinite(location.getZ())
				|| location.getX() < -30_000_000D
				|| location.getX() >= 30_000_000D
				|| location.getZ() < -30_000_000D
				|| location.getZ() >= 30_000_000D) {
			return false;
		}
		return isBlockYWithinWorld(location.getWorld(), location.getY());
	}

	public boolean hasInactiveSavedChestDefinitions() {
		return !configFiles.getRejectedChestDefinitions().isEmpty()
				|| !unavailableChestDefinitions.isEmpty()
				|| !failedChestDefinitions.isEmpty();
	}

	private boolean isInactiveSavedChestDefinition(String chestName) {
		return configFiles.getRejectedChestDefinitions().containsKey(chestName)
				|| unavailableChestDefinitions.contains(chestName)
				|| failedChestDefinitions.containsKey(chestName);
	}

	public Lootchest findLootChest(Location location) {
		if (location == null || lootChest == null) {
			return null;
		}

		if (lootChestLocationIndex == null || location.getWorld() == null) {
			return scanLootChest(location);
		}

		boolean verifyIndexedHit = configs != null && configs.debug;
		BlockLocationIndex.Resolution<Lootchest> resolution = lootChestLocationIndex.resolve(
				location.getWorld().getUID(),
				location.getBlockX(),
				location.getBlockY(),
				location.getBlockZ(),
				chest -> isRegisteredAt(chest, location),
				() -> scanLootChest(location),
				verifyIndexedHit);
		if (verifyIndexedHit && resolution.mismatch()) {
			warnLocationIndexMismatch(location, resolution.indexedValue(), resolution.value());
		}
		return resolution.value();
	}

	private Lootchest scanLootChest(Location location) {
		for (Lootchest chest : lootChest.values()) {
			Location chestLocation = chest.getActualLocation();
			if (chestLocation != null && chestLocation.equals(location)) {
				return chest;
			}
		}
		return null;
	}

	private boolean isRegisteredAt(Lootchest chest, Location location) {
		return chest != null
				&& lootChest.get(chest.getName()) == chest
				&& location.equals(chest.getActualLocation());
	}

	private void warnLocationIndexMismatch(Location location, Lootchest indexedChest, Lootchest scannedChest) {
		String warningKey = location.getWorld().getUID()
				+ ":" + location.getBlockX()
				+ ":" + location.getBlockY()
				+ ":" + location.getBlockZ();
		if (locationIndexMismatchWarnings.add(warningKey)) {
			getLogger().warning(
					"Guarded location index mismatch at " + warningKey
							+ ": scan=" + chestName(scannedChest)
							+ ", index=" + chestName(indexedChest)
							+ ". The scan result was used and the index was repaired.");
		}
	}

	private String chestName(Lootchest chest) {
		return chest == null ? "none" : chest.getName();
	}

	private String printableChestName(String chestName) {
		return printableText(Objects.toString(chestName, "null"), 120);
	}

	private String printableFailureDetail(String detail) {
		return printableText(Objects.toString(detail, "no detail"), 240);
	}

	private String printableText(String text, int maximumLength) {
		StringBuilder printable = new StringBuilder();
		text.codePoints().forEach(character -> {
			if (Character.isISOControl(character)) {
				printable.append('?');
			} else if (printable.length() < maximumLength) {
				printable.appendCodePoint(character);
			}
		});
		if (text.length() > maximumLength) {
			printable.append("...");
		}
		return printable.toString();
	}

	private void spawnLoadedChest(Lootchest chest, boolean forceSpawn) {
		if (forceSpawn) {
			chest.spawn(true);
			return;
		}
		if (!chest.spawn(false)) {
			LootChestUtils.scheduleReSpawn(chest);
			chest.reactivateEffects();
		}
	}
	
	
	/**
	* In many versions, I add some text a config option.
	* These lines are done to update config and language files without erasing options that are already set
	*/
	private void updateOldConfig() {
		CompatibilityMigrations.migrateConfig(configFiles.getConfig());
		CompatibilityMigrations.migrateLanguage(configFiles.getLang());
		boolean savedChestDataChanged =
				CompatibilityMigrations.migrateSavedChestData(
						configFiles.getData(),
						configFiles.getLoadableChestNames());
		configFiles.setConfig("spawn_on_non_solid_blocks", false);
		configFiles.setConfig("Minimum_Height_For_Random_Spawn", 0);
		configFiles.setConfig("Max_Height_For_Random_Spawn", 200);
		configFiles.setConfig("Max_Filled_Slots_By_Default", 0);
		configFiles.setConfig("SaveDataFileDuringReload", false);
		configFiles.setConfig("respawn_notify.respawn_all_with_command_in_world.enabled", true);
		configFiles.setConfig("respawn_notify.respawn_all_with_command_in_world.message", "<#a6e3a1>All LootChests were force-respawned in <#89dceb>[World]<#a6e3a1>.");
		configFiles.setConfig("respawn_notify.Minimum_Number_Of_Players_For_Natural_Spawning", 0);
		configFiles.setConfig("Particles.fallback_particle", "FLAME");
		configFiles.setConfig("Scheduler.Chests_Per_Tick", 1);
		configFiles.setLang("Menu.particles.selected", "<#a6e3a1>Currently selected");
		configFiles.setLang("info.title", "<#cba6f7><bold>Lootbox</bold> <#6c7086>v[Version]");
		configFiles.setLang("info.release", "<#a6e3a1>Build <#89dceb>[Build] <#6c7086>| <#bac2de>[Artifact]");
		configFiles.setLang("info.source", "<#a6e3a1>Source <#89dceb>[Source]");
		configFiles.setLang("info.target", "<#a6e3a1>Targets <#89dceb>Paper [Paper] <#6c7086>(API [PaperApi]) <#a6e3a1>and <#89dceb>Java [Java]");
		configFiles.setLang("info.paper_release", "<#a6e3a1>Paper release <#89dceb>[Paper] build [PaperBuild] [PaperChannel]");
		configFiles.setLang("info.holograms", "<#a6e3a1>Holograms <#89dceb>[Holograms]");
		configFiles.setLang("info.introduction", "<#bac2de>Discover repeatable loot containers with rewards configured for 1MoreBlock.");
		configFiles.setLang("info.commands", "<#a6e3a1>Start with <#89dceb>/lc locate <#a6e3a1>when your rank grants access, or use <#89dceb>/lc help<#a6e3a1>.");
		configFiles.setLang("info.documentation", "<click:open_url:'https://docs.1moreblock.com/custom-server-plugins/lootbox/'><hover:show_text:'Open the Lootbox guide'><#89dceb><underlined>docs.1moreblock.com/custom-server-plugins/lootbox/</underlined></#89dceb></hover></click>");
		configFiles.setLang("PluginReloadedWithIssues", "<#f9e2af>Reload completed with inactive saved definitions: <#f38ba8>[Rejected] rejected<#f9e2af>, <#f38ba8>[Deferred] world-deferred<#f9e2af>, and <#f38ba8>[Failed] failed<#f9e2af>. Run <#89dceb>/lc audit<#f9e2af>.");
		configFiles.setLang("PluginReloadFailed", "<#f38ba8>Reload was aborted before candidate publication. Check the server log and run <#f9e2af>/lc audit<#f38ba8> before retrying.");
		configFiles.setLang("PluginReloadActivationFailed", "<#f38ba8>The candidate files were committed, but runtime activation failed and all LootChests were left inactive. Run <#f9e2af>/lc audit<#f38ba8>, inspect the log, then retry or restart.");
		configFiles.setLang("audit.title", "<#cba6f7><bold>Lootbox lifecycle audit</bold>");
		configFiles.setLang("audit.summary", "<#a6e3a1>Loaded <#89dceb>[Total] <#bac2de>| <#a6e3a1>present <#89dceb>[Present] <#bac2de>| <#a6e3a1>absent <#89dceb>[Absent] <#bac2de>| <#a6e3a1>wrong <#89dceb>[Wrong] <#bac2de>| <#a6e3a1>unavailable <#89dceb>[Unavailable] <#bac2de>| <#a6e3a1>issues <#89dceb>[Issues]");
		configFiles.setLang("audit.index", "<#a6e3a1>Location index <#89dceb>[Indexed]/[Total]");
		configFiles.setLang("audit.definitions", "<#a6e3a1>Saved definitions <#89dceb>[Saved] <#bac2de>| <#a6e3a1>loadable <#89dceb>[Loadable] <#bac2de>| <#a6e3a1>rejected <#89dceb>[Rejected] <#bac2de>| <#a6e3a1>world-deferred <#89dceb>[Deferred] <#bac2de>| <#a6e3a1>activation-failed <#89dceb>[Failed]");
		configFiles.setLang("audit.clean", "<#a6e3a1>No lifecycle inconsistencies were found.");
		configFiles.setLang("audit.finding", "<#f6c177>- <#f38ba8>[Code] <#89dceb>[Chest]<#cdd6f4>: [Detail]");
		configFiles.setLang("audit.definition_finding", "<#f6c177>- <#f38ba8>saved-definition <#89dceb>[Chest]<#cdd6f4>: [Detail]");
		configFiles.setLang("audit.definition_target", "<#cba6f7><bold>Lootbox saved definition:</bold> <#89dceb>[Chest] <#bac2de>([Status])");
		configFiles.setLang("audit.definition_preserved", "<#f9e2af>The saved definition remains preserved in data.yml and was not replaced from backup.");
		configFiles.setLang("audit.truncated", "<#f9e2af>[Remaining] additional findings were omitted to keep the report readable.");
		configFiles.setLang("audit.read_only", "<#bac2de>Read-only audit complete; no chest, display, task, configuration, or saved data was changed.");
		configFiles.setLang("audit.click_to_tp", "<#a6e3a1>Click to teleport to <#89dceb>[Chest]");
		configFiles.setLang("audit.target_title", "<#cba6f7><bold>Lootbox audit:</bold> <#89dceb>[Chest]");
		configFiles.setLang("audit.target_container", "<#a6e3a1>Container <#bac2de>saved <#89dceb>[Expected]<#bac2de>, live <#89dceb>[Actual]<#bac2de>, state <#89dceb>[State]");
		configFiles.setLang("audit.target_location", "<#a6e3a1>Location <#89dceb>[Location] <#bac2de>| <#a6e3a1>index <#89dceb>[Index]");
		configFiles.setLang("audit.target_effects", "<#a6e3a1>Hologram <#bac2de>expected <#89dceb>[HologramExpected]<#bac2de>, active <#89dceb>[HologramActive] <#bac2de>| <#a6e3a1>particle <#bac2de>expected <#89dceb>[ParticleExpected]<#bac2de>, active <#89dceb>[ParticleActive]");
		configFiles.setLang("audit.target_task", "<#a6e3a1>Respawn task <#bac2de>expected <#89dceb>[TaskExpected]<#bac2de>, active <#89dceb>[TaskActive]");
		configFiles.setLang(MENU_MAIN_TYPE, "<#cba6f7>Select container type");
		configFiles.setLang("notAnInteger", "<#f38ba8>[Number] is not a whole number.");
		configFiles.setLang("invalidChestName", "<#f38ba8>LootChest names cannot be blank or contain spaces, control characters, or periods.");
		configFiles.setLang("blockIsAlreadyLootchest", "<#f38ba8>This block is already registered as a LootChest.");
		configFiles.setLang("editedMaxFilledSlots", "<#a6e3a1>Maximum filled slots updated for <#89dceb>[Chest]<#a6e3a1>.");
		configFiles.setLang("copiedChest", "<#f6c177>Copied <#89dceb>[Chest1] <#f6c177>into <#89dceb>[Chest2]<#f6c177>.");
		configFiles.setLang("NotEnoughPlayers", "<#f38ba8>At least <#f9e2af>[Number] players <#f38ba8>are required to spawn LootChests.");
		configFiles.setLang("ChestDespawned", "<#a6e3a1>Despawned <#89dceb>[Chest]<#a6e3a1>.");
		configFiles.setLang("NoChestAtLocation", "<#f38ba8>That LootChest is already absent.");
		configFiles.setLang("AllChestsDespawned", "<#a6e3a1>All LootChests were despawned.");
		configFiles.setLang("AllChestsDespawnedInWorld", "<#a6e3a1>All LootChests were despawned in <#89dceb>[World]<#a6e3a1>.");
		configFiles.setLang("ChestOperationInProgress", "<#f9e2af>LootChest is still loading or processing another bulk chest command. Please try again in a moment.");
		configFiles.setLang("ListCommandHover", "<#a6e3a1>Click to edit <#89dceb>[Chest]");
		configFiles.setLang("worldDoesntExist", "<#f38ba8>World <#89dceb>[World] <#f38ba8>does not exist.");
		configFiles.setLang("AllChestsReloadedInWorld", "<#a6e3a1>All LootChests were respawned in <#89dceb>[World]<#a6e3a1>.");
		if(!configFiles.getLang().getStringList("help").toString().contains("despawnall")){
			List<String> help = configFiles.getLang().getStringList("help");
			help.add("<#a6e3a1>/lc despawnall <#bac2de>[world] <#6c7086>- Despawn all LootChests");
			configFiles.getLang().set("help", help);
			configFiles.saveLang();
		}
		if(!configFiles.getLang().getStringList("help").toString().contains("copy")){
			List<String> help = configFiles.getLang().getStringList("help");
			help.add("<#a6e3a1>/lc copy <#bac2de>\\<source> \\<destination> <#6c7086>- Copy one LootChest into another");
			configFiles.getLang().set("help", help);
			configFiles.saveLang();
		}
		if(!configFiles.getLang().getStringList("help").toString().contains("maxfilledslots")){
			List<String> help = configFiles.getLang().getStringList("help");
			help.add("<#a6e3a1>/lc maxfilledslots <#bac2de>\\<name> \\<number> <#6c7086>- Limit filled slots");
			configFiles.getLang().set("help", help);
			configFiles.saveLang();
		}
		if(!configFiles.getLang().getStringList("help").toString().contains("/lc info")){
			List<String> help = configFiles.getLang().getStringList("help");
			help.add(2, "<#a6e3a1>/lc info <#6c7086>- About Lootbox and its documentation");
			configFiles.getLang().set("help", help);
			configFiles.saveLang();
		}
		if(!configFiles.getLang().getStringList("help").toString().contains("/lc audit")){
			List<String> help = configFiles.getLang().getStringList("help");
			help.add(Math.min(2, help.size()), "<#a6e3a1>/lc audit <#bac2de>[chest] <#6c7086>- Inspect all Lootboxes or one named Lootbox");
			configFiles.getLang().set("help", help);
			configFiles.saveLang();
		}
		List<String> commandHelp = configFiles.getLang().getStringList("help");
		for (int i = 0; i < commandHelp.size(); i++) {
			if (commandHelp.get(i).contains("/lc settime")) {
				commandHelp.set(i, "<#a6e3a1>/lc settime <#bac2de>\\<name> \\<minutes> <#6c7086>- Set respawn time");
			}
		}
		configFiles.getLang().set("help", commandHelp);
		if(!configFiles.getLang().getStringList("help").toString().contains("despawn ")){
			  List<String> help = configFiles.getLang().getStringList("help");
			  help.add("<#a6e3a1>/lc despawn <#bac2de>\\<name> <#6c7086>- Despawn a LootChest");
			  configFiles.getLang().set("help", help);
			  configFiles.saveLang();
			}
		configFiles.saveLang();
		configFiles.saveConfig();
		if (savedChestDataChanged) {
			configFiles.saveData();
		}

	}
	

	
	/**
	 * Builds the editor choices from payload-free particles exposed by the running Paper API.
	 */
	private void initParticles() {
		particleCatalog = new ParticleCatalog(configs.partFallbackParticle, getLogger()::warning);
		particles.clear();
		particles.putAll(particleCatalog.getSupportedParticles());
		Messages.log("<#a6e3a1>Loaded " + particles.size() + " Paper particles; fallback: "
				+ particleCatalog.getFallback().name() + ".");
	}

	public void reloadParticleCatalog() {
		failedParticles.clear();
		initParticles();
	}


}
