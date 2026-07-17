package fr.black_eyes.lootchest.listeners;

import fr.black_eyes.lootchest.ui.UiHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class UiListener implements Listener {

	private final UiHandler uiHandler;

	public UiListener(UiHandler uiHandler) {
		this.uiHandler = uiHandler;
	}

	/**
	 * Delegates the click event to the UiHandler to take care of the player's
	 * interaction with the UI
	 */
	@EventHandler
	public void onUiClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (uiHandler.handleClick(
				player,
				event.getView().getTopInventory(),
				event.getRawSlot(),
				event.getClick())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onUiDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}
		if (uiHandler.handleDrag(
				player,
				event.getView().getTopInventory(),
				event.getRawSlots())) {
			event.setCancelled(true);
		}
	}

	/**
	 * Inform the UiHandler that the player has closed the UI
	 */
	@EventHandler
	public void onUiClose(InventoryCloseEvent event) {
		if (event.getPlayer() instanceof Player player) {
			uiHandler.handleClose(player, event.getInventory());
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		uiHandler.handleQuit(event.getPlayer());
	}
}
