package fr.black_eyes.lootchest;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Materials used by LootChest's menus and container checks on Paper 26.2.
 *
 * <p>The field names retain the plugin's historical names so saved data and the
 * rest of the code do not need a migration.</p>
 */
public final class Mat {
    public static final Material WITCH_SPAWN_EGG = Material.WITCH_SPAWN_EGG;
    public static final Material COBWEB = Material.COBWEB;
    public static final Material SILVERFISH_SPAWN_EGG = Material.SILVERFISH_SPAWN_EGG;
    public static final Material ELDER_GUARDIAN_SPAWN_EGG = Material.ELDER_GUARDIAN_SPAWN_EGG;
    public static final Material SCULK = Material.SCULK;
    public static final Material SCULK_SHRIEKER = Material.SCULK_SHRIEKER;
    public static final Material WHITE_DYE = Material.GRAY_DYE;
    public static final Material RED_CONCRETE = Material.RED_CONCRETE;
    public static final Material VILLAGER_SPAWN_EGG = Material.VILLAGER_SPAWN_EGG;
    public static final Material WARPED_FUNGUS = Material.WARPED_FUNGUS;
    public static final Material LIGHTNING_ROD = Material.LIGHTNING_ROD;
    public static final Material POINTED_DRIPSTONE = Material.POINTED_DRIPSTONE;
    public static final Material SOUL_LANTERN = Material.SOUL_LANTERN;
    public static final Material WATER = Material.WATER_BUCKET;
    public static final Material INK_SACK = Material.INK_SAC;
    public static final Material NAUTILUS_SHELL = Material.NAUTILUS_SHELL;
    public static final Material SPAWNER = Material.SPAWNER;
    public static final Material GLOW_INK_SAC = Material.GLOW_INK_SAC;
    public static final Material SPORE_BLOSSOM = Material.SPORE_BLOSSOM;
    public static final Material END_ROD = Material.END_ROD;
    public static final Material CRYING_OBSIDIAN = Material.CRYING_OBSIDIAN;
    public static final Material HONEY_BLOCK = Material.HONEYCOMB;
    public static final Material DRAGONS_BREATH = Material.DRAGON_BREATH;
    public static final Material DOLPHIN_SPAWN_EGG = Material.DOLPHIN_SPAWN_EGG;
    public static final Material CRIMSON_FUNGUS = Material.CRIMSON_FUNGUS;
    public static final Material COMPOSTER = Material.COMPOSTER;
    public static final Material CAMPFIRE = Material.CAMPFIRE;
    public static final Material BUBBLE_CORAL = Material.BUBBLE_CORAL;
    public static final Material SOUL_SAND = Material.SOUL_SAND;
    public static final Material EMERALD_BLOCK = Material.EMERALD_BLOCK;
    public static final Material STICK = Material.STICK;
    public static final Material GOLD_NUGGET = Material.GOLD_NUGGET;
    public static final Material GOLD_INGOT = Material.GOLD_INGOT;
    public static final Material GOLD_BLOCK = Material.GOLD_BLOCK;
    public static final Material TOTEM_OF_UNDYING = Material.TOTEM_OF_UNDYING;
    public static final Material CHEST = Material.CHEST;
    public static final Material CLOCK = Material.CLOCK;
    public static final Material DIAMOND = Material.DIAMOND;
    public static final Material ENDER_CHEST = Material.ENDER_CHEST;
    public static final Material ENDER_EYE = Material.ENDER_EYE;
    public static final Material SIGN = Material.OAK_SIGN;
    public static final Material TNT = Material.TNT;
    public static final Material FIREWORK = Material.FIREWORK_ROCKET;
    public static final Material PRISMARINE = Material.PRISMARINE_CRYSTALS;
    public static final Material MYCELIUM = Material.MYCELIUM;
    public static final Material IRON_SWORD = Material.IRON_SWORD;
    public static final Material DIAMOND_SWORD = Material.DIAMOND_SWORD;
    public static final Material FURNACE = Material.FURNACE;
    public static final Material ENCHANTED_BOOK = Material.ENCHANTED_BOOK;
    public static final Material NOTE_BLOCK = Material.NOTE_BLOCK;
    public static final Material END_PORTAL_FRAME = Material.END_PORTAL_FRAME;
    public static final Material ENCHANTING_TABLE = Material.ENCHANTING_TABLE;
    public static final Material BLAZE_POWDER = Material.BLAZE_POWDER;
    public static final Material STONE = Material.STONE;
    public static final Material QUARTZ = Material.QUARTZ;
    public static final Material SNOW_BALL = Material.SNOWBALL;
    public static final Material IRON_SHOVEL = Material.IRON_SHOVEL;
    public static final Material SLIME_BALL = Material.SLIME_BALL;
    public static final Material ROSE_RED = Material.RED_TULIP;
    public static final Material REDSTONE_BLOCK = Material.REDSTONE_BLOCK;
    public static final Material BARRIER = Material.BARRIER;
    public static final Material EMERALD = Material.EMERALD;
    public static final Material REDSTONE = Material.REDSTONE;
    public static final Material LEAVES = Material.OAK_LEAVES;
    public static final Material TRAPPED_CHEST = Material.TRAPPED_CHEST;
    public static final Material BARREL = Material.BARREL;

    private Mat() {
    }

    public static boolean isALootChestBlock(Block block) {
        Material type = block.getType();
        return isShulkerBox(type)
                || isCopperChest(type)
                || type == CHEST
                || type == TRAPPED_CHEST
                || type == BARREL;
    }

    public static boolean isShulkerBox(Material material) {
        return material.isBlock()
                && material.name().endsWith("SHULKER_BOX")
                && !material.name().startsWith("LEGACY_");
    }

    public static boolean isCopperChest(Material material) {
        return material.isBlock() && material.name().endsWith("COPPER_CHEST");
    }
}
