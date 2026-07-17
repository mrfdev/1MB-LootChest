package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.CommandSender;

import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.commands.ArgType;
import fr.black_eyes.lootchest.commands.SubCommand;

public class DespawnAllCommand extends SubCommand {
	
	public DespawnAllCommand() {
		super("despawnall", Collections.emptyList(), Collections.singletonList(ArgType.WORLD));
	}
	
	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		String worldName = args.length == 2 ? args[1] : null;
		List<Lootchest> chests = new ArrayList<>();
		for (Lootchest chest : Main.getInstance().getLootChest().values()) {
			if (worldName == null || chest.getWorld().equals(worldName)) {
				chests.add(chest);
			}
		}

		boolean started = Main.getInstance().runBatchedChestOperation(
				chests,
				chest -> chest.spawn(false, true),
				() -> {
					if (worldName == null) {
						Messages.msg(sender, "AllChestsDespawned", " ", " ");
					} else {
						Messages.msg(sender, "AllChestsDespawnedInWorld", "[World]", worldName);
					}
				});
		if (!started) {
			Messages.msg(sender, "ChestOperationInProgress");
		}
	}
}
