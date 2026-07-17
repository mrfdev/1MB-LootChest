package fr.black_eyes.lootchest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MatTest {
    @Test
    void recognizesEverySupportedPaper262Container() {
        Set<Material> expected = EnumSet.of(
                Material.BARREL,
                Material.BLACK_SHULKER_BOX,
                Material.BLUE_SHULKER_BOX,
                Material.BROWN_SHULKER_BOX,
                Material.CHEST,
                Material.COPPER_CHEST,
                Material.CYAN_SHULKER_BOX,
                Material.EXPOSED_COPPER_CHEST,
                Material.GRAY_SHULKER_BOX,
                Material.GREEN_SHULKER_BOX,
                Material.LIGHT_BLUE_SHULKER_BOX,
                Material.LIGHT_GRAY_SHULKER_BOX,
                Material.LIME_SHULKER_BOX,
                Material.MAGENTA_SHULKER_BOX,
                Material.ORANGE_SHULKER_BOX,
                Material.OXIDIZED_COPPER_CHEST,
                Material.PINK_SHULKER_BOX,
                Material.PURPLE_SHULKER_BOX,
                Material.RED_SHULKER_BOX,
                Material.SHULKER_BOX,
                Material.TRAPPED_CHEST,
                Material.WAXED_COPPER_CHEST,
                Material.WAXED_EXPOSED_COPPER_CHEST,
                Material.WAXED_OXIDIZED_COPPER_CHEST,
                Material.WAXED_WEATHERED_COPPER_CHEST,
                Material.WEATHERED_COPPER_CHEST,
                Material.WHITE_SHULKER_BOX,
                Material.YELLOW_SHULKER_BOX);

        Set<Material> actual = Arrays.stream(Material.values())
                .filter(Mat::isLootChestMaterial)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Material.class)));

        assertEquals(expected, actual);
    }
}
