package fr.black_eyes.lootchest.listeners;

import fr.black_eyes.lootchest.Messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Mat;
import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.lifecycle.ChestLifecycle;


public class DeleteListener implements Listener  {
	

	private final Map<UUID, OpenLootChest> openInvs = new HashMap<>();
	//gère la destruction d'un coffre au niveau des hologrames
	
	
	private long isProtected(Block b) {
		Location blockLocation = b.getLocation();
		Long protectedUntil = Main.getInstance().getProtection().get(blockLocation);
		if(protectedUntil == null) {
			return 0;
		}

		long remaining = protectedUntil - System.currentTimeMillis();
		if (remaining > 0) {
			return remaining;
		}

		Main.getInstance().getProtection().remove(blockLocation, protectedUntil);
		return 0;
	}


	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(event.getAction() != Action.LEFT_CLICK_BLOCK
				&& event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		Block block = event.getClickedBlock();
		if(block == null) {
			return;
		}
		LootChestContainer lootChestContainer = findLootChestContainer(block);
		if(lootChestContainer == null) {
			return;
		}

		long protectionTime = isProtected(lootChestContainer.location().getBlock());
		if(protectionTime > 0) {
			event.setCancelled(true);
			long secondsRemaining = Math.max(1, (protectionTime + 999) / 1000);
			Messages.msg(
					event.getPlayer(),
					"CantBreakBlockBecauseProtected",
					"[Time]",
					Long.toString(secondsRemaining));
			return;
		}
		if (!lootChestContainer.chest().canOpen()) {
			event.setCancelled(true);
		}

	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBeforeOpenInventory(InventoryOpenEvent event) {
		if (!(event.getPlayer() instanceof Player player)) {
			return;
		}
		LootChestContainer lootChestContainer = findLootChestContainer(event.getInventory());
		if (lootChestContainer == null) {
			return;
		}
		if (!lootChestContainer.chest().canOpen()) {
			event.setCancelled(true);
			return;
		}

		long protectionTime = isProtected(lootChestContainer.location().getBlock());
		if (protectionTime > 0) {
			event.setCancelled(true);
			long secondsRemaining = Math.max(1, (protectionTime + 999) / 1000);
			Messages.msg(
					player,
					"CantBreakBlockBecauseProtected",
					"[Time]",
					Long.toString(secondsRemaining));
			return;
		}

		if (Main.configs.radiusWithoutMonstersForOpeningChest <= 0) {
			return;
		}
		int nearbyMonsters = 0;
		List<Entity> entities = player.getNearbyEntities(
				Main.configs.radiusWithoutMonstersForOpeningChest,
				Main.configs.radiusWithoutMonstersForOpeningChest,
				Main.configs.radiusWithoutMonstersForOpeningChest);
		for(Entity entity: entities) {
			if(entity instanceof Monster) {
				nearbyMonsters++;
			}
		}
		if(nearbyMonsters != 0) {
			event.setCancelled(true);
			Messages.msg(
					player,
					"CantOpenLootchestBecauseMonster",
					"[Number]",
					Integer.toString(nearbyMonsters));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onOpenInventory(InventoryOpenEvent event) {
		if (!(event.getPlayer() instanceof Player player)) {
			return;
		}
		LootChestContainer lootChestContainer = findLootChestContainer(event.getInventory());
		if (lootChestContainer != null) {
			ChestLifecycle.OpenToken token =
					lootChestContainer.chest().open(player.getUniqueId());
			if (token == null) {
				return;
			}
			OpenLootChest previous = openInvs.put(
					player.getUniqueId(),
					new OpenLootChest(
							lootChestContainer.chest(),
							lootChestContainer.location(),
							token));
			if (previous != null) {
				previous.chest().close(previous.token());
			}
		}
	}
   
    
	@EventHandler
	public void onCloseInventory(InventoryCloseEvent e) {
		if (!(e.getPlayer() instanceof Player p)) {
			return;
		}
		handleTrackedInventoryClose(p, e.getInventory());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		handleTrackedInventoryClose(player, player.getOpenInventory().getTopInventory());
		OpenLootChest stale = openInvs.remove(player.getUniqueId());
		if (stale != null) {
			stale.chest().close(stale.token());
		}
	}

	private void handleTrackedInventoryClose(Player p, Inventory inv) {
		UUID playerId = p.getUniqueId();
		OpenLootChest tracked = openInvs.get(playerId);
		if (tracked == null || !isInventoryAt(inv, tracked.location())) {
			return;
		}
		if (!openInvs.remove(playerId, tracked)) {
			return;
		}
		Lootchest key = tracked.chest();
		if (!key.close(tracked.token())) {
			return;
		}

		Location loc = tracked.location().clone();
		if (LootChestUtils.isLootChest(loc) != key) {
			return;
		}
		boolean inventoryEmpty = LootChestUtils.isEmpty(inv);
		if(ChestLifecycle.shouldCollectAfterClose(
				inventoryEmpty,
				Main.configs.removeEmptyChests,
				Main.configs.removeChestAfterFirstOpening)) {
			ChestLifecycle.RemovalCause cause = inventoryEmpty
					? ChestLifecycle.RemovalCause.EMPTY
					: ChestLifecycle.RemovalCause.FIRST_OPEN;
			ChestLifecycle.Transition transition = key.beginRemoval(cause);
			if (transition != null) {
				if(Main.configs.destroyNaturallyInsteadOfRemovingChest) {
					loc.getWorld().dropItemNaturally(loc, new ItemStack(key.getType()));
				}
				key.completeRemovalNextTick(transition, loc);
			}
		}
		if(inventoryEmpty) {
			sendChestTakeMessageIfEnabled(key, p);
		}
	}

	public void clearTrackedInventories() {
		Map<UUID, OpenLootChest> trackedInventories = new HashMap<>(openInvs);
		openInvs.clear();
		for (Map.Entry<UUID, OpenLootChest> entry : trackedInventories.entrySet()) {
			OpenLootChest tracked = entry.getValue();
			tracked.chest().close(tracked.token());
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null
					&& isInventoryAt(player.getOpenInventory().getTopInventory(), tracked.location())) {
				player.closeInventory();
			}
		}
	}

	private boolean isInventoryAt(Inventory inventory, Location expectedLocation) {
		InventoryHolder holder = inventory.getHolder(false);
		if (holder instanceof DoubleChest doubleChest) {
			return isHolderAt(doubleChest.getLeftSide(false), expectedLocation)
					|| isHolderAt(doubleChest.getRightSide(false), expectedLocation);
		}
		if (isHolderAt(holder, expectedLocation)) {
			return true;
		}
		return isSameBlock(inventory.getLocation(), expectedLocation);
	}

	private boolean isHolderAt(InventoryHolder holder, Location expectedLocation) {
		return holder instanceof Container container
				&& isSameBlock(container.getLocation(), expectedLocation);
	}

	private boolean isSameBlock(Location first, Location second) {
		return first != null
				&& second != null
				&& first.getWorld() != null
				&& second.getWorld() != null
				&& first.getWorld().getUID().equals(second.getWorld().getUID())
				&& first.getBlockX() == second.getBlockX()
				&& first.getBlockY() == second.getBlockY()
				&& first.getBlockZ() == second.getBlockZ();
	}

	private LootChestContainer findLootChestContainer(Inventory inventory) {
		InventoryHolder holder = inventory.getHolder(false);
		if (holder instanceof DoubleChest doubleChest) {
			LootChestContainer left = findLootChestContainer(doubleChest.getLeftSide(false));
			return left != null ? left : findLootChestContainer(doubleChest.getRightSide(false));
		}

		LootChestContainer container = findLootChestContainer(holder);
		return container != null ? container : findLootChestContainer(inventory.getLocation());
	}

	private LootChestContainer findLootChestContainer(InventoryHolder holder) {
		return holder instanceof Container container
				? findLootChestContainer(container.getLocation())
				: null;
	}

	private LootChestContainer findLootChestContainer(Block block) {
		if (!Mat.isALootChestBlock(block)) {
			return null;
		}
		if (block.getState() instanceof Container container) {
			LootChestContainer inventoryContainer = findLootChestContainer(container.getInventory());
			if (inventoryContainer != null) {
				return inventoryContainer;
			}
		}
		return findLootChestContainer(block.getLocation());
	}

	private LootChestContainer findLootChestContainer(Location location) {
		if (location == null) {
			return null;
		}
		Location blockLocation = location.getBlock().getLocation();
		if (!Mat.isALootChestBlock(blockLocation.getBlock())) {
			return null;
		}
		Lootchest chest = LootChestUtils.isLootChest(blockLocation);
		return chest == null ? null : new LootChestContainer(chest, blockLocation);
	}

	private record LootChestContainer(Lootchest chest, Location location) {
	}

	private record OpenLootChest(
			Lootchest chest,
			Location location,
			ChestLifecycle.OpenToken token) {
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onChestBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		LootChestContainer lootChestContainer = findLootChestContainer(block);
		if(lootChestContainer == null) {
			return;
		}

		Lootchest chest = lootChestContainer.chest();
		Location lootChestLocation = lootChestContainer.location();
		if(isProtected(lootChestLocation.getBlock()) > 0) {
			event.setCancelled(true);
			return;
		}
		if (!(block.getState() instanceof Container container)) {
			return;
		}

		event.setCancelled(true);
		ChestLifecycle.Transition transition =
				chest.beginRemoval(ChestLifecycle.RemovalCause.BREAK);
		if (transition == null) {
			return;
		}

		try {
			ChestLifecycle.collectBreakContents(
					container.getInventory(),
					item -> block.getWorld().dropItemNaturally(block.getLocation(), item));
			if(Main.configs.destroyNaturallyInsteadOfRemovingChest) {
				lootChestLocation.getWorld().dropItemNaturally(
						lootChestLocation,
						new ItemStack(chest.getType()));
			}
		} finally {
			// Paper restores cancelled block breaks after event handlers return.
			chest.completeRemovalNextTick(transition, lootChestLocation);
		}

		sendChestTakeMessageIfEnabled(chest, event.getPlayer());
	}

	private void sendChestTakeMessageIfEnabled(Lootchest keys, Player p) {
		if(keys.isTakeMsgEnabled() && !keys.isTaken()){
			keys.setTaken(true);
			String msg = Objects.requireNonNull(Main.getInstance().getConfigFiles().getLang().getString("playerTookChest")).replace("[Player]", p.getName()).replace("[Chest]", keys.getHolo());
			if(!Main.configs.notePerWorldMessage) {
				LootChestUtils.broadcast(msg);
			}else {
				for(Player pl : p.getWorld().getPlayers()){
					Messages.send(pl, msg);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplosion(EntityExplodeEvent event) {
		handleExplosion(event.blockList());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplosion(BlockExplodeEvent event) {
		handleExplosion(event.blockList());
	}

	private void handleExplosion(List<Block> affectedBlocks) {
		Iterator<Block> iterator = affectedBlocks.iterator();
		while (iterator.hasNext()) {
			Block block = iterator.next();
			LootChestContainer container = findLootChestContainer(block);
			if (container == null) {
				continue;
			}

			// LootChest owns the block lifecycle, so vanilla explosion processing
			// must never remove the block behind its saved state.
			iterator.remove();
			if (Main.configs.protectFromExplosions
					|| isProtected(container.location().getBlock()) > 0) {
				continue;
			}

			Location location = container.location();
			ChestLifecycle.Transition transition = container.chest().beginRemoval(
					ChestLifecycle.RemovalCause.EXPLOSION);
			if (transition == null) {
				continue;
			}
			if(Main.configs.destroyNaturallyInsteadOfRemovingChest) {
				location.getWorld().dropItemNaturally(
						location,
						new ItemStack(container.chest().getType()));
			}
			if (container.chest().completeRemoval(transition, location)) {
				container.chest().spawn(false);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInventoryMove(InventoryMoveItemEvent event) {
		if (blocksAutomatedAccess(event.getSource())
				|| blocksAutomatedAccess(event.getDestination())) {
			event.setCancelled(true);
		}
	}

	private boolean blocksAutomatedAccess(Inventory inventory) {
		LootChestContainer container = findLootChestContainer(inventory);
		return container != null
				&& (Main.configs.preventHopperPlacingUnderLootChest
				|| isProtected(container.location().getBlock()) > 0);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onHopperPlace(BlockPlaceEvent event) {
		Block hopper = event.getBlockPlaced();
		if (Main.configs.preventHopperPlacingUnderLootChest
				&& hopper.getType() == Material.HOPPER
				&& findLootChestContainer(hopper.getRelative(BlockFace.UP)) != null) {
			event.setCancelled(true);
		}
	}


	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		if (movesLootChest(event.getBlocks())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		if (movesLootChest(event.getBlocks())) {
			event.setCancelled(true);
		}
	}

	private boolean movesLootChest(List<Block> movedBlocks) {
		for (Block block : movedBlocks) {
			if (findLootChestContainer(block) != null) {
				return true;
			}
		}
		return false;
	}

	// if a chest is placed around a lootchest, the event has to be canceled
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent e) {
		Block block = e.getBlock();
		if (Mat.isALootChestBlock(block)){
			for(Block b : getBlocksInRadius(e.getBlock(), 1)) {
				if (LootChestUtils.isLootChest(b.getLocation()) != null) {
					e.setCancelled(true);
					return;
				}
			}
		}
	}

	public List<Block> getBlocksInRadius(Block start, int radius){
		ArrayList<Block> blocks = new ArrayList<>();
		for(double x = start.getLocation().getX() - radius; x <= start.getLocation().getX() + radius; x++){
			for(double y = start.getLocation().getY() - radius; y <= start.getLocation().getY() + radius; y++){
				for(double z = start.getLocation().getZ() - radius; z <= start.getLocation().getZ() + radius; z++){
					Location loc = new Location(start.getWorld(), x, y, z);
					blocks.add(loc.getBlock());
				}
			}
		}
		return blocks;
	}


}
