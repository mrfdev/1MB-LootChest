package fr.black_eyes.lootchest.commands.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.Finding;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.IssueCode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class AuditCommandTest {
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();
    private static final String FINDING =
            "<#f6c177>- <#f38ba8>[Code] <#89dceb>[Chest]<#cdd6f4>: [Detail]";
    private static final String HOVER =
            "<#a6e3a1>Click to teleport to <#89dceb>[Chest]";

    @Test
    void playerFindingRunsTeleportForSafeChestName() {
        Component message = AuditCommand.renderFinding(
                FINDING,
                HOVER,
                new Finding(IssueCode.WRONG_CONTAINER, "superman1", "expected copper"),
                true);

        assertEquals(
                "- wrong-container superman1: expected copper",
                PLAIN.serialize(message));
        List<ClickEvent<?>> clicks = clickEvents(message);
        assertEquals(1, clicks.size());
        assertRunCommand(clicks.getFirst(), "/lc tp superman1");
        HoverEvent<?> hover = findClickableComponent(message).hoverEvent();
        assertNotNull(hover);
        assertTrue(hover.value() instanceof Component);
        assertEquals(
                "Click to teleport to superman1",
                PLAIN.serialize((Component) hover.value()));
    }

    @Test
    void consoleAndUnsafeNamesRemainPlain() {
        Finding finding = new Finding(
                IssueCode.MISSING_HOLOGRAM,
                "unsafe name",
                "not active");

        Component console = AuditCommand.renderFinding(FINDING, HOVER, finding, false);
        Component unsafePlayer = AuditCommand.renderFinding(FINDING, HOVER, finding, true);

        assertEquals(
                "- missing-hologram unsafe name: not active",
                PLAIN.serialize(console));
        assertTrue(clickEvents(console).isEmpty());
        assertTrue(clickEvents(unsafePlayer).isEmpty());
    }

    @Test
    void targetedTitleUsesTheSameTeleportAction() {
        Component title = AuditCommand.renderChestTemplate(
                "<#cba6f7><bold>Lootbox audit:</bold> <#89dceb>[Chest]",
                HOVER,
                "superman1",
                true);

        assertEquals("Lootbox audit: superman1", PLAIN.serialize(title));
        assertRunCommand(clickEvents(title).getFirst(), "/lc tp superman1");
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

    private Component findClickableComponent(Component component) {
        for (Component child : component.iterable(ComponentIteratorType.DEPTH_FIRST)) {
            if (child.clickEvent() != null) {
                return child;
            }
        }
        throw new AssertionError("No clickable component found");
    }

    private void assertRunCommand(ClickEvent<?> click, String command) {
        assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
        assertTrue(click.payload() instanceof ClickEvent.Payload.Text);
        assertEquals(command, ((ClickEvent.Payload.Text) click.payload()).value());
    }
}
