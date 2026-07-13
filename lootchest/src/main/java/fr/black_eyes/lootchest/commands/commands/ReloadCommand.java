package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Messages;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import fr.black_eyes.lootchest.Config;
import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.commands.SubCommand;
import fr.black_eyes.simpleJavaPlugin.Files;

import java.util.Objects;

import static fr.black_eyes.lootchest.Constants.DATA_CHEST_PATH;

@SuppressWarnings("deprecation")
public class ReloadCommand extends SubCommand {
	
	public ReloadCommand() {
		super("reload");
	}
	
	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		Main main = Main.getInstance();
		Files configFiles = Main.getInstance().getConfigFiles();
		if (Config.getInstance().saveDataFileDuringReload) {
			LootChestUtils.saveAllChests();
		} else {
			configFiles.reloadData();
		}
		configFiles.reloadConfig();
		Main.setConfigs(Config.getInstance(configFiles.getConfig()));
		main.reloadParticleCatalog();
		main.getPart().clear();
			Main.getInstance().getLootChest().values().forEach(chest -> chest.getHologram().remove());
		Main.getInstance().getLootChest().clear();
		for (String keys : Objects.requireNonNull(configFiles.getData().getConfigurationSection("chests")).getKeys(false)) {
			String name = configFiles.getData().getString(DATA_CHEST_PATH + keys + ".position.world");
			String randomname = name;
			if (configFiles.getData().getInt(DATA_CHEST_PATH + keys + ".randomradius") > 0) {
				randomname = configFiles.getData().getString(DATA_CHEST_PATH + keys + ".randomPosition.world");
			}
			if (name != null && LootChestUtils.isWorldLoaded(randomname) && LootChestUtils.isWorldLoaded(name)) {
				Main.getInstance().getLootChest().put(keys, new Lootchest(keys));
			} else {
				Messages.log("<#f38ba8>Could not load LootChest " + keys + ": world " + configFiles.getData().getString(DATA_CHEST_PATH + keys + ".position.world") + " is not loaded.");
			}
		}
		
		for (final Lootchest l : Main.getInstance().getLootChest().values()) {
				if (LootChestUtils.isWorldLoaded(l.getWorld())) {
					Bukkit.getScheduler().scheduleAsyncDelayedTask(Main.getInstance(), ()
							-> Bukkit.getScheduler().scheduleSyncDelayedTask(Main.getInstance(), () ->
								l.spawn(true), 0L), 5L);
				}
		}
		Messages.msg(sender, "PluginReloaded", " ", " ");
	}
}
