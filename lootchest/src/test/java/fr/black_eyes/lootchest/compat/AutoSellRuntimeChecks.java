package fr.black_eyes.lootchest.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.integrations.AutoSellGuard;
import fr.black_eyes.lootchest.listeners.LootRewardDropListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Synthetic player state only, isolated by the console-only PaperRuntimeProbe. Never shipped. */
final class AutoSellRuntimeChecks {
    static void run(JavaPlugin probe, Lootchest chest) throws Exception {
        runPlayer(probe, chest, UUID.fromString("19500e6e-c119-42f6-9851-c82706d4abe1"), "AutoSellProbeJava");
        runPlayer(probe, chest, UUID.fromString("00000000-0000-0000-0009-00000000a501"), ".AutoSellProbe");
    }

    private static void runPlayer(JavaPlugin probe, Lootchest chest, UUID uuid, String playerName) throws Exception {
        Plugin autosell = Bukkit.getPluginManager().getPlugin("1MB-CMIAPI-AutoSell");
        check(autosell == null || autosell.isEnabled(), "Installed AutoSell fixture must be enabled");
        Inventory storage = Bukkit.createInventory(null, 36);
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[] {PlayerInventory.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getHeldItemSlot")) return 0;
                    return Inventory.class.getMethod(method.getName(), method.getParameterTypes()).invoke(storage, args);
                });
        List<String> messages = new ArrayList<>();
        AtomicReference<InventoryView> openView = new AtomicReference<>();
        AtomicReference<String> name = new AtomicReference<>(playerName);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name.get();
                    case "getInventory" -> inventory;
                    case "getOpenInventory" -> openView.get();
                    case "getNearbyEntities" -> List.of();
                    case "getWorld" -> chest.getActualLocation().getWorld();
                    case "getLocation" -> chest.getActualLocation();
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "isOnline", "hasPermission", "isValid" -> true;
                    case "sendMessage" -> {
                        for (Object arg : args) if (arg instanceof Component component) {
                            messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                        }
                        yield null;
                    }
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "LootChestAutoSellProbe";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        Inventory physical = ((Container) chest.getActualLocation().getBlock().getState()).getInventory();
        org.bukkit.Location blockLocation = chest.getActualLocation().getBlock().getLocation();
        InventoryView view = view(player, physical, inventory);
        openView.set(view);
        if (autosell == null) {
            check(Main.getInstance().getAutoSellGuard().check(player) == AutoSellGuard.Result.ALLOWED,
                    "Standalone collection must be allowed");
            InventoryOpenEvent event = new InventoryOpenEvent(view);
            // Only the preflight: no tracked session is created in this standalone branch.
            Long protection = Main.getInstance().getProtection().remove(blockLocation);
            try {
                new fr.black_eyes.lootchest.listeners.DeleteListener().onBeforeOpenInventory(event);
            } finally {
                if (protection != null) Main.getInstance().getProtection().put(blockLocation, protection);
            }
            check(!event.isCancelled(), "Standalone chest opening was blocked");
            probe.getLogger().info("PROBE AutoSell absent; standalone opening allowed for " + playerName);
            return;
        }
        @SuppressWarnings("unchecked")
        Map<UUID, Object> profiles = (Map<UUID, Object>) field(autosell.getClass(), "profiles").get(autosell);
        check(!profiles.containsKey(uuid) && Bukkit.getPlayer(uuid) == null, "Synthetic identity is in use");
        Class<?> profileType = Class.forName(autosell.getClass().getName() + "$Profile", false,
                autosell.getClass().getClassLoader());
        var constructor = profileType.getDeclaredConstructor(UUID.class);
        constructor.setAccessible(true);
        Object profile = constructor.newInstance(uuid);
        Method selectOnly = profileType.getDeclaredMethod("selectOnly", String.class, String.class);
        selectOnly.setAccessible(true);
        Method categoryOf = autosell.getClass().getDeclaredMethod("categoryOf", Material.class);
        categoryOf.setAccessible(true);
        selectOnly.invoke(profile, "cobblestone", categoryOf.invoke(autosell, Material.COBBLESTONE));
        ItemStack[] original = chest.getInv().getContents();
        ItemStack[] physicalBefore = physical.getContents();
        int maxSlots = chest.getMaxFilledSlots();
        check(!Main.configs.removeChestAfterFirstOpening, "Probe fixture must retain nonempty opened chests");
        profiles.put(uuid, profile);
        Long protection = Main.getInstance().getProtection().remove(blockLocation);
        try {
            Object decision = autosell.getClass().getMethod("preview", Player.class).invoke(autosell, player);
            check(!((Boolean) decision.getClass().getMethod("allowed").invoke(decision)), "AutoSell fixture is not ON");
            ItemStack reward = ItemStack.of(Material.COBBLESTONE, 64);
            Method eligibility = autosell.getClass().getDeclaredMethod("itemEligibility", ItemStack.class, profileType);
            eligibility.setAccessible(true);
            Object eligible = eligibility.invoke(autosell, reward, profile);
            Method allowed = eligible.getClass().getDeclaredMethod("allowed");
            allowed.setAccessible(true);
            check((Boolean) allowed.invoke(eligible), "Test reward must be eligible for actual AutoSell: " + eligible);
            probe.getLogger().info("PROBE AutoSell ON; plain LootChest cobblestone is sellable by actual AutoSell");
            InventoryOpenEvent blockedOpen = new InventoryOpenEvent(view);
            fire(blockedOpen);
            check(blockedOpen.isCancelled(), "AutoSell ON opened a LootChest");
            check(messages.stream().anyMatch(message -> message.contains("cannot open this LootChest")
                    && message.contains("/autosell toggle")), "Opening denial lacks the correct disable command: " + messages);
            int messagesBefore = messages.size();
            InventoryOpenEvent preCancelled = new InventoryOpenEvent(view);
            preCancelled.setCancelled(true);
            fire(preCancelled);
            check(preCancelled.isCancelled() && messages.size() == messagesBefore,
                    "Earlier protection cancellation must be respected without additional feedback");
            check(field(profileType, "enabled").getBoolean(profile), "Guard silently disabled AutoSell");
            chest.getInv().clear();
            chest.getInv().setItem(0, reward);
            chest.setMaxFilledSlots(0);
            LootChestUtils.fillInventory(chest, inventory, false, player);
            check(storage.isEmpty(), "AutoSell ON received sellable loot without a safety check");
            check(!messages.isEmpty(), "Blocked recipient must get instructions");
            storage.setItem(0, ItemStack.of(Material.EMERALD, 5));
            ItemStack[] before = storage.getContents();
            check(!LootChestUtils.tryFillInventory(chest, inventory, true, player), "Clear-and-fill bypassed guard");
            check(Arrays.equals(before, storage.getContents()), "Blocked delivery cleared existing items");
            for (int slot = 0; slot < storage.getSize(); slot++) storage.setItem(slot, ItemStack.of(Material.STONE, 64));
            before = storage.getContents();
            check(!LootChestUtils.tryFillInventory(chest, inventory, false, player), "Full inventory bypassed guard");
            check(Arrays.equals(before, storage.getContents()), "Blocked full-inventory delivery changed items");
            storage.clear();

            field(profileType, "enabled").setBoolean(profile, false);
            InventoryOpenEvent allowedOpen = new InventoryOpenEvent(view);
            fire(allowedOpen);
            check(!allowedOpen.isCancelled(), "AutoSell OFF cannot open LootChest");
            // Now change state while the same view remains open. Every route must recheck it.
            field(profileType, "enabled").setBoolean(profile, true);
            name.set(playerName + "Renamed");
            for (ClickType click : ClickType.values()) {
                InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                        0, click, InventoryAction.PICKUP_ALL, click == ClickType.NUMBER_KEY ? 0 : -1);
                fire(event);
                check(event.isCancelled(), "Open-session toggle bypass through " + click);
            }
            for (InventoryAction action : InventoryAction.values()) {
                InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                        30, ClickType.LEFT, action);
                fire(event);
                check(event.isCancelled(), "Bottom-inventory action bypass through " + action);
            }
            InventoryCreativeEvent creative = new InventoryCreativeEvent(view, InventoryType.SlotType.CONTAINER,
                    0, reward);
            fire(creative);
            check(creative.isCancelled(), "Creative interaction bypassed the guard");
            InventoryDragEvent drag = new InventoryDragEvent(view, reward, reward, false, Map.of(0, reward, 28, reward));
            fire(drag);
            check(drag.isCancelled(), "Drag bypassed the guard");
            BlockBreakEvent broken = new BlockBreakEvent(chest.getActualLocation().getBlock(), player);
            fire(broken);
            check(broken.isCancelled() && Arrays.equals(physicalBefore, physical.getContents()),
                    "Blocked break removed or dropped chest loot");
            checkDrops(player, view, physical, reward, profileType, profile);
            field(profileType, "enabled").setBoolean(profile, true);
            checkDrops(player, view, physical, physicalBefore[0].clone(), profileType, profile);

            field(profileType, "enabled").setBoolean(profile, false);
            @SuppressWarnings("unchecked")
            Map<UUID, CompletableFuture<Void>> pending = (Map<UUID, CompletableFuture<Void>>)
                    field(autosell.getClass(), "pendingSafeOff").get(autosell);
            check(!pending.containsKey(uuid), "Synthetic safe-off identity is in use");
            pending.put(uuid, new CompletableFuture<>());
            try {
                InventoryOpenEvent saving = new InventoryOpenEvent(view);
                fire(saving);
                check(saving.isCancelled(), "Safe-off persistence must finish before opening");
            } finally { pending.remove(uuid); }
            InventoryClickEvent allowedClick = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                    0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
            fire(allowedClick);
            check(!allowedClick.isCancelled(), "AutoSell OFF must permit taking loot");
            check(LootChestUtils.tryFillInventory(chest, inventory, false, player), "AutoSell OFF delivery refused");
            check(reward.equals(storage.getItem(0)), "Safe delivery changed reward quantity or metadata");
            fire(new InventoryCloseEvent(view));
            InventoryOpenEvent reopened = new InventoryOpenEvent(view);
            fire(reopened);
            check(!reopened.isCancelled(), "Reopening after safe collection was blocked");
            fire(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
            InventoryOpenEvent reconnected = new InventoryOpenEvent(view);
            fire(reconnected);
            check(!reconnected.isCancelled(), "Reconnecting retained a stale collection denial");
            // Ordinary inventories remain accessible even when AutoSell is active.
            field(profileType, "enabled").setBoolean(profile, true);
            InventoryOpenEvent ordinary = new InventoryOpenEvent(view(player, Bukkit.createInventory(null, 27), inventory));
            fire(ordinary);
            check(!ordinary.isCancelled(), "An ordinary chest was incorrectly blocked");
            probe.getLogger().info("PROBE AutoSell opening, live toggle, click/action matrix, drag, creative, break, "
                    + "ground loot, safe-off wait, full inventory and retry passed for " + playerName);
        } finally {
            try {
                fire(new InventoryCloseEvent(view));
            } finally {
                Main.getInstance().getAutoSellGuard().forget(uuid);
                profiles.remove(uuid);
                chest.getInv().setContents(original);
                chest.setMaxFilledSlots(maxSlots);
                if (protection != null) Main.getInstance().getProtection().put(blockLocation, protection);
            }
        }
    }

    private static void checkDrops(Player player, InventoryView view, Inventory physical, ItemStack reward,
                                   Class<?> profileType, Object profile) throws Exception {
        Item dropped = player.getWorld().dropItem(player.getLocation().add(0, 2, 0), reward);
        Item ordinary = player.getWorld().dropItem(player.getLocation().add(0, 2, 0), reward);
        try {
            fire(new PlayerDropItemEvent(player, dropped));
            check(LootRewardDropListener.isReward(dropped), "Dropping from an open LootChest lost provenance");
            check(dropped.getItemStack().equals(reward), "Drop marker changed item metadata");
            for (int repeat = 0; repeat < 10; repeat++) {
                EntityPickupItemEvent pickup = new EntityPickupItemEvent(player, dropped, 0);
                fire(pickup);
                check(pickup.isCancelled() && dropped.isValid(), "AutoSell ON picked up protected ground loot");
            }
            EntityPickupItemEvent unrelated = new EntityPickupItemEvent(player, ordinary, 0);
            fire(unrelated);
            check(!unrelated.isCancelled(), "Ordinary drops must remain available to AutoSell");
            for (ItemMergeEvent merge : List.of(new ItemMergeEvent(dropped, ordinary), new ItemMergeEvent(ordinary, dropped))) {
                fire(merge);
                check(merge.isCancelled(), "Merge lost the reward marker");
            }
            InventoryPickupItemEvent hopper = new InventoryPickupItemEvent(physical, dropped);
            fire(hopper);
            check(hopper.isCancelled(), "Hopper stripped protected ground loot identity");
            field(profileType, "enabled").setBoolean(profile, false);
            EntityPickupItemEvent retry = new EntityPickupItemEvent(player, dropped, 0);
            fire(retry);
            check(!retry.isCancelled(), "AutoSell OFF cannot pick up protected loot");
        } finally {
            dropped.remove();
            ordinary.remove();
        }
    }

    private static InventoryView view(Player player, Inventory top, Inventory bottom) {
        return (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(),
                new Class<?>[] {InventoryView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> top;
                    case "getBottomInventory" -> bottom;
                    case "getPlayer" -> player;
                    case "getType" -> InventoryType.CHEST;
                    case "convertSlot" -> (int) args[0] < top.getSize() ? args[0] : (int) args[0] - top.getSize();
                    case "getInventory" -> (int) args[0] < top.getSize() ? top : bottom;
                    case "getTitle", "getOriginalTitle" -> "AutoSell probe";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static void fire(Event event) throws Exception {
        // Real registered LootChest handlers, including cancellation/priority semantics.
        // Other plugins never receive these synthetic players or attempt real payouts.
        for (var listener : event.getHandlers().getRegisteredListeners()) {
            if (listener.getPlugin() == Main.getInstance()) listener.callEvent(event);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
