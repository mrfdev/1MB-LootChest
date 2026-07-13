package fr.black_eyes.lootchest;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Modules.Holograms.CMIHologram;
import com.Zrips.CMI.Modules.Holograms.HologramManager;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import lombok.Getter;

/**
 * Creates a hologram with an armorstand, related to a lootchest (for location and text only)
 * @author Valentin
 *
 */
public class LootChestHologram {

	//represents all the null names that can be given to a hologram to not create a holo
	private static final List<String> NULL_NAME = new ArrayList<>(
			Arrays.asList("\"\"" ,"\" \"" ,"null" ,"" ," " ,"_" ,"none")
			); 

	private static final double DEFAULT_RATE = 0.5;

	/**
	 * @return the text displayed by the hologram
	 */
	@Getter private String text;
	/**
	 * @return the location of the hologram
	 */
	@Getter private Location location;
	private final Lootchest chest;
	private BukkitRunnable runnable;
	/**
	 * @param chest the chest linked with this holo
	 */
	public LootChestHologram(Lootchest chest) {
		this.chest = chest;
		this.location = chest.getActualLocation();
	}
	
	/**
	 * set the text of hologram
	 * setting the text also create the armorstand if not created
	 * @param location The location to set the hologram
	 */
	public void setLoc(Location location) {
		if(Main.configs.usehologram){
			Location loc2 = location.clone();
			loc2.add(0.5, Main.configs.Hologram_distance_to_chest + DEFAULT_RATE, 0.5);
			this.location = loc2;
			remove();
			this.setText(chest.getHolo());
			if(!NULL_NAME.contains(text) && Main.configs.timerShowTimer && chest.getTime() != -1) {
				if(runnable == null) {
					startShowTime();
				}
				if(runnable.isCancelled()) {
					try {
						runnable.runTaskTimer(Main.getInstance(), 0, 20);
					}catch(IllegalStateException e) {
						runnable.cancel();
						runnable = null; 
						startShowTime();
					}
				}
			}
		}
	}



	private CMIHologram hologram;

	
	/**
	 * Kills the hologram
	 */
	public void remove() {
		if(Main.configs.usehologram) {
			if(runnable != null) {
				runnable.cancel();
				runnable = null;
			}
			if(hologram!=null) {
				try {
					hologram.remove();
				} catch (RuntimeException | LinkageError e) {
					disableHolograms(e);
				} finally {
					hologram = null;
				}
			}

		}
	}
	
	/**
	 * @param name The text displayed by the hologram
	 */
	public void setText(String name) {
		text = name;
		if(!Main.configs.usehologram) return;
		if(!NULL_NAME.contains(name)) {
			try {
				getHologram();
				setLine(Messages.legacy(name));
			} catch (RuntimeException | LinkageError e) {
				disableHolograms(e);
			}
		}else {
			remove();
		}
	}

	/**
	 * Manage hologram lines to display / change its name
	 * @param text The text to display
	 */
	private void setLine(String text){
		hologram.getPages().setLines(Collections.singletonList(text));
		hologram.update();
	}

	private void createHologram() {
		text = chest.getHolo();
		HologramManager manager = CMI.getInstance().getHologramManager();
		String name = "LootChest-" + chest.getName();
		CMIHologram existing = manager.getByName(name);
		if (existing != null) {
			existing.remove();
		}
		hologram = new CMIHologram(name, location);
		hologram.getSettings().setSaveToFile(false);
		hologram.getPages().setLines(Collections.singletonList(Messages.legacy(text)));
		manager.add(hologram, true, true);
		if (manager.getByName(name) != hologram) {
			throw new IllegalStateException("CMI did not register hologram " + name);
		}

	}

	/**
	 * @return The hologram
	 */
	private CMIHologram getHologram() {
		if(hologram==null) {
			createHologram();
		}
		return hologram;
	
	}
	
	/**
	 * Doesn't throw a party.
	 * Shows a timer on the hologram if the config says it
	 */
	private void startShowTime() {
		runnable = new BukkitRunnable() {
    		public void run() {
				try {
					CMIHologram holo = getHologram();
					long tempsActuel = (new Timestamp(System.currentTimeMillis())).getTime()/1000;
					long secondes = chest.getTime()*60;
					long tempsEnregistre = chest.getLastReset()/1000;
					secondes = secondes - (tempsActuel - tempsEnregistre);
					long secs = secondes%60;
					long mins = (secondes%3600)/60;
					long hours = secondes/3600;
					String text = Main.configs.timerFormat;
					if(text != null && Main.configs.timerHSep != null && Main.configs.timerMSep != null && Main.configs.timerSSep != null) {
						if(hours <1) text = text.replace("%Hours", "").replace("%Hsep", "");
						if(mins <1) text = text.replace("%Minutes", "").replace("%Msep", "");
						text = text.replace("%Hours", hours+"").replace("%Hsep", Main.configs.timerHSep)
								.replace("%Minutes", mins+"").replace("%Msep", Main.configs.timerMSep)
								.replace("%Seconds", secs+"").replace("%Ssep", Main.configs.timerSSep)
								.replace("%Hologram", getText());

						if(holo ==null) {
							runnable.cancel();
						}else {
							//replace with paragraph character
							setLine(Messages.legacy(text));
						}
					}
					if(secondes<=0) {
						runnable.cancel();
					}
				} catch (RuntimeException | LinkageError e) {
					disableHolograms(e);
				}
	    	}
	    };
	    runnable.runTaskTimer(Main.getInstance(), 0, 20);
	}

	private void disableHolograms(Throwable throwable) {
		Main.configs.usehologram = false;
		if(runnable != null) {
			runnable.cancel();
			runnable = null;
		}
		hologram = null;
		Main.getInstance().getLogger().warning("LootChest holograms disabled after CMI error: "
				+ throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
	}
	
}
