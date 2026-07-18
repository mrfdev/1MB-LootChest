package fr.black_eyes.lootchest;

import java.sql.Timestamp;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import fr.black_eyes.api.events.LootChestSpawnEvent;
import org.bukkit.Particle;
import fr.black_eyes.lootchest.lifecycle.ChestLifecycle;
import lombok.Getter;
import lombok.Setter;

import static fr.black_eyes.lootchest.Constants.DATA_CHEST_PATH;

public class Lootchest {

	public static final String MAX_FILLED_SLOTS = ".maxFilledSlots";
	public static final String RANDOMRADIUS = ".randomradius";
	public static final String PARTICLE = ".particle";
	public static final String PROTECTION_TIME = ".protectionTime";
	public static final String TYPE = ".type";
	/**
	 * @return the Lootchest name
	 * @param name the name to give to the lootchest
	 */
	@Getter
	@Setter private String name;

	/**
	 * @param globalLoc The static location of the lootchest, which is always used to choose the random location
	 */
	@Setter private Location globalLoc;

	/**
	 * @param randomLoc the random location that changes every time a lootchest respawns. Can be disabled to only use global location
	 */
	@Setter private Location randomLoc;

	/**
	 * @return The inventory of the lootchest, used to give it to someone, or fill the lootchest
	 */
	@Getter	private final Inventory inv;

	/**
	 * @return An array of integers, representing chances of each item in the chest
	 */
	@Getter	Integer[] chances;

	/**
	 * @return a string representing the direction of the chest block (north, east, south, or east)
	 * @param direction a string representing the direction of the chest block (north, east, south, or east)
	 */
	@Getter
	@Setter private String direction;

	/**
	 * @return the text displayed by the hologram of the lootchest
	 */
	@Getter private String holo;

	/**
	 * @return the time between two spawns of the lootchest.
	 * @param time the time between two spawns of the lootchest. -1 to disable auto respawn
	 */
	@Getter
	@Setter private long time;

	/**
	 * @return the last respawn date of the chest, in milliseconds.
	 * @param lastReset the last respawn date of the chest, in milliseconds.
	 */
	@Setter
	@Getter
	private long lastReset;

	/**
	 * @return the particle to spawn around the lootchest
	 * @param particle the particle to spawn around the lootchest
	 */
	 @Getter
	 @Setter
	 private Particle particle;

	/**
	 * @return the value of respawn_cmd boolean, which says if we should send a broadcast if the chest is respawned manually
	 * @param respawn_cmd boolean, which says if we should send a broadcast if the chest is respawned manually
	 */
	@Getter
	@Setter private boolean respawnCmdMsgEnabled;

	/**
	 * @return the value of the respawn_natural boolean, which says if we should send a broadcast if the chest is respawned "naturally"
	 * @param respawn_natural boolean, which says if we should send a broadcast if the chest is respawned "naturally"
	 */
	@Getter
	@Setter private boolean respawnNaturalMsgEnabled;

	/**
	 * @return the value of the take_msg boolean, which says if we should send a broadcast if the chest taken/looted by a player
	 * @param take_msg boolean, which says if we should send a broadcast if the chest taken/looted by a player
	 */
	@Getter
	@Setter private boolean takeMsgEnabled;

	/**
	 * @return the radius around the global location, to set the random location
	 * @param radius the radius around the global location, to set the random location
	 */
	@Getter
	@Setter private int radius;

	/**
	 * @return the world to spawn the lootchest
	 * @param world the world to spawn the lootchest
	 */
	@Getter
	@Setter private String world;

	/**
	 * @return taken boolean, which says if the chest was looted already or not
	 * @param taken boolean, which says if the chest was looted already or not
	 */
	@Getter
	@Setter private boolean taken;

	/** 
	 * @return the material used for this loot chest container
	 * @param type the material used for this loot chest container
	 */
	@Getter @Setter private Material type;

	/**
	 * @return the hologram object attached to this lootchest
	 */
	@Getter private final LootChestHologram hologram;

	@Getter @Setter private long protectionTime;

	@Getter @Setter private Integer maxFilledSlots;
	
