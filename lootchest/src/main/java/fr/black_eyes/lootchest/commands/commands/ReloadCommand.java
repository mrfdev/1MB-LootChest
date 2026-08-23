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
		Runnable failure = () -> Messages.msg(sender, "PluginReloadFailed");
		Runnable committedActivationFailure = () -> Messages.msg(sender, "PluginReloadActivationFailed");
		if (!main.reloadLootChests(
				() -> {
					if (main.hasInactiveSavedChestDefinitions()) {
						Messages.msg(
								sender,
								"PluginReloadedWithIssues",
								"[Rejected]", Integer.toString(main.getConfigFiles().getRejectedChestDefinitions().size()),
								"[Deferred]", Integer.toString(main.getUnavailableChestDefinitions().size()),
								"[Failed]", Integer.toString(main.getFailedChestDefinitions().size()));
					} else {
						Messages.msg(sender, "PluginReloaded", " ", " ");
					}
				},
				failure,
				committedActivationFailure)) {
			failure.run();
		}
	}
}
