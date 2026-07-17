package fr.black_eyes.lootchest.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

class DeleteListenerEventContractTest {
    @Test
    void lifecycleGuardsRespectEarlierProtectionCancellations() throws ReflectiveOperationException {
        assertHighestIgnoringCancelled("onPlayerInteract", PlayerInteractEvent.class);
        assertHighestIgnoringCancelled("onBeforeOpenInventory", InventoryOpenEvent.class);
        assertHighestIgnoringCancelled("onChestBreak", BlockBreakEvent.class);
        assertHighestIgnoringCancelled("onEntityExplosion", EntityExplodeEvent.class);
        assertHighestIgnoringCancelled("onBlockExplosion", BlockExplodeEvent.class);
        assertHighestIgnoringCancelled("onInventoryMove", InventoryMoveItemEvent.class);
        assertHighestIgnoringCancelled("onHopperPlace", BlockPlaceEvent.class);
        assertHighestIgnoringCancelled("onPistonExtend", BlockPistonExtendEvent.class);
        assertHighestIgnoringCancelled("onPistonRetract", BlockPistonRetractEvent.class);
        assertHighestIgnoringCancelled("onBlockPlace", BlockPlaceEvent.class);
    }

    @Test
    void successfulOpenTrackingRunsAtMonitorPriority() throws ReflectiveOperationException {
        EventHandler handler = eventHandler("onOpenInventory", InventoryOpenEvent.class);

        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    private void assertHighestIgnoringCancelled(
            String methodName,
            Class<? extends Event> eventType) throws ReflectiveOperationException {
        EventHandler handler = eventHandler(methodName, eventType);

        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    private EventHandler eventHandler(
            String methodName,
            Class<? extends Event> eventType) throws ReflectiveOperationException {
        Method method = DeleteListener.class.getDeclaredMethod(methodName, eventType);
        EventHandler handler = method.getAnnotation(EventHandler.class);
        assertTrue(handler != null, methodName + " must remain a Bukkit event handler");
        return handler;
    }
}
