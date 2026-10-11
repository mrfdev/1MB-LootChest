package fr.black_eyes.lootchest.compat;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import fr.black_eyes.api.LootChestAPI;
import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.Lootchest;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;

/** Packaged only by the local smoke runner, never into the release JAR. */
public final class PaperRuntimeProbe extends JavaPlugin {
    private static final String FIXTURE = "stable_smoke_metadata";
    private static final NamespacedKey FOREIGN_KEY = new NamespacedKey("unrelated_plugin", "opaque");

    @Override
    public void onEnable() {
        check(Boolean.getBoolean("lootchest.smoke") && getServer().getIp().equals("127.0.0.1"),
                "The probe requires the explicitly opted-in loopback smoke server");
        Objects.requireNonNull(getCommand("lootchestprobe")).setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || args.length != 1) {
            return false;
        }
        try {
            switch (args[0]) {
                case "prepare" -> {
                    prepare();
                    verify();
                    getLogger().info("PROBE PASS prepare");
                }
                case "verify" -> {
                    verify();
                    getLogger().info("PROBE PASS metadata, nested items and inventory isolation");
                }
                case "async" -> {
                    // Intentionally call the plugin's fail-closed API from a
                    // worker. The null block must never reach Bukkit world code.
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        try {
                            check(LootChestAPI.createLootChest(null, "must_not_exist") == null,
                                    "Off-thread creation was accepted");
                            getLogger().info("PROBE PASS async API refused before world access");
                        } catch (Throwable failure) {
                            getLogger().log(Level.SEVERE, "PROBE FAIL async", failure);
                        }
                    });
                }
                default -> throw new IllegalArgumentException("Unknown probe operation");
            }
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE, "PROBE FAIL " + args[0], failure);
        }
        return true;
    }

    private static void prepare() {
        Lootchest previousFixture = LootChestAPI.getLootChest(FIXTURE);
        if (previousFixture != null) {
            check(previousFixture.getPosition().getWorld().getName().equals("spawn")
                    && previousFixture.getPosition().getBlockX() == 8
                    && previousFixture.getPosition().getBlockY() == 200
                    && previousFixture.getPosition().getBlockZ() == -40,
                    "Refuse to replace a fixture at an unexpected location");
            verify();
            LootChestAPI.removeLootChest(previousFixture);
            check(LootChestAPI.getLootChest(FIXTURE) == null, "Old test fixture was not removed");
        }
        check(!fr.black_eyes.lootchest.Main.getInstance().getConfigFiles().hasSavedChestDefinition(FIXTURE),
                "The probe name is reserved by an inactive definition");
        Block block = Objects.requireNonNull(Bukkit.getWorld("spawn")).getBlockAt(8, 200, -40);
        ItemStack[] items = expectedItems();
        if (block.getType().isAir()) {
            block.setType(Material.CHEST);
        } else {
            // A failed creation can leave our exact physical fixture behind.
            // Recover only that fixture; never replace arbitrary existing loot.
            check(block.getType() == Material.CHEST, "The disposable probe location is occupied");
            Inventory previous = ((InventoryHolder) block.getState()).getInventory();
            for (int slot = 0; slot < previous.getSize(); slot++) {
                ItemStack expected = slot < items.length ? items[slot] : null;
                check(Objects.equals(expected, previous.getItem(slot)),
                        "The occupied probe location contains unrelated items");
            }
        }
        Inventory inventory = ((InventoryHolder) block.getState()).getInventory();
        inventory.setItem(0, items[0]);
        inventory.setItem(1, items[1]);
        Lootchest chest = Objects.requireNonNull(LootChestAPI.createSavedLootChest(block, FIXTURE));
        check(chest.getWorld().equals(block.getWorld().getName()),
                "New chest world identity differs from its Bukkit saved name: " + chest.getWorld());
        chest.getChances()[0] = 100;
        chest.getChances()[1] = 100;
        chest.setMaxFilledSlots(0);
        chest.setTime(60);
        check(chest.spawn(true), "Probe chest could not spawn");
        LootChestAPI.saveLootChest(chest);
    }

    private static void verify() {
        Lootchest chest = Objects.requireNonNull(LootChestAPI.getLootChest(FIXTURE),
                "Metadata fixture missing after reload/restart");
        ItemStack[] expected = expectedItems();
        Inventory physical = ((InventoryHolder) chest.getActualLocation().getBlock().getState()).getInventory();
        Inventory delivery = Bukkit.createInventory(null, 27);
        LootChestUtils.fillInventory(chest, delivery, true, null);
        for (int slot = 0; slot < expected.length; slot++) {
            check(expected[slot].equals(chest.getInv().getItem(slot)), "Saved template metadata changed at " + slot);
            check(expected[slot].equals(physical.getItem(slot)), "Spawned item metadata changed at " + slot);
            check(expected[slot].equals(delivery.getItem(slot)), "Delivered item metadata changed at " + slot);
        }
        ItemStack delivered = delivery.getItem(0);
        Objects.requireNonNull(delivered).editPersistentDataContainer(pdc ->
                pdc.set(FOREIGN_KEY, PersistentDataType.STRING, "recipient-only-change"));
        delivery.setItem(0, delivered);
        check(expected[0].equals(chest.getInv().getItem(0)), "Delivery modified the reward template");
    }

    private static ItemStack[] expectedItems() {
        ItemStack tool = ItemStack.of(Material.DIAMOND_SWORD);
        tool.editMeta(meta -> {
            meta.displayName(Component.text("Stable compatibility fixture"));
            meta.lore(List.of(Component.text("Preserve third-party data")));
            meta.addEnchant(Enchantment.SHARPNESS, 3, true);
            ((Damageable) meta).setDamage(17);
        });
        tool.editPersistentDataContainer(pdc -> {
            pdc.set(FOREIGN_KEY, PersistentDataType.STRING, "opaque-third-party-value");
            pdc.set(new NamespacedKey("unrelated_plugin", "bytes"), PersistentDataType.BYTE_ARRAY,
                    new byte[] {0, 1, 42, -1});
        });
        tool.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addFloat(1.25F).addFlag(true).build());
        tool.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
        ItemStack container = ItemStack.of(Material.BLUE_SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) container.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        state.getInventory().setItem(4, tool.clone());
        meta.setBlockState(state);
        container.setItemMeta(meta);
        container.editPersistentDataContainer(pdc ->
                pdc.set(FOREIGN_KEY, PersistentDataType.STRING, "nested-container"));
        return new ItemStack[] {tool, container};
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
