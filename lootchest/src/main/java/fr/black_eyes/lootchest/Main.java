package fr.black_eyes.lootchest;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.*;

import eu.decentsoftware.holograms.api.DecentHolograms;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.api.utils.reflect.Version;
import fr.black_eyes.lootchest.commands.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;

import fr.black_eyes.lootchest.commands.CommandHandler;
import fr.black_eyes.lootchest.listeners.DeleteListener;
import fr.black_eyes.lootchest.listeners.UiListener;
import fr.black_eyes.lootchest.particles.Particle;
import fr.black_eyes.lootchest.ui.UiHandler;
import fr.black_eyes.simpleJavaPlugin.SimpleJavaPlugin;
import fr.black_eyes.simpleJavaPlugin.Updater;
import lombok.Getter;
import lombok.Setter;

import static fr.black_eyes.lootchest.Constants.DATA_CHEST_PATH;


public class Main extends SimpleJavaPlugin {
	public static final String MENU_MAIN_TYPE = "Menu.main.type";
	public static final String MENU_CHANCES_LORE = "Menu.chances.lore";
	@Getter private Particle[] supportedParticles = {};
	@Getter private final HashMap<Location, Long> protection = new HashMap<>();
	@Getter private final HashMap<String, Particle> particles = new HashMap<>();
	@Getter private final HashMap<Location, Particle> part = new HashMap<>();
	@Setter public static Config configs;
	@Getter private HashMap<String, Lootchest> lootChest;
	@Getter @Setter private static Main instance;
	@Getter private LootChestUtils utils;
	@Getter private boolean useArmorStands;
	@Getter private DecentHolograms hologramImpl;
	@Getter private UiHandler uiHandler;
	private boolean simplePluginStarted;
	private static int version = 0;


