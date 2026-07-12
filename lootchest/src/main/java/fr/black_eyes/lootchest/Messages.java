package fr.black_eyes.lootchest;

import java.util.regex.Pattern;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class Messages {

	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
	private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
	private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
	private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
	private static final Pattern LEGACY_CODE = Pattern.compile("(?i)[&\\u00A7][0-9A-FK-ORX]");
	private static final Pattern MINI_MESSAGE_TAG = Pattern.compile(
			"(?i)<(?:#[0-9a-f]{6}|/?(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white|bold|italic|underlined|strikethrough|obfuscated|reset|newline|br|click|hover|insertion|font|gradient|rainbow|transition)(?::[^>]*)?)>");

	private Messages() {}

	public static Component component(String message, String... replacements) {
		String rendered = replace(message, replacements);
		if(LEGACY_CODE.matcher(rendered).find() && !MINI_MESSAGE_TAG.matcher(rendered).find()) {
			return LEGACY.deserialize(rendered.replace('\u00A7', '&'));
		}
		try {
			return MINI_MESSAGE.deserialize(rendered);
		} catch (RuntimeException exception) {
			Main.getInstance().getLogger().warning("Invalid MiniMessage text: " + exception.getMessage());
			return Component.text(rendered);
		}
	}

	public static String get(String path, String... replacements) {
		String message = Main.getInstance().getConfigFiles().getLang().getString(path);
		if(message == null) {
			return "<red>Missing locale message: " + MINI_MESSAGE.escapeTags(path) + "</red>";
		}
		return replace(message, replacements);
	}

	public static void msg(CommandSender sender, String path, String... replacements) {
		send(sender, get(path, replacements));
	}

	public static void send(CommandSender sender, String message, String... replacements) {
		sender.sendMessage(component(message, replacements));
	}

	public static void sendMultilineMessage(String message, CommandSender sender) {
		send(sender, message);
	}

	public static void log(String message, String... replacements) {
		Main.getInstance().getComponentLogger().info(component(message, replacements));
	}

	public static String legacy(String message, String... replacements) {
		return LEGACY_SECTION.serialize(component(message, replacements));
	}

	public static String plain(String message, String... replacements) {
		return PLAIN.serialize(component(message, replacements));
	}

	private static String replace(String message, String... replacements) {
		String rendered = message == null ? "" : message;
		boolean legacy = LEGACY_CODE.matcher(rendered).find();
		for(int i = 0; i + 1 < replacements.length; i += 2) {
			String value = replacements[i + 1] == null ? "" : replacements[i + 1];
			rendered = rendered.replace(replacements[i], legacy ? value : MINI_MESSAGE.escapeTags(value));
		}
		return rendered;
	}
}
