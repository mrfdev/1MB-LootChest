package fr.black_eyes.lootchest.commands.commands;

import fr.black_eyes.lootchest.Messages;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.commands.SubCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public class ListCommand extends SubCommand {

	private static final String LIST_PLACEHOLDER = "[List]";
	private static final String EDIT_COMMAND = "/lc edit ";

	public ListCommand() {
		super("list");
	}
	
	@Override
	public String getUsage() {
		return "/lc list";
	}
	
	@Override
	protected void onCommand(CommandSender sender, String[] args) {
		List<String> sorted = Main.getInstance().getLootChest().keySet().stream().sorted().toList();
		Component message = renderList(
				Messages.get("ListCommand"),
				Messages.get("ListCommandHover"),
				sorted,
				sender instanceof Player);
		sender.sendMessage(message);
	}

	static Component renderList(
			String template,
			String hoverTemplate,
			List<String> chestNames,
			boolean interactive) {
		List<Component> entries = chestNames.stream()
				.map(chestName -> chestEntry(chestName, hoverTemplate, interactive))
				.toList();
		Component chestList = Component.join(JoinConfiguration.spaces(), entries);

		return Messages.component(template).replaceText(TextReplacementConfig.builder()
				.matchLiteral(LIST_PLACEHOLDER)
				.replacement(chestList)
				.build());
	}

	private static Component chestEntry(String chestName, String hoverTemplate, boolean interactive) {
		Component entry = Component.text(chestName);
		if (!interactive || !isSafeCommandArgument(chestName)) {
			return entry;
		}

		return entry
				.clickEvent(ClickEvent.runCommand(EDIT_COMMAND + chestName))
				.hoverEvent(HoverEvent.showText(
						Messages.component(hoverTemplate, "[Chest]", chestName)));
	}

	private static boolean isSafeCommandArgument(String chestName) {
		return !chestName.isBlank()
				&& chestName.codePoints().noneMatch(character ->
						Character.isWhitespace(character) || Character.isISOControl(character));
	}
}