	/**
	 * Function used in Main / reload for chest loading
	 * @param naming the name of the chest
	 */
	public Lootchest(String naming) {
		Main main = Main.getInstance();
		LootChestUtils utils = main.getUtils();
		LootChestFiles configFiles = Main.getInstance().getConfigFiles();
		taken = false;
		if(!configFiles.getData().isSet(DATA_CHEST_PATH+naming+ TYPE)){
			type = Mat.CHEST;
		}else {
			String types = configFiles.getData().getString(DATA_CHEST_PATH+naming+ TYPE);
            if (types != null) {
                switch(types) {
                    case "TRAPPED_CHEST": type = Mat.TRAPPED_CHEST; break;
                    case "BARREL": type = Mat.BARREL; break;
                    default: {
                        try{
                            type = Material.valueOf(types);
                        }catch(IllegalArgumentException e){
                            type = Mat.CHEST;
                        }
                    } break;
                }
            }
        }
		if(!configFiles.getData().isSet(DATA_CHEST_PATH+naming+ MAX_FILLED_SLOTS)){
			maxFilledSlots = Main.configs.defaultMaxFilledSlots;
		}else{
			maxFilledSlots = configFiles.getData().getInt(DATA_CHEST_PATH+naming+ MAX_FILLED_SLOTS);
		}
		name = naming;
        chances = new Integer[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		inv = Bukkit.createInventory(null, 27);
		globalLoc = utils.getPosition(naming);
		if(configFiles.getData().isSet(DATA_CHEST_PATH+naming+ RANDOMRADIUS)) {
			radius = configFiles.getData().getInt(DATA_CHEST_PATH+naming+ RANDOMRADIUS);
			if(radius > 0) {
				randomLoc = utils.getRandomPosition(naming);
			}else {
				randomLoc = null;
			}
		}else {
			radius = 0;
			randomLoc = null;
		}

		if(configFiles.getData().isSet(DATA_CHEST_PATH+naming+ PROTECTION_TIME)) {
			protectionTime = configFiles.getData().getLong(DATA_CHEST_PATH+naming+ PROTECTION_TIME);
		}else {
			protectionTime = Main.configs.defaultRespawnProtection;
		}


		holo = configFiles.getData().getString(DATA_CHEST_PATH + naming + ".holo");
		String part = configFiles.getData().getString(DATA_CHEST_PATH + naming + PARTICLE);
		if(part != null && part.equalsIgnoreCase("Disabled")) {
			particle = null;
		}else {
			particle = Main.getInstance().getParticleCatalog()
					.resolveOrFallback(part, "LootChest " + naming);
		}
		time = configFiles.getData().getInt(DATA_CHEST_PATH + naming + ".time");
		try {
		for(String keys : Objects.requireNonNull(configFiles.getData().getConfigurationSection(DATA_CHEST_PATH + naming + ".inventory")).getKeys(false)) {
			inv.setItem(Integer.parseInt(keys), configFiles.getData().getItemStack(DATA_CHEST_PATH + naming + ".inventory." + keys));
			chances[Integer.parseInt(keys)] = configFiles.getData().getInt(DATA_CHEST_PATH + naming + ".chance." + keys);
		}
		}catch(NullPointerException e) {
			Messages.log("<#f38ba8>Chest inventory data for " + name + " could not be loaded.");
		}
		respawnCmdMsgEnabled =  configFiles.getData().getBoolean(DATA_CHEST_PATH + naming + ".respawn_cmd");
		respawnNaturalMsgEnabled =  configFiles.getData().getBoolean(DATA_CHEST_PATH + naming + ".respawn_natural");
		takeMsgEnabled =  configFiles.getData().getBoolean(DATA_CHEST_PATH + naming + ".take_message");
		world = configFiles.getData().getString(DATA_CHEST_PATH + naming + ".position.world");
		direction = configFiles.getData().getString(DATA_CHEST_PATH + naming + ".direction");
		lastReset = configFiles.getData().getLong(DATA_CHEST_PATH + name + ".lastreset");
		
		hologram = new LootChestHologram(this);
	}
	
	
	
	/**
	 * Function used for /lc create
	 * @param chest - The block of the chest
	 * @param naming - The name of the chest
	 */
	public Lootchest(Block chest, String naming){
		type = chest.getType();
		taken = false;
		name = naming;
		inv = Bukkit.createInventory(null, 27);
        this.chances = new Integer[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		Inventory inventory =  ((InventoryHolder) chest.getState()).getInventory();
		for(int i = 0 ; i < 27 ; i++) {
			if(inventory.getItem(i) != null) {
				inv.setItem( i, inventory.getItem(i));
				chances[i] =  Main.configs.defaultItemChance;
			}
		}
		if(inventory.getSize() >27) {
			Messages.log("<#f38ba8>Do not use double chests to create LootChests. Only half of the inventory was registered.");
		}
		maxFilledSlots = Main.configs.defaultMaxFilledSlots;
		respawnCmdMsgEnabled =  Main.configs.noteCommandE;
		respawnNaturalMsgEnabled =  Main.configs.noteNaturalE;
		takeMsgEnabled =  Main.configs.noteMessageOnChestTake;
		direction = LootChestUtils.getDirection(chest);
		holo = name;
		time =  Main.configs.defaultResetTime;
		globalLoc =  chest.getLocation();
		lastReset =  new Timestamp(System.currentTimeMillis()).getTime();
		particle = Main.getInstance().getParticleCatalog()
				.resolveOrFallback(Main.configs.partDefaultParticle, "Particles.default_particle");
	   	radius = 0;
	   	world = LootChestUtils.getWorldName(chest.getWorld());
		protectionTime = Main.configs.defaultRespawnProtection;
		hologram = new LootChestHologram(this);
	}
	
	public Lootchest(Lootchest lc, String name){
		type = lc.getType();
		taken = false;
		this.name = name;
		inv = Bukkit.createInventory(null, 27);
        this.chances = new Integer[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
		for (int i = 0; i < 27; i++) {
			if (lc.getInv().getItem(i) != null) {
				inv.setItem(i, lc.getInv().getItem(i));
				chances[i] = lc.getChances()[i];
			}
		}
		chances = lc.getChances().clone();
		maxFilledSlots = lc.getMaxFilledSlots();
		respawnCmdMsgEnabled = lc.isRespawnCmdMsgEnabled();
		respawnNaturalMsgEnabled = lc.isRespawnNaturalMsgEnabled();
		takeMsgEnabled = lc.isTakeMsgEnabled();
		direction = lc.getDirection();
		holo = lc.getHolo();
		time = lc.getTime();
		globalLoc = lc.getPosition();
		particle = lc.getParticle();
		radius = lc.getRadius();
		world = lc.getWorld();
		protectionTime = lc.getProtectionTime();
		hologram = new LootChestHologram(this);
	}
	
	/**
	 * Function used at defined time in config and at plugin stop for saving chests. 
	 * This function doesn't save the config, file, it just edits the one in memory. Use "updateData" to save the chest in the file.
	 * UpdateData already calls this function.
	 */
	public void saveInConfig(){
		Main main = Main.getInstance();
		LootChestUtils utils = main.getUtils();
		LootChestFiles configFiles = Main.getInstance().getConfigFiles();
		configFiles.getData().set(DATA_CHEST_PATH + name + ".inventory", null);
		for(int i = 0 ; i < inv.getSize() ; i++) {
			if(inv.getItem(i) != null && Objects.requireNonNull(inv.getItem(i)).getType() != Material.AIR) {
				configFiles.getData().set(DATA_CHEST_PATH + name + ".inventory." + i, inv.getItem(i));
				configFiles.getData().set(DATA_CHEST_PATH + name + ".chance." + i, chances[i]);
			}
		}
		// Keep old data files rollback-readable while permanently disabling the removed effect.
		configFiles.getData().set(DATA_CHEST_PATH + name + ".fall", false);
		configFiles.getData().set(DATA_CHEST_PATH + name + TYPE, type.name());
		configFiles.getData().set(DATA_CHEST_PATH + name + ".respawn_cmd", respawnCmdMsgEnabled);
		configFiles.getData().set(DATA_CHEST_PATH + name + ".respawn_natural", respawnNaturalMsgEnabled);
		configFiles.getData().set(DATA_CHEST_PATH + name + ".take_message", takeMsgEnabled);
		
		configFiles.getData().set(DATA_CHEST_PATH + name + ".direction", direction);
		configFiles.getData().set(DATA_CHEST_PATH + name + ".holo", holo);
		configFiles.getData().set(DATA_CHEST_PATH + name + ".time", time);
		configFiles.getData().set(DATA_CHEST_PATH + name + PROTECTION_TIME, protectionTime);
		utils.setPosition(name, globalLoc);
		configFiles.getData().set(DATA_CHEST_PATH + name + ".lastreset", lastReset);
		if(particle!=null)
			configFiles.getData().set(DATA_CHEST_PATH +name+ PARTICLE, particle.name());
		else
			configFiles.getData().set(DATA_CHEST_PATH +name+ PARTICLE, "Disabled");
		configFiles.getData().set(DATA_CHEST_PATH+name+ RANDOMRADIUS, radius);
		if(randomLoc != null) {
			utils.setRandomPosition(name, randomLoc);
		}
		configFiles.getData().set(DATA_CHEST_PATH + name + MAX_FILLED_SLOTS, maxFilledSlots);

	}

	//fonction pour changer la position d'un coffre
	//function to change a chest location
	public void setLocation(Location loc3) {
		despawn();
		setWorld(loc3.getWorld().getName());
		setGlobalLoc(loc3);
		spawn(true);
	}

	/**
	 * Remove the chest block, the hologram, and the particle
	 * If the chunk isn't loaded before doing this, it will be unloaded after (hopefully).
	 */
	public void despawn(){
		Location startLocation = getActualLocation();
		int chunkX = startLocation.getBlockX() >> 4;
		int chunkZ = startLocation.getBlockZ() >> 4;
		boolean loaded = startLocation.getWorld().isChunkLoaded(chunkX, chunkZ);
		if(LootChestUtils.isWorldLoaded(getWorld()) && isGoodType(startLocation.getBlock())) {
			ChestLifecycle.removePhysicalContainer(
					startLocation.getBlock(),
					getParticleLocation(),
					Main.getInstance().getPart(),
					hologram::remove);
		} else {
			ChestLifecycle.removeEffects(
					getParticleLocation(),
					Main.getInstance().getPart(),
					hologram::remove);
		}
		boolean loaded2 = startLocation.getWorld().isChunkLoaded(chunkX, chunkZ);
		if(loaded != loaded2) {
			startLocation.getWorld().unloadChunk(chunkX, chunkZ);
		}
	}

	public void despawnAtValidatedLocation(Location location) {
		Location actualLocation = getActualLocation();
		if(actualLocation.getWorld() != location.getWorld()
				|| actualLocation.getBlockX() != location.getBlockX()
				|| actualLocation.getBlockY() != location.getBlockY()
				|| actualLocation.getBlockZ() != location.getBlockZ()) {
			return;
		}

		Block block = location.getBlock();
		ChestLifecycle.removePhysicalContainer(
				block,
				getParticleLocation(),
				Main.getInstance().getPart(),
				hologram::remove);
	}

	/**
	 * @return whever config option Minimum_Number_Of_Players_For_Natural_Spawning is respected
	 */
	private static boolean checkIfEnoughPlayers(){
		int num = Main.configs.minimumNumberOfPlayersForNaturalSpawning;
		int players = LootChestUtils.getPlayerCount();
		return (players >= num); 
	}

	/**
	 * @return whever config option Minimum_Number_Of_Players_For_Command_Spawning is respected
	 */
	public static boolean checkIfEnoughPlayersCommand(){
		int num = Main.configs.minimumNumberOfPlayersForCommandSpawning;
		int players = LootChestUtils.getPlayerCount();
		return (players >= num);
	}

	private boolean checkIfTimeToRespawn(){
		long tempsactuel = (new Timestamp(System.currentTimeMillis())).getTime();
		long minutes = getTime()*60*1000;
		long tempsenregistre = getLastReset();
		return (tempsactuel - tempsenregistre > minutes && minutes>-1);
	}

	/**
	 * used by spawn, spawns the chest
	 * @param block - The block concerned, where the spawn will append
	 * @param blockLocation - Location of the block
	 */
	public void createchest( Block block, Location blockLocation) {
		block.setType(getType());
		Inventory inventory = ((InventoryHolder) block.getState()).getInventory();
		LootChestUtils.fillInventory(this, inventory, true, null);

		// Preserve an existing facing when one was saved. Legacy data may omit it.
		org.bukkit.block.data.BlockData data = block.getBlockData();
		if (direction != null && !direction.equals("NULL")
				&& data instanceof org.bukkit.block.data.Directional directional) {
			try {
				BlockFace facing = BlockFace.valueOf(direction);
				if (directional.getFaces().contains(facing)) {
					directional.setFacing(facing);
					block.setBlockData(data, false);
				}
			} catch (IllegalArgumentException e) {
				Messages.log("<#f6c177>Could not restore the direction of LootChest " + getName() + ". The chest will otherwise continue to work.");
			}
		}

		// spawn particles and hologram if needed
		final Location loc2 = getParticleLocation();
		if(getParticle() != null && Main.configs.partEnable){
			Main.getInstance().getPart().put(loc2, getParticle());
		}
		getHologram().setLoc(blockLocation);
		
		setLastReset();
		if(Main.configs.saveChestLocationsAtEverySpawn) {
			saveInConfig();
			Main.getInstance().getConfigFiles().saveData();
		}
		setTaken(false);
		if(protectionTime >0){
			long now = (new Timestamp(System.currentTimeMillis())).getTime();
			Main.getInstance().getProtection().put(block.getLocation(), now+protectionTime*1000);
		}
		Main.getInstance().trackLootChestLocation(this);
		Bukkit.getPluginManager().callEvent(new LootChestSpawnEvent(this));
		LootChestUtils.scheduleReSpawn(this);
	}

	public void activateExistingContainer(Block block) {
		Location location = block.getLocation();
		createchest(block, location);
		resendContainerBlock(block);
	}

	/**
	 * Executes the spawn function, despawning the chest only if we force it to respawn
	 * @param forceRespawn Forces the chest to respawn, even if it's not time to respawn
	 */
	public boolean spawn(boolean forceRespawn){
		return spawn(forceRespawn, forceRespawn);
	}

	/**
	 * Spawns the chest, with its hologram and particles, checking if it's time to respawn, 
	 * if there's enough players, if the world is loaded, finding a good location, etc..
	 * 
	 * @param forceSpawn Forces the chest to respawn, even if it's not time to respawn
	 * @param forceDespawn Forces the chest to despawn, even if it's not time to respawn
	 * @return true if the chest was spawned, false if it wasn't
	 */
	public boolean spawn(boolean forceSpawn, boolean forceDespawn) {
		if(time == 0) time = -1;
		if(forceDespawn) {
			despawn();
			setLastReset();
		}
		// if world is not loaded or lootchest was deleted or not enough players
		if(!LootChestUtils.isWorldLoaded(getWorld()) || !Main.getInstance().getLootChest().containsValue(this) ) {
			LootChestUtils.scheduleReSpawn(this);
			return false;
		}
		// if [there's not enough player, or it's not time to respawn] and we didn't force respawn
		if( !checkIfTimeToRespawn() && !forceSpawn) {
			LootChestUtils.scheduleReSpawn(this);
			return false;
		}
		if(!checkIfEnoughPlayers() && !forceSpawn) {
			LootChestUtils.scheduleReSpawn(this);
			return false;
		}
		Location globalLocation = getPosition();
		Location spawnLoc = globalLocation.clone();
		//if randomSpawn is enabled, we get a random location in the radius
		if(getRadius() !=0){
			//if this option is true, we take the location of one of online players randomly.
			if(Main.configs.usePlayersLocationsForRandomSpawn) {
				globalLocation = LootChestUtils.chooseRandomPlayer(getWorld());
				globalLocation = globalLocation!=null?globalLocation:spawnLoc.clone();
			}
			spawnLoc = LootChestUtils.chooseRandomLocation(globalLocation, radius);
			if(spawnLoc == null){
				Messages.log("<#f38ba8>LootChest " + getName() + " could not find a valid respawn location.");
				LootChestUtils.scheduleReSpawn(this);
				return false;
			}
			// whatever happens after, the chest will spawn, so we can set this
			despawn();
			setRandomLoc(spawnLoc.clone());
		}
		// if the chest is already spawned, we despawn it
		despawn();

		// handle natural spawning messages - command respawn messages are handled in command class
		if(!forceSpawn && isRespawnNaturalMsgEnabled() ) {
			String naturalMsg = (((Main.configs.noteNaturalMsg.replace("[Chest]", holo)).replace("[x]", spawnLoc.getX()+"")).replace("[y]", spawnLoc.getY()+"")).replace("[z]", spawnLoc.getZ()+"").replace("[World]", world);
			if(!Main.configs.notePerWorldMessage) {
				for(World w : Bukkit.getWorlds()) {
					for(Player p : w.getPlayers()) {
						Messages.sendMultilineMessage(naturalMsg, p);
					}
				}
			}else {
				for(Player p : spawnLoc.getWorld().getPlayers()){
					Messages.sendMultilineMessage(naturalMsg, p);
				}
			}
		}

		final Block newBlock = spawnLoc.getBlock();
		createchest(newBlock, spawnLoc);
		resendContainerBlock(newBlock);
		
		return true;
	}

	private void resendContainerBlock(Block block) {
		Location location = block.getLocation();
		Main.getInstance().getTaskRegistry().runLater(() -> {
			Block current = location.getBlock();
			if (!isGoodType(current)) {
				return;
			}
			for (Player player : current.getWorld().getPlayers()) {
				player.sendBlockChange(location, current.getBlockData());
			}
		}, 1L);
	}

	public Location getParticleLocation() {
		final Location loc2 = getActualLocation().clone();
		loc2.add(0.5,0.5,0.5);
		return loc2;
	}
	

	/**
	 * @return a clone of the global location
	 */
	public Location getPosition() {			return globalLoc.clone();	}
	/**
	 * @return a clone of the random location
	 */
	public Location getRandomPosition() {	return (randomLoc!=null)?randomLoc.clone():getPosition();	}

	/**
	 * @return a clone of the actual location, e.g. the random location if the radius is not 0, else the global location
	 */
	public Location getActualLocation() {
		return (radius!=0)?getRandomPosition():getPosition();
	}
	
	/**
	 * @param index the index to set the chance in inventory
	 * @param v the value of the chance to set for "index" item
	 */
	public void setChance(int index, int v) {			chances[index] = v;		}


	/**
	 * @param inventory the inventory of the chest
	 */
	public void setInventory(Inventory inventory) {
		// if we don't clear the existing inv, removed items will still be in the chest
		inv.clear();
		for(int i = 0 ; i < inventory.getSize() ; i++) {
			if(inventory.getItem(i) != null) {
				inv.setItem( i, inventory.getItem(i));
				if(chances[i] ==0) {
					chances[i] =  Main.configs.defaultItemChance;
				}
			}
		}
	}

	/**
	 * Change a chest's location
	 * @param loc the new location
	 */
	public void changepos(Location loc) {
		despawn();
		setWorld(loc.getWorld().getName());
		setGlobalLoc(loc);
		spawn(true);
	}

	/**
	 * Saves the chest in data file, in case of crash, after a modification, or before server shutdown
	 */
	public void updateData() {
		Main.getInstance().trackLootChestLocation(this);
		saveInConfig();
		Main.getInstance().getConfigFiles().saveData();
	}
	
	/**
	 * Deletes the chest from data file and despawns it
	 */
	public void deleteChest() {
		despawn();
		LootChestUtils.cancelReSpawn(this);
		Main.getInstance().untrackLootChestLocation(this);
		Main.getInstance().getLootChest().remove(getName());
		Main.getInstance().getConfigFiles().getData().set(DATA_CHEST_PATH+ getName(), null);
		Main.getInstance().getConfigFiles().saveData();
	}


	/**
	 * @param block the block to check
	 * @return true if the block is the good one for the chest
	 */
	public boolean isGoodType(Block block) {
		return type.equals(block.getType());
	}

	/**
	 * gives the main information about the chest
	 */
	public String toString() {
		return (name +" " +direction+" "+ radius+" "+particle);
	}
	
	/**
	 * sets the text of the lootchest's holo
	 * @param text the text to set
	 */
	public void setHolo(String text) {
		holo = text;
		hologram.remove();
		hologram.setText(holo);
	}

	public void setLastReset() {
		this.lastReset = (new Timestamp(System.currentTimeMillis())).getTime();
	}

	/**
	 * reactivates particles, after a server restart for example
	 */
	public void reactivateEffects() {

		Location loc = getActualLocation();
		//if the lootchest isn't here, let's not spawn particles or anything
		if(!isGoodType(loc.getBlock())) {
			return;
		}
		getHologram().setLoc(loc);
		
		final Location loc2 = getParticleLocation();
		if (getParticle() != null) {
			Main.getInstance().getPart().put(loc2, getParticle());
		}
		
	}
    
}
