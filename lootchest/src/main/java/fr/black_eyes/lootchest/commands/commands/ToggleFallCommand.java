package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Messages;

import java.util.Collections;

import org.bukkit.command.CommandSender;

import fr.black_eyes.lootchest.Constants;
import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.commands.ArgType;
import fr.black_eyes.lootchest.commands.SubCommand;

public class ToggleFallCommand extends SubCommand {
	
	public ToggleFallCommand() {
		super("togglefall", Collections.singletonList(ArgType.LOOTCHEST));
	}
	
	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		Lootchest lc = Main.getInstance().getLootChest().get(args[1]);
		boolean fall = lc.isFallEnabled();
		if (fall) {
			lc.setFallEnabled(false);
			Messages.msg(sender, "disabledFallEffect", Constants.CHEST_PLACEHOLDER, args[1]);
		} else {
			lc.setFallEnabled(true);
			Messages.msg(sender, "enabledFallEffect", Constants.CHEST_PLACEHOLDER, args[1]);
		}
		lc.updateData();
	}
}
