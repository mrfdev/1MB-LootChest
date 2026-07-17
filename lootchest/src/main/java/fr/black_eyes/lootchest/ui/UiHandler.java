package fr.black_eyes.lootchest.ui;

import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.ui.menu.ChancesMenu;
import fr.black_eyes.lootchest.ui.menu.ContentsMenu;
import fr.black_eyes.lootchest.ui.menu.CopyMenu;
import fr.black_eyes.lootchest.ui.menu.MainMenu;
import fr.black_eyes.lootchest.ui.menu.ParticleMenu;
import fr.black_eyes.lootchest.ui.menu.TimeMenu;
import fr.black_eyes.lootchest.ui.menu.TypeMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles the opening and closing of UIs for players and delegating interactions with the UIs
 */
public class UiHandler {
	
	private final Map<UUID, ChestUi> playerUis;
	private final Main plugin;

	/**
	 * Enum for the different types of UIs that can be opened
	 */
	public enum UiType {
		MAIN, COPY, TYPE, PARTICLE, EDIT, TIME, CHANCES
	}

	public UiHandler(Main plugin) {
		this.playerUis = new HashMap<>();
		this.plugin = plugin;
	}

	/**
	 * Delegates the click event to the respective UI to handle the player's interaction with the UI
	 * @return true if the click event should be cancelled
	 */
	public boolean handleClick(Player player, Inventory topInventory, int rawSlot, ClickType type) {
		ChestUi ui = getOpenUi(player, topInventory);
		if (ui == null || ui.allowsInventoryEditing()) {
			return false;
		}
		if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
			ui.onClickSlot(player, rawSlot, type);
		}
		return true;
	}

	/**
	 * Read-only menus reject drag operations. The contents editor deliberately
	 * allows them so administrators can arrange loot normally.
	 */
	public boolean handleDrag(Player player, Inventory topInventory, Set<Integer> rawSlots) {
		ChestUi ui = getOpenUi(player, topInventory);
		return ui != null && !ui.allowsInventoryEditing() && !rawSlots.isEmpty();
	}

	/**
	 * Inform the UI that the player has closed the UI
	 */
	public void handleClose(Player player, Inventory topInventory) {
		UUID playerId = player.getUniqueId();
		ChestUi ui = getOpenUi(player, topInventory);
		if (ui != null && playerUis.remove(playerId, ui)) {
			plugin.getTaskRegistry().cancel(openTaskKey(playerId));
			closeUi(player, ui, ChestUi.CloseReason.PLAYER);
		}
	}

	public void handleQuit(Player player) {
		UUID playerId = player.getUniqueId();
		plugin.getTaskRegistry().cancel(openTaskKey(playerId));
		ChestUi ui = playerUis.remove(playerId);
		if (ui != null) {
			closeUi(player, ui, ChestUi.CloseReason.QUIT);
		}
	}

	public void closeAll(ChestUi.CloseReason reason) {
		Map<UUID, ChestUi> openUis = new HashMap<>(playerUis);
		playerUis.clear();
		for (Map.Entry<UUID, ChestUi> entry : openUis.entrySet()) {
			UUID playerId = entry.getKey();
			plugin.getTaskRegistry().cancel(openTaskKey(playerId));
			Player player = Bukkit.getPlayer(playerId);
			if (player == null) {
				continue;
			}
			ChestUi ui = entry.getValue();
			closeUi(player, ui, reason);
			if (ui.ownsInventory(player.getOpenInventory().getTopInventory())) {
				player.closeInventory();
			}
		}
	}

	/**
	 * Opens the specified UI for the player with a delay
	 * @param delay ticks to wait before opening the UI
	 */
	public void openUi(Player player, UiType type, Lootchest chest, long delay) {
		UUID playerId = player.getUniqueId();
		plugin.getTaskRegistry().runLater(
				openTaskKey(playerId),
				() -> {
					if (player.isOnline() && !playerUis.containsKey(playerId)) {
						openUiNow(player, type, chest);
					}
				},
				delay);
	}

	/**
	 * Opens the specified UI for the player
	 */
	public void openUi(Player player, UiType type, Lootchest chest) {
		if (!player.isOnline()) {
			return;
		}
		UUID playerId = player.getUniqueId();
		plugin.getTaskRegistry().cancel(openTaskKey(playerId));
		if (playerUis.containsKey(playerId)) {
			plugin.getTaskRegistry().runLater(
					openTaskKey(playerId),
					() -> {
						if (player.isOnline()) {
							openUiNow(player, type, chest);
						}
					},
					1L);
			return;
		}
		openUiNow(player, type, chest);
	}

	private void openUiNow(Player player, UiType type, Lootchest chest) {
		UUID playerId = player.getUniqueId();
		ChestUi previous = playerUis.remove(playerId);
		if (previous != null) {
			closeUi(player, previous, ChestUi.CloseReason.TRANSITION);
		}

		ChestUi ui = createUi(type, chest);
		ui.open(player);
		if (ui.ownsInventory(player.getOpenInventory().getTopInventory())) {
			playerUis.put(playerId, ui);
		}
	}

	private ChestUi createUi(UiType type, Lootchest chest) {
		return switch (type) {
			case MAIN -> new MainMenu(chest, this);
			case COPY -> new CopyMenu(chest, this);
			case TYPE -> new TypeMenu(chest, this);
			case PARTICLE -> new ParticleMenu(chest, this);
			case EDIT -> new ContentsMenu(chest, this);
			case TIME -> new TimeMenu(chest, this);
			case CHANCES -> new ChancesMenu(chest, this);
		};
	}

	private ChestUi getOpenUi(Player player, Inventory topInventory) {
		ChestUi ui = playerUis.get(player.getUniqueId());
		return ui != null && ui.ownsInventory(topInventory) ? ui : null;
	}

	private void closeUi(Player player, ChestUi ui, ChestUi.CloseReason reason) {
		try {
			ui.onClose(player, reason);
		} catch (RuntimeException | LinkageError exception) {
			plugin.getLogger().log(
					Level.SEVERE,
					"Could not finalize LootChest UI for " + player.getName() + " during " + reason + ".",
					exception);
		}
	}

	private static String openTaskKey(UUID playerId) {
		return "ui-open:" + playerId;
	}

}
