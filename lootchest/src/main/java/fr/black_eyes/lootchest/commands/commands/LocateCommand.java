package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Messages;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import fr.black_eyes.lootchest.Constants;
import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.commands.SubCommand;


public class LocateCommand extends SubCommand {
	
	public LocateCommand() {
		super("locate");
	}
	
	@Override
	public String getUsage() {
		return "/lc locate";
	}
	
	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		Messages.msg(sender, "locate_command.main_message", " ", " ");
		for (Lootchest lcs : Main.getInstance().getLootChest().values()) {
			if (lcs.isRespawnNaturalMsgEnabled() && !lcs.isTaken()) {
				Location block = lcs.getActualLocation();
				String holo = lcs.getHolo();
				Messages.msg(sender, "locate_command.chest_list", "[world]", block.getWorld().getName(), Constants.CHEST_PLACEHOLDER, holo, "[x]", block.getX() + "", "[y]", block.getY() + "", "[z]", block.getZ() + "");
			}
		}
	}
	

}
