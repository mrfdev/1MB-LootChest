package fr.black_eyes.lootchest.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.integrations.AutoSellGuard;

/** Keep ground loot protected without changing the reward's ItemStack or foreign metadata. */
public final class LootRewardDropListener implements Listener {
    private static final NamespacedKey REWARD = new NamespacedKey("lootchest", "reward_drop");

    public static Item mark(Item item) {
        item.getPersistentDataContainer().set(REWARD, PersistentDataType.BYTE, (byte) 1);
        return item;
    }

    public static boolean isReward(Item item) {
        return item.getPersistentDataContainer().has(REWARD);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (isReward(event.getItem()) && event.getEntity() instanceof Player player
                && !Main.getInstance().getAutoSellGuard().allowPickup(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        // A merge into ordinary ground items would discard the entity-only marker.
        if (Bukkit.getPluginManager().isPluginEnabled(AutoSellGuard.PLUGIN_NAME)
                && isReward(event.getEntity()) != isReward(event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        // Moving the stack through a hopper would also discard its entity identity.
        if (Bukkit.getPluginManager().isPluginEnabled(AutoSellGuard.PLUGIN_NAME)
                && isReward(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Main.getInstance().getAutoSellGuard().forget(event.getPlayer().getUniqueId());
    }
}
