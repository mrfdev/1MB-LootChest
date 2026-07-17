package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.commands.ArgType;
import fr.black_eyes.lootchest.commands.SubCommand;

public class RespawnAllCommand extends SubCommand {
	
	public RespawnAllCommand() {
		super("respawnall", Collections.emptyList(), Collections.singletonList(ArgType.WORLD));
	}
	
	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		String worldName = args.length == 2 ? args[1] : null;
		if(!Lootchest.checkIfEnoughPlayersCommand()){
			Messages.msg(sender, "NotEnoughPlayers", "[Number]" , ""+Main.configs.minimumNumberOfPlayersForCommandSpawning);
			return;
		}
		List<Lootchest> chests = new ArrayList<>();
		for (Lootchest l : Main.getInstance().getLootChest().values()) {
			if (worldName != null && !l.getWorld().equals(worldName)) {
				continue;
			}
			chests.add(l);
		}

		boolean started = Main.getInstance().runBatchedChestOperation(chests, chest -> chest.spawn(true), () -> {
			if(Main.configs.noteAllcmdE && worldName == null) {
				String message = Main.configs.noteAllcmdMsg;
				for (Player player : Bukkit.getOnlinePlayers()) {
					Messages.sendMultilineMessage(message, player);
				}
			}
			if(worldName != null) {
				Messages.msg(sender, "AllChestsReloadedInWorld", "[World]", worldName);
			} else {
				Messages.msg(sender, "AllChestsReloaded", " ", " ");
			}
		});
		if (!started) {
			Messages.msg(sender, "ChestOperationInProgress");
		}
	}
}
