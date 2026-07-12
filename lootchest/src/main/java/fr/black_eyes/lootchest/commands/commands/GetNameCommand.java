package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Messages;

import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.black_eyes.lootchest.Constants;
import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.commands.SubCommand;

public class GetNameCommand extends SubCommand {
	
	public GetNameCommand() {
		super("getname");
		setPlayerRequired(true);
		
	}
	
	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		Player player = (Player) sender;
		Block chest = LootChestUtils.getWatchedBlock(player);
		Lootchest lc = LootChestUtils.isLootChest(chest.getLocation());
		
		if (lc != null) {
			Messages.msg(sender, "commandGetName", Constants.CHEST_PLACEHOLDER, lc.getName());
			return;
		}
		Messages.msg(sender, "notAChest", " ", " ");
	}
}
