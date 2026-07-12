package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Messages;
import fr.black_eyes.lootchest.commands.SubCommand;
import org.bukkit.command.CommandSender;

/** Public introduction and canonical documentation link for the 1MoreBlock edition. */
public class InfoCommand extends SubCommand {

	public InfoCommand() {
		super("info");
	}

	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		String version = Main.getInstance().getPluginMeta().getVersion();
		Messages.msg(sender, "info.title", "[Version]", version);
		Messages.msg(sender, "info.introduction");
		Messages.msg(sender, "info.commands");
		Messages.msg(sender, "info.documentation");
	}
}
