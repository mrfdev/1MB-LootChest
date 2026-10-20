package fr.black_eyes.lootchest.compat;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import fr.black_eyes.api.LootChestAPI;
import fr.black_eyes.lootchest.Config;
import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Messages;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.ContainerState;
import fr.black_eyes.lootchest.lifecycle.LifecycleAuditor;
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
                case "copper" -> {
                    verifyCopperReactivation();
                    getLogger().info("PROBE PASS all eight copper states reactivate effects; barrel rejected");
                }
                case "holograms", "despawned", "timer" -> {
                    check(Bukkit.getPluginManager().isPluginEnabled("CMI")
                                    && Bukkit.getPluginManager().isPluginEnabled("CMILib"),
                            "The integrated probe requires both optional plugins");
                    // Resolve CMI classes only on this integrated path so the
                    // same probe also loads with no optional plugins installed.
                    CmiChecks.run(this, args[0]);
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

    private static void verifyCopperReactivation() {
        verify();
        Lootchest fixture = Objects.requireNonNull(LootChestAPI.getLootChest(FIXTURE));
        Block block = fixture.getActualLocation().getBlock();
        BlockState previousBlock = block.getState();
        check(previousBlock instanceof Container, "The disposable metadata fixture must be a container");
        Material previousType = fixture.getType();
        boolean previousHologram = fixture.getHologram().isActive();
        var particles = Main.getInstance().getPart();
        var particleLocation = fixture.getParticleLocation();
        var previousParticle = particles.get(particleLocation);
        try {
            // Snapshot the original tile state before emptying the live block.
            // Type changes must not drop the fixture's metadata-bearing rewards.
            ((Container) block.getState()).getInventory().clear();
            fixture.setType(Material.COPPER_CHEST);
            for (Material state : List.of(
                    Material.COPPER_CHEST, Material.EXPOSED_COPPER_CHEST,
                    Material.WEATHERED_COPPER_CHEST, Material.OXIDIZED_COPPER_CHEST,
                    Material.WAXED_COPPER_CHEST, Material.WAXED_EXPOSED_COPPER_CHEST,
                    Material.WAXED_WEATHERED_COPPER_CHEST, Material.WAXED_OXIDIZED_COPPER_CHEST)) {
                block.setType(state, false);
                check(fixture.isGoodType(block),
                        "Saved COPPER_CHEST rejects its physical oxidation/wax state: " + state);
                fixture.getHologram().remove();
                particles.remove(particleLocation);
                fixture.reactivateEffects();
                if (Main.configs.usehologram) {
                    CmiChecks.verifyFixtureRegistration(fixture, state);
                } else {
                    check(!fixture.getHologram().isActive(),
                            "Standalone reactivation unexpectedly enabled a hologram");
                }
                if (fixture.getParticle() != null) {
                    check(particles.get(particleLocation) == fixture.getParticle(),
                            "Copper reactivation lost particles for " + state);
                }
            }
            block.setType(Material.BARREL, false);
            check(!fixture.isGoodType(block), "A barrel was accepted as the saved copper chest");
            fixture.getHologram().remove();
            particles.remove(particleLocation);
            fixture.reactivateEffects();
            check(!fixture.getHologram().isActive() && !particles.containsKey(particleLocation),
                    "An unrelated barrel reactivated copper chest effects");
        } finally {
            fixture.getHologram().remove();
            fixture.setType(previousType);
            check(previousBlock.update(true, false), "Could not restore the original metadata fixture block");
            if (previousHologram) {
                fixture.reactivateEffects();
            }
            if (previousParticle == null) {
                particles.remove(particleLocation);
            } else {
                particles.put(particleLocation, previousParticle);
            }
            verify();
        }
    }

    private static final class CmiChecks {
        private static final String PREFIX = "LootChest-";

        private static void verifyFixtureRegistration(Lootchest fixture, Material state) {
            var registered = com.Zrips.CMI.CMI.getInstance().getHologramManager()
                    .getByName(PREFIX + FIXTURE);
            check(fixture.getHologram().isActive() && registered != null,
                    "Copper reactivation did not register its hologram for " + state);
            check(!registered.getSettings().isSaveToFile(),
                    "Copper reactivation made its hologram persistent for " + state);
        }

        private static void run(PaperRuntimeProbe probe, String operation) throws InvalidConfigurationException {
            var manager = com.Zrips.CMI.CMI.getInstance().getHologramManager();
            check(manager != null && Main.configs.usehologram, "CMI integration is unavailable");
            Set<String> expected = new HashSet<>();
            for (Lootchest chest : LootChestAPI.getAllLootChests().values()) {
                String name = PREFIX + chest.getName();
                var registered = manager.getByName(name);
                var snapshot = LifecycleAuditor.inspect(Main.getInstance(), chest).snapshot();
                String detail = name + " (ownHandle=" + chest.getHologram().isActive()
                        + ", state=" + snapshot.containerState() + ", actual=" + snapshot.actualType()
                        + ", registered=" + (registered != null) + ")";
                if (operation.equals("despawned")) {
                    check(!chest.getHologram().isActive(), "LootChest retained a despawned hologram: " + detail);
                    check(!Main.getInstance().getTaskRegistry().hasTask("hologram-timer:" + chest.getName()),
                            "LootChest retained a despawned timer: " + name);
                } else if (snapshot.containerState() == ContainerState.PRESENT && snapshot.hologramExpected()) {
                    expected.add(name);
                    check(chest.getHologram().isActive() && registered != null,
                            "CMI lost an active LootChest hologram: " + detail
                                    + "; registry=" + manager.getHolograms().keySet());
                    check(manager.getHolograms().values().stream().anyMatch(value -> value == registered),
                            "CMI name lookup and registry disagree: " + name);
                    check(!registered.getSettings().isSaveToFile(), "Transient hologram became persistent: " + name);
                    check(registered.getWorldName().equals(chest.getActualLocation().getWorld().getName()),
                            "Hologram world changed: " + name);
                } else {
                    check(snapshot.containerState() != ContainerState.UNAVAILABLE,
                            "Cannot inspect hologram ownership in an unloaded fixture chunk: " + detail);
                    check(!chest.getHologram().isActive(),
                            "LootChest retained a hologram for an inactive container: " + detail);
                }
            }
            Set<String> actual = new HashSet<>();
            for (var registered : manager.getHolograms().values()) {
                if (registered.getName().startsWith(PREFIX)) {
                    check(actual.add(registered.getName()), "Duplicate CMI hologram: " + registered.getName());
                }
            }
            check(actual.equals(expected), "CMI registry differs: expected " + expected + ", actual " + actual);
            if (operation.equals("despawned")) {
                probe.getLogger().info("PROBE PASS CMI registry empty after despawn");
                return;
            }
            check(!expected.isEmpty(), "Integrated smoke did not exercise any holograms");
            Lootchest fixture = Objects.requireNonNull(LootChestAPI.getLootChest(FIXTURE));
            var registered = Objects.requireNonNull(manager.getByName(PREFIX + FIXTURE));
            String originalText = fixture.getHologram().getText();
            String changed = "<#55ff55>CMI text-update probe";
            try {
                fixture.getHologram().setText(changed);
                check(manager.getByName(PREFIX + FIXTURE) == registered,
                        "Text update unexpectedly replaced the registered hologram");
                check(registered.getPages().getLines().equals(List.of(Messages.legacy(changed))),
                        "LootChest updated a stale CMI object instead of the registered hologram");
            } finally {
                fixture.getHologram().setText(originalText);
            }
            if (operation.equals("timer")) {
                verifyTimer(probe, fixture);
            } else {
                probe.getLogger().info("PROBE PASS CMI registry, transience and text updates");
            }
        }

        private static void verifyTimer(PaperRuntimeProbe probe, Lootchest fixture)
                throws InvalidConfigurationException {
            Config previousConfig = Main.configs;
            boolean previousTimer = previousConfig.timerShowTimer;
            check(previousConfig.timerFormat.contains("%Seconds"),
                    "The smoke timer format must expose seconds");
            YamlConfiguration timerConfiguration = new YamlConfiguration();
            timerConfiguration.loadFromString(Main.getInstance().getConfigFiles().getConfig().saveToString());
            timerConfiguration.set("Timer_on_hologram.Show_Timer_On_Hologram", true);
            Config timerConfig = new Config(timerConfiguration);
            timerConfig.usehologram = previousConfig.usehologram;
            timerConfig.worldborderCheckForSpawn = previousConfig.worldborderCheckForSpawn;
            // Start a timer only for this disposable fixture. Never rewrite the
            // saved configuration or mutate its immutable snapshot. This entire
            // replacement and restoration runs in one server-thread operation.
            try {
                Main.configs = timerConfig;
                fixture.getHologram().setLoc(fixture.getActualLocation());
            } finally {
                Main.configs = previousConfig;
            }
            String timerKey = "hologram-timer:" + FIXTURE;
            check(Main.getInstance().getTaskRegistry().hasTask(timerKey), "Fixture timer was not registered");
            probe.getServer().getScheduler().runTaskLater(probe, () -> {
                try {
                    var manager = com.Zrips.CMI.CMI.getInstance().getHologramManager();
                    var hologram = Objects.requireNonNull(manager.getByName(PREFIX + FIXTURE));
                    List<String> first = List.copyOf(hologram.getPages().getLines());
                    probe.getServer().getScheduler().runTaskLater(probe, () -> {
                        try {
                            check(manager.getByName(PREFIX + FIXTURE) == hologram,
                                    "Timer replaced or lost its registered hologram");
                            List<String> later = List.copyOf(hologram.getPages().getLines());
                            check(!first.equals(later), "Timer text did not advance");
                            check(later.stream().noneMatch(line -> line.contains("%Seconds")
                                            || line.contains("%Hologram")),
                                    "Timer left unresolved placeholders");
                            fixture.getHologram().setLoc(fixture.getActualLocation());
                            check(Main.getInstance().getTaskRegistry().hasTask(timerKey) == previousTimer,
                                    "Temporary timer state was not restored");
                            probe.getLogger().info("PROBE PASS CMI timer updates and cleanup");
                        } catch (Throwable failure) {
                            probe.getLogger().log(Level.SEVERE, "PROBE FAIL timer", failure);
                        } finally {
                            fixture.getHologram().setLoc(fixture.getActualLocation());
                        }
                    }, 45L);
                } catch (Throwable failure) {
                    fixture.getHologram().setLoc(fixture.getActualLocation());
                    probe.getLogger().log(Level.SEVERE, "PROBE FAIL timer", failure);
                }
            }, 2L);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
