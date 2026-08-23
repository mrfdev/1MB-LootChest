package fr.black_eyes.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;

import fr.black_eyes.lootchest.LootChestUtils;
import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Mat;

@SuppressWarnings("unused")
public class LootChestAPI {

    private LootChestAPI() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Generates a random name for a lootchest
     * @return The name
     */
    public static String generateName(){
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    /**
     * Get all lootchests loaded within lootchest plugin
     * @return HashMap<String, Lootchest> containing all lootchests with thei name as key
     */
    public static Map<String, Lootchest> getAllLootChests() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Main.getInstance().getLootChest()));
    }

    /**
     * Get a lootchest by its name
     * @param name The name of the lootchest
     * @return The lootchest
     */
    public static Lootchest getLootChest(String name) {
        return getAllLootChests().get(name);
    }

    /**
     * Creates a lootchest from a chest block, but does not add it to the lootchest plugin.
     * @param chest A chest/barrel/trapped chest block containing some items
     * @param name The name of the lootchest
     * @return The lootchest
     */
    public static Lootchest createLootChest(Block chest, String name) {
        if (!requirePrimaryThread("create a LootChest")) {
            return null;
        }
        if (!Mat.isALootChestBlock(chest)) {
            Main.getInstance().getLogger().warning("The block is not a chest! No chest will be created.");
            return null;
        }
        if (!checkNameAvalability(name)) {
            Main.getInstance().getLogger().warning(
                    "The requested LootChest name is unsafe or already reserved. A random name will be generated.");
            do {
                name = generateName();
            } while (!checkNameAvalability(name));
        }
        return new Lootchest(chest,name);
    }

    /**
     * Creates a lootchest from a chest block and adds it to the lootchest plugin.
     * @param chest A chest/barrel/trapped chest block containing some items
     * @param name The name of the lootchest
     * @return The lootchest
     */
    public static Lootchest createSavedLootChest(Block chest, String name) {
        Lootchest lc = createLootChest(chest, name);
        if (lc != null) {
            addLootChest(lc.getName(), lc);
        }
        return lc;
    }    

    /**
     * Add a lootchest to the lootchest plugin.
     * 
     * @param name The name of the lootchest
     * @param lc The lootchest to add
     */
    public static void addLootChest(String name, Lootchest lc) {
        if (!requirePrimaryThread("register a LootChest")) {
            return;
        }
        if (Main.getInstance().isChestWorkInProgress()) {
            Main.getInstance().getLogger().warning(
                    "A LootChest cannot be registered while a reload or bulk operation is in progress.");
            return;
        }
        if (lc == null || name == null || !name.equals(lc.getName())) {
            Main.getInstance().getLogger().warning(
                    "A LootChest can only be added under its own non-null name.");
            return;
        }
        if (!Main.getInstance().getConfigFiles().getChestNameProblems(name).isEmpty()) {
            Main.getInstance().getLogger().warning("Unsafe LootChest name rejected: " + name);
            return;
        }
        if (Main.getInstance().getConfigFiles().hasSavedChestDefinition(name)
                && Main.getInstance().getLootChest().get(name) != lc) {
            Main.getInstance().getLogger().warning(
                    "Saved LootChest name is already reserved and cannot be overwritten through the API.");
            return;
        }
        Lootchest previous = Main.getInstance().getLootChest().put(name, lc);
        if (previous != null && previous != lc) {
            Main.getInstance().untrackLootChestLocation(previous);
        }
        Main.getInstance().trackLootChestLocation(lc);
    }

    /**
     * Despawns the chest and removes it from data file
     * @param name The name of the lootchest
     */
    public static void removeLootChest(String name) {
        if (!requirePrimaryThread("remove a LootChest")) {
            return;
        }
        Lootchest lc = getLootChest(name);
        if (lc != null) {
            lc.deleteChest();
        }
    }

    /**
     * Despawns the chest and removes it from data file
     * @param lc The lootchest
     */
    public static void removeLootChest(Lootchest lc) {
        if (lc != null) {
            removeLootChest(lc.getName());
        }
    }

    /**
     * Copies an existing lootchest to another existing lootchest
     * @param lc The lootchest to copy
     * @param secondLc The lootchest to copy to
     */
    public static void copyToExistingChest(Lootchest lc, Lootchest secondLc) {
        if (!requirePrimaryThread("copy a LootChest")) {
            return;
        }
        LootChestUtils.copychest(lc, secondLc);
    }

    /**
     * Copies an existing lootchest to a new lootchest, specifying the name of this chest
     * @param lc The lootchest to copy
     * @param newName The name of the new lootchest
     * @return The new lootchest
     */
    public static Lootchest copyToNewChest(Lootchest lc, String newName) {
        if (!requirePrimaryThread("copy a LootChest")) {
            return null;
        }
        if (!checkNameAvalability(newName)) {
            Main.getInstance().getLogger().warning(
                    "The requested LootChest name is unsafe or already reserved. A random name will be generated.");
            do {
                newName = generateName();
            } while (!checkNameAvalability(newName));
        }
        return new Lootchest(lc, newName);
    }

    /**
     * Saves the lootchest in data file, but it is already done automatically on shutdown, except if the lootchest wasn't added to the lootchest plugin
     * @param lc The lootchest
     */
    public static void saveLootChest(Lootchest lc) {
        if (!requirePrimaryThread("save a LootChest")) {
            return;
        }
        if (lc == null) {
            return;
        }
        // updateData enforces the same name, reservation, inactive-definition,
        // and reload-transaction policy as every other persistence entry point.
        lc.updateData();
    }

    /**
     * Saves all the lootchests in data file, but it is already done automatically on shutdown
     */
    public static void saveAllLootChests() {
        if (!requirePrimaryThread("save LootChests")) {
            return;
        }
        LootChestUtils.saveAllChests();
    }

    /**
     * Checks if a lootchest with the given name exists
     * @param name The name of the lootchest
     * @return true if the lootchest does not exist, false otherwise
     */
    private static boolean checkNameAvalability(String name) {
        return Main.getInstance().getConfigFiles().getChestNameProblems(name).isEmpty()
                && !Main.getInstance().getConfigFiles().hasSavedChestDefinition(name)
                && !getAllLootChests().containsKey(name);
    }

    private static boolean requirePrimaryThread(String action) {
        if (Bukkit.isPrimaryThread()) {
            return true;
        }
        Main.getInstance().getLogger().warning(
                "Developer API refused to " + action + " outside the Paper server thread.");
        return false;
    }
}
