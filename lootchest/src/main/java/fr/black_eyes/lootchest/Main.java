package fr.black_eyes.lootchest;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.*;

import com.Zrips.CMI.CMI;
import fr.black_eyes.lootchest.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;

import fr.black_eyes.lootchest.commands.CommandHandler;
import fr.black_eyes.lootchest.listeners.DeleteListener;
import fr.black_eyes.lootchest.listeners.UiListener;
import fr.black_eyes.lootchest.particles.ParticleCatalog;
import fr.black_eyes.lootchest.ui.UiHandler;
import fr.black_eyes.simpleJavaPlugin.SimpleJavaPlugin;
import lombok.Getter;
import lombok.Setter;

import static fr.black_eyes.lootchest.Constants.DATA_CHEST_PATH;


public class Main extends SimpleJavaPlugin {
	public static final String MENU_MAIN_TYPE = "Menu.main.type";
	public static final String MENU_CHANCES_LORE = "Menu.chances.lore";
	@Getter private final HashMap<Location, Long> protection = new HashMap<>();
	@Getter private final LinkedHashMap<String, Particle> particles = new LinkedHashMap<>();
	@Getter private final HashMap<Location, Particle> part = new HashMap<>();
	@Getter private ParticleCatalog particleCatalog;
	private final Set<Particle> failedParticles = EnumSet.noneOf(Particle.class);
	@Setter public static Config configs;
	@Getter private HashMap<String, Lootchest> lootChest;
	@Getter @Setter private static Main instance;
	@Getter private LootChestUtils utils;
	@Getter private boolean cmiHologramsAvailable;
	@Getter private UiHandler uiHandler;
	private boolean simplePluginStarted;


	@Override
	public void onDisable() {
		if (lootChest != null && cmiHologramsAvailable) {
			lootChest.values().forEach(chest -> chest.getHologram().remove());
		}
		if (simplePluginStarted) {
			super.onDisable();
		}
		if (lootChest != null) {
			LootChestUtils.saveAllChests();
		}
	}
    
    /**
     * Check if bungee is enabled in spigot config
     * @return true if bungee is enabed, else false
     */
    private boolean hasBungee(){
        boolean bungee = org.spigotmc.SpigotConfig.bungee;
        boolean onlineMode = Bukkit.getServer().getOnlineMode();
        return (bungee && !onlineMode);
    }
	
