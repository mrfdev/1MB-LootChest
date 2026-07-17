package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Messages;

import org.bukkit.command.CommandSender;

import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.commands.SubCommand;

public class ReloadCommand extends SubCommand {
	
	public ReloadCommand() {
		super("reload");
	}
	
	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		Main main = Main.getInstance();
		main.reloadLootChests(() -> Messages.msg(sender, "PluginReloaded", " ", " "));
	}
}