	@Override
	public void onDisable() {
		if (simplePluginStarted) {
			super.onDisable();
		}
		if (hologramImpl != null) {
			DecentHologramsAPI.onDisable();
			hologramImpl = null;
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

	/**
	 * Returns the version of your server (the x in 1.x.y)
	 * 
	 * @return The version number
	 */
	public static int getVersion() {
		if(version == 0) {
			String completeVer = getCleanBukkitVersion();
            // there is now an exception: version can now be just "26.1". Let's add a "1." before it if there's no "1"
            if(!completeVer.startsWith("1"))
                completeVer = "1." + completeVer;
			// version can be 1.8.4 or 1.12.2 or 1.8, we need to get all the digits after the first dot, and ignore the second dot IF THERE IS ONE
			version = Integer.parseInt(completeVer.split("\\.")[1]);
		}
		return version;
	}

	public static String getCleanBukkitVersion() {
		String completeVer = Bukkit.getBukkitVersion().split("-")[0];
		return completeVer.replaceFirst("^([0-9]+(?:\\.[0-9]+)*).*$", "$1");
	}

	/**
	 * Get the version a different way:
	 * 1.8.4 = 184, 1.20.6 = 1206, etc.
	 * @return the version number
	 */
	public static int getCompleteVersion(){
		String completeVer = getCleanBukkitVersion();
		String sversion = completeVer.replace(".", "");
		if(sversion.startsWith("18") || sversion.startsWith("19") || sversion.startsWith("17")){
			//add a 0 between the first and second digit
			sversion = sversion.charAt(0) + "0" + sversion.substring(1);
			if(sversion.endsWith("10")) {
				//remove the 0 at the end
				sversion = sversion.substring(0, sversion.length()-1);
			}
		}
		if(sversion.length() == 3) {
			//add a 0 at the end
			sversion = sversion + "0";
		}
		// we just have to remove the dots and parse string as integer
		return Integer.parseInt(sversion);
	}
    
	@Override
	public void onEnable() {
		setInstance(this);

		lootChest = new HashMap<>();
		useArmorStands = true;
		//initialisation des matériaux dans toutes les verions du jeu
        //initializing materials in all game versions, to allow cross-version compatibility
        Mat.init_materials();


			//In many versions, I add some text a config option. These lines are done to update config and language files without erasing options that are already set
			super.onEnable();
			simplePluginStarted = true;
			if(configFiles.getLang() == null) {
			Messages.log("<#f38ba8>Configuration or data files could not be initialized. LootChest will stop.");
			return;
		}
		Messages.log("config files loaded");
		Messages.log("Server version: " + getCleanBukkitVersion());
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
		startEmbeddedHolograms();

		//If we enabled bungee broadcast, but we aren't on a bungee server, not any message will show
        if(configs.noteBungeeBroadcast && !hasBungee()){
				Messages.log("<#f38ba8>Bungee broadcasting is enabled in LootChest, but proxy support is disabled in the server configuration.");
				Messages.log("<#f38ba8>Chest spawn messages cannot be delivered across the proxy until it is enabled.");
        	}
 
        if( !useArmorStands && Main.configs.fallBlock.equals("CHEST")) {
        	configFiles.getConfig().set("Fall_Effect.Block", "NOTE_BLOCK");
        	configs.fallBlock = "NOTE_BLOCK";
        }
        

		if(configs.checkForUpdates) {
			Messages.log("Checking for update...");
			new Updater(this, "lootchest.61564");
		}

		//if 1.7, disable world border check
		if(getVersion()<=7) {
			configs.usehologram = false;
			configs.worldborderCheckForSpawn = false;
			Messages.log("<#f6c177>World-border checks are unavailable on this server version.");
			Messages.log("<#f6c177>Holograms are unavailable on this server version.");
					
		}
        Messages.log("Starting particles...");
        
		if(configs.partEnable) {
			//Initialization of particles values, it doesn't spawn them but is used in spawning
			initParticles();
	
			//loop de tous les coffres tous les 1/4 (modifiable dans la config) de secondes pour faire spawn des particules
			//loop of all chests every 1/4 (editable in config) of seconds to spawn particles 
			startParticles();
		}
    	
    	//Loads all chests asynchronously
    	loadChests();
        
	}

	private void startEmbeddedHolograms() {
		if (getCompleteVersion() < 1080 || !configs.usehologram) {
			return;
		}
		try {
			DecentHologramsAPI.onLoad(this);
			DecentHologramsAPI.onEnable();
			if (!DecentHologramsAPI.isRunning()) {
				configs.usehologram = false;
				getLogger().warning("Embedded DecentHolograms did not start; LootChest holograms are disabled.");
				return;
			}
			hologramImpl = DecentHologramsAPI.get();
			Messages.log("<#a6e3a1>Embedded DecentHolograms adapter: " + Version.CURRENT.name());
		} catch (RuntimeException | LinkageError e) {
			configs.usehologram = false;
			hologramImpl = null;
			try {
				DecentHologramsAPI.onDisable();
			} catch (RuntimeException | LinkageError ignored) {
				// Ignore cleanup failures after a partial embedded hologram startup.
			}
			getLogger().warning("Embedded DecentHolograms failed to start; LootChest holograms are disabled. "
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
		new Thread(() -> {
			if(Main.getCompleteVersion()>=1080){
				new BukkitRunnable() {
					public void run() {
						try{
							float radius = (float) configs.PART_radius;
							float speed = (float)configs.PART_speed;
							int number = configs.partNumber;
							if (configs.partEnable) {
								for(Map.Entry<Location, Particle> entry: part.entrySet()) {
									boolean loaded = entry.getKey().getWorld().isChunkLoaded(entry.getKey().getBlockX() >> 4, entry.getKey().getBlockZ() >> 4);
									if (loaded && entry.getValue()!=null)
											try{
												entry.getValue().display(radius, radius, radius, speed, number, entry.getKey(), entry.getKey().getWorld().getPlayers());
											}catch(Exception e) {
												// concurrent modification exception, just ignore it
											}

								}
							}
						}catch(Exception e) {
							// concurrent modification exception, just ignore it
						}
					}
				}.runTaskTimer(this, 0, configs.partRespawnTicks);
			}else{
				new BukkitRunnable() {
					public void run() {
						float radius = (float) configs.PART_radius;
						float speed = (float)configs.PART_speed;
						int number = configs.partNumber;
						if (configs.partEnable) {
							for(Map.Entry<Location, Particle> entry: part.entrySet()) {
								boolean loaded = entry.getKey().getWorld().isChunkLoaded(entry.getKey().getBlockX() >> 4, entry.getKey().getBlockZ() >> 4);
								if (loaded && entry.getValue()!=null)
									entry.getValue().display(radius, radius, radius, speed, number, entry.getKey(), entry.getKey().getWorld().getPlayers());
								
							}
						}
					}
				}.runTaskTimer(this, 0, configs.partRespawnTicks);
			}
		}).start();
	}
    		
	/**
	 * Loads all chests asynchronously
	 */
	@SuppressWarnings("deprecation") //compatibility with 1.7
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
		//deletion of now unsuported feature
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
	 * This initializes an array of particles. Under 1.12, I use InventiveTalent's ParticleAPI,
	 * and for 1.12+, I use new particles spawning functions, so I use default spigot particles
	 */
	private void initParticles() {
		int cpt = 0;
		for(Particle p:Particle.values()) {
			if(p.isSupported() && p.getParticle()!=null) {
				cpt++;
			}
		}
		supportedParticles = new Particle[cpt];
		int i = 0;
		for(Particle p:Particle.values()) {
			if(p.isSupported() && p.getParticle()!=null) {
				particles.put(p.getName(), p);
				supportedParticles[i++] = p;
			}
		}
	}


}