		@Override
		public void onEnable() {
		setInstance(this);

		lootChest = new HashMap<>();
				//In many versions, I add some text a config option. These lines are done to update config and language files without erasing options that are already set
			super.onEnable();
			simplePluginStarted = true;
			if(configFiles.getLang() == null) {
			Messages.log("<#f38ba8>Configuration or data files could not be initialized. LootChest will stop.");
			return;
		}
		Messages.log("config files loaded");
			Messages.log("Server version: " + Bukkit.getMinecraftVersion());
		updateOldConfig();
		configFiles.reloadConfig();
		utils = new LootChestUtils();

		uiHandler = new UiHandler(this);
		registerEvents(uiHandler);
		registerCommands();
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", new BungeeChannel());

        
		
		//load config
		setConfigs(Config.getInstance(configFiles.getConfig()));
		startCmiHolograms();

		//If we enabled bungee broadcast, but we aren't on a bungee server, not any message will show
        if(configs.noteBungeeBroadcast && !hasBungee()){
				Messages.log("<#f38ba8>Bungee broadcasting is enabled in LootChest, but proxy support is disabled in the server configuration.");
				Messages.log("<#f38ba8>Chest spawn messages cannot be delivered across the proxy until it is enabled.");
        	}
 
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
			if (!configs.usehologram) {
			return;
		}
		if (Bukkit.getPluginManager().getPlugin("CMI") == null
				|| !Bukkit.getPluginManager().isPluginEnabled("CMI")) {
			configs.usehologram = false;
			getLogger().warning("CMI is not enabled; LootChest holograms are disabled.");
			return;
		}
		try {
			CMI cmi = CMI.getInstance();
			if (cmi == null || cmi.getHologramManager() == null) {
				configs.usehologram = false;
				getLogger().warning("CMI's hologram manager is unavailable; LootChest holograms are disabled.");
				return;
			}
			cmiHologramsAvailable = true;
				Messages.log("<#a6e3a1>Using CMI holograms: " + cmi.getPluginMeta().getVersion());
		} catch (RuntimeException | LinkageError e) {
			configs.usehologram = false;
			cmiHologramsAvailable = false;
			getLogger().warning("CMI holograms failed to start; LootChest holograms are disabled. "
					+ e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private void registerEvents(UiHandler uiHandler) {
		PluginManager pluginManager = Bukkit.getPluginManager();
		pluginManager.registerEvents(new DeleteListener(), this);
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
		new BukkitRunnable() {
			@Override
			public void run() {
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
			}
		}.runTaskTimer(this, 0, configs.partRespawnTicks);
	}
    		
	/**
	 * Loads all chests asynchronously
	 */
	@SuppressWarnings("deprecation") // Scheduler modernization is tracked in TODO item 2.
	private void loadChests() {
		long countdown = configs.cooldownBeforePluginStart;
    	if(countdown>0) 
			Messages.log("Chests will load in "+ countdown + " seconds.");
    	
        this.getServer().getScheduler().runTaskLater(this, () -> {
            Messages.log("Loading chests...");
            long current = (new Timestamp(System.currentTimeMillis())).getTime();
            for(String keys : Objects.requireNonNull(configFiles.getData().getConfigurationSection("chests")).getKeys(false)) {
                String name = configFiles.getData().getString(DATA_CHEST_PATH + keys + ".position.world");
                String randomName = name;
                if( configFiles.getData().getInt(DATA_CHEST_PATH + keys + ".randomradius")>0) {
                    randomName = configFiles.getData().getString(DATA_CHEST_PATH + keys + ".randomPosition.world");
                }
                if(name != null && LootChestUtils.isWorldLoaded(randomName) && LootChestUtils.isWorldLoaded(name)) {
                    getLootChest().put(keys, new Lootchest(keys));
                }
                else {
					Messages.log("<#f38ba8>Could not load LootChest " + keys + ": world " + configFiles.getData().getString(DATA_CHEST_PATH + keys + ".position.world") + " is not loaded.");
                }
            }
            
            Messages.log("Loaded "+lootChest.size() + " Lootchests in "+((new Timestamp(System.currentTimeMillis())).getTime()-current) + " miliseconds");
            Messages.log("Starting LootChest timers asynchronously...");
            for (final Lootchest lc : lootChest.values()) {
                Bukkit.getScheduler().scheduleAsyncDelayedTask(instance, () ->
                        Bukkit.getScheduler().scheduleSyncDelayedTask(instance, () -> {
                            if (!lc.spawn(false)) {
                                LootChestUtils.scheduleReSpawn(lc);
                                lc.reactivateEffects();
                            }
                        }, 0L)
                        , 5L);
            }
            Messages.log("Plugin loaded");
                }, countdown+20);
	}
	
	
	/**
	* In many versions, I add some text a config option.
	* These lines are done to update config and language files without erasing options that are already set
	*/
	private void updateOldConfig() {
		// hotfix
		// in chances.lore, replace all % by nothing
		if(Objects.requireNonNull(configFiles.getLang().getString(MENU_CHANCES_LORE)).contains("%")) {
			String lore = configFiles.getLang().getString(MENU_CHANCES_LORE);
			if (lore != null) {
				lore = lore.replace("%", "");
			}
			configFiles.getLang().set(MENU_CHANCES_LORE, lore);
		}
		if(configFiles.getConfig().getInt("Particles.respawn_ticks") == 5){
			configFiles.getConfig().set("Particles.respawn_ticks", 20);
		}
		if(configFiles.getConfig().isSet("RemoveChestAfterFirstOpenning")){
			boolean remove = configFiles.getConfig().getBoolean("RemoveChestAfterFirstOpenning");
			configFiles.getConfig().set("RemoveChestAfterFirstOpenning", null);
			configFiles.getConfig().set("RemoveChestAfterFirstOpening", remove);
		}
		configFiles.setConfig("spawn_on_non_solid_blocks", false);
		configFiles.setConfig("Minimum_Height_For_Random_Spawn", 0);
		configFiles.setConfig("Max_Height_For_Random_Spawn", 200);
		configFiles.setConfig("Max_Filled_Slots_By_Default", 0);
		configFiles.setConfig("SaveDataFileDuringReload", true);
		configFiles.setConfig("respawn_notify.respawn_all_with_command_in_world.enabled", true);
		configFiles.setConfig("respawn_notify.respawn_all_with_command_in_world.message", "<#a6e3a1>All LootChests were force-respawned in <#89dceb>[World]<#a6e3a1>.");
		configFiles.setConfig("respawn_notify.Minimum_Number_Of_Players_For_Natural_Spawning", 0);
		configFiles.setConfig("EnableLootin", false);
		configFiles.setConfig("Particles.fallback_particle", "FLAME");
		// Keep legacy files rollback-readable while permanently disabling the removed effect.
		configFiles.getConfig().set("Fall_Effect.Enabled", false);
		configFiles.setLang("Menu.particles.selected", "<#a6e3a1>Currently selected");
		configFiles.setLang("info.title", "<#cba6f7><bold>Lootbox</bold> <#6c7086>v[Version]");
		configFiles.setLang("info.introduction", "<#bac2de>Discover repeatable loot containers with rewards configured for 1MoreBlock.");
		configFiles.setLang("info.commands", "<#a6e3a1>Start with <#89dceb>/lc locate <#a6e3a1>when your rank grants access, or use <#89dceb>/lc help<#a6e3a1>.");
		configFiles.setLang("info.documentation", "<click:open_url:'https://docs.1moreblock.com/custom-server-plugins/lootbox/'><hover:show_text:'Open the Lootbox guide'><#89dceb><underlined>docs.1moreblock.com/custom-server-plugins/lootbox/</underlined></#89dceb></hover></click>");
		configFiles.getConfig().set("Fall_Effect.Let_Block_Above_Chest_After_Fall", null);
		configFiles.setLang(MENU_MAIN_TYPE, "<#cba6f7>Select container type");
		configFiles.setLang("notAnInteger", "<#f38ba8>[Number] is not a whole number.");
		configFiles.setLang("blockIsAlreadyLootchest", "<#f38ba8>This block is already registered as a LootChest.");
		configFiles.setLang("editedMaxFilledSlots", "<#a6e3a1>Maximum filled slots updated for <#89dceb>[Chest]<#a6e3a1>.");
		configFiles.setLang("copiedChest", "<#f6c177>Copied <#89dceb>[Chest1] <#f6c177>into <#89dceb>[Chest2]<#f6c177>.");
		configFiles.setLang("NotEnoughPlayers", "<#f38ba8>At least <#f9e2af>[Number] players <#f38ba8>are required to spawn LootChests.");
		configFiles.setLang("ChestDespawned", "<#a6e3a1>Despawned <#89dceb>[Chest]<#a6e3a1>.");
		configFiles.setLang("NoChestAtLocation", "<#f38ba8>That LootChest is already absent.");
		if(configFiles.getConfig().isSet("Fall_Effect.Optionnal_Color_If_Block_Is_Wool"))
			configFiles.setConfig("Fall_Effect.Optionnal_Color_If_Block_Is_Wool", null);
		configFiles.setLang("AllChestsDespawned", "<#a6e3a1>All LootChests were despawned.");
		configFiles.setLang("AllChestsDespawnedInWorld", "<#a6e3a1>All LootChests were despawned in <#89dceb>[World]<#a6e3a1>.");
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
		List<String> commandHelp = configFiles.getLang().getStringList("help");
		commandHelp.removeIf(line -> line.toLowerCase(Locale.ROOT).contains("togglefall"));
		for (int i = 0; i < commandHelp.size(); i++) {
			if (commandHelp.get(i).contains("/lc settime")) {
				commandHelp.set(i, "<#a6e3a1>/lc settime <#bac2de>\\<name> \\<minutes> <#6c7086>- Set respawn time");
			}
		}
		configFiles.getLang().set("help", commandHelp);
		configFiles.getLang().set("enabledFallEffect", null);
		configFiles.getLang().set("disabledFallEffect", null);
		configFiles.getLang().set("Menu.main.disable_fall", null);
		configFiles.getLang().set("Menu.main.enable_fall", null);
		if(!configFiles.getLang().getStringList("help").toString().contains("despawn ")){
			  List<String> help = configFiles.getLang().getStringList("help");
			  help.add("<#a6e3a1>/lc despawn <#bac2de>\\<name> <#6c7086>- Despawn a LootChest");
			  configFiles.getLang().set("help", help);
			  configFiles.saveLang();
		}
		//remove useless command
		if(configFiles.getLang().getStringList("help").toString().contains("removeAllHolo")){
			List<String> help = configFiles.getLang().getStringList("help");
			//get line and remove it
			int index = help.stream().filter(s -> s.contains("removeAllHolo")).findFirst().map(help::indexOf).orElse(-1);
			if(index!=-1) {
				help.remove(index);
			}
			configFiles.getLang().set("help", help);
			configFiles.saveLang();
		}
		configFiles.saveLang();
		configFiles.saveConfig();

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
