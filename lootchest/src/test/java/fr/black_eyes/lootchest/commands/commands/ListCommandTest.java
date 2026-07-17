package fr.black_eyes.lootchest.commands.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class ListCommandTest {
	private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
	private static final String TEMPLATE = "<#cba6f7>LootChests: <#89dceb>[List]";
	private static final String HOVER = "<#a6e3a1>Click to edit <#89dceb>[Chest]";

	@Test
	void playerEntriesRunTheirNormalEditCommands() {
		Component message = ListCommand.renderList(
				TEMPLATE,
				HOVER,
				List.of("another1", "newtest10"),
				true);

		assertEquals("LootChests: another1 newtest10", PLAIN.serialize(message));
		List<ClickEvent<?>> clicks = clickEvents(message);
		assertEquals(2, clicks.size());
		assertRunCommand(clicks.get(0), "/lc edit another1");
		assertRunCommand(clicks.get(1), "/lc edit newtest10");
		HoverEvent<?> hover = findClickableComponent(message, "/lc edit another1").hoverEvent();
		assertNotNull(hover);
		assertTrue(hover.value() instanceof Component);
		assertEquals("Click to edit another1", PLAIN.serialize((Component) hover.value()));
	}

	@Test
	void consoleEntriesRemainPlainAndNonInteractive() {
		Component message = ListCommand.renderList(
				TEMPLATE,
				HOVER,
				List.of("another1", "newtest10"),
				false);

		assertEquals("LootChests: another1 newtest10", PLAIN.serialize(message));
		assertTrue(clickEvents(message).isEmpty());
	}

	@Test
	void unsafeSavedNamesStayVisibleWithoutCreatingACommandAction() {
		Component message = ListCommand.renderList(
				TEMPLATE,
				HOVER,
				List.of("safe-name", "unsafe\nname"),
				true);

		assertEquals("LootChests: safe-name unsafe\nname", PLAIN.serialize(message));
		List<ClickEvent<?>> clicks = clickEvents(message);
		assertEquals(1, clicks.size());
		assertRunCommand(clicks.getFirst(), "/lc edit safe-name");
	}

	private List<ClickEvent<?>> clickEvents(Component component) {
		List<ClickEvent<?>> clicks = new ArrayList<>();
		for (Component child : component.iterable(ComponentIteratorType.DEPTH_FIRST)) {
			if (child.clickEvent() != null) {
				clicks.add(child.clickEvent());
			}
		}
		return clicks;
	}

	private Component findClickableComponent(Component component, String command) {
		for (Component child : component.iterable(ComponentIteratorType.DEPTH_FIRST)) {
			ClickEvent<?> click = child.clickEvent();
			if (click != null
					&& click.payload() instanceof ClickEvent.Payload.Text payload
					&& command.equals(payload.value())) {
				return child;
			}
		}
		throw new AssertionError("No component runs " + command);
	}

	private void assertRunCommand(ClickEvent<?> click, String command) {
		assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
		assertTrue(click.payload() instanceof ClickEvent.Payload.Text);
		assertEquals(command, ((ClickEvent.Payload.Text) click.payload()).value());
	}
}
