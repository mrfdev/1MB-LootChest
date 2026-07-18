package fr.black_eyes.lootchest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MatTest {
    private static final Set<Material> BASE_CONTAINERS = EnumSet.of(
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL);

    private static final Set<Material> COPPER_CHESTS = EnumSet.of(
            Material.COPPER_CHEST,
            Material.EXPOSED_COPPER_CHEST,
            Material.WEATHERED_COPPER_CHEST,
            Material.OXIDIZED_COPPER_CHEST,
            Material.WAXED_COPPER_CHEST,
            Material.WAXED_EXPOSED_COPPER_CHEST,
            Material.WAXED_WEATHERED_COPPER_CHEST,
            Material.WAXED_OXIDIZED_COPPER_CHEST);

    private static final Set<Material> SHULKER_BOXES = EnumSet.of(
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX);

    @Test
    void recognizesEveryPaper262CopperChestState() {
        Set<Material> paperCopperChests = currentMaterialsEndingWith("COPPER_CHEST");

        assertEquals(COPPER_CHESTS, paperCopperChests);
        assertTrue(COPPER_CHESTS.stream().allMatch(Mat::isCopperChest));
        assertTrue(COPPER_CHESTS.stream().allMatch(expected ->
                COPPER_CHESTS.stream().allMatch(actual ->
                        Mat.matchesContainerType(expected, actual))));
    }

    @Test
    void recognizesEveryPaper262ShulkerBoxState() {
        Set<Material> paperShulkerBoxes = currentMaterialsEndingWith("SHULKER_BOX");

        assertEquals(SHULKER_BOXES, paperShulkerBoxes);
        assertTrue(SHULKER_BOXES.stream().allMatch(Mat::isShulkerBox));
    }

    @Test
    void recognizesExactlyTheSupportedPaper262Containers() {
        Set<Material> expected = supportedContainerMaterials()
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Material.class)));
        Set<Material> actual = Arrays.stream(Material.values())
                .filter(Mat::isLootChestMaterial)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Material.class)));

        assertEquals(expected, actual);
    }

    @ParameterizedTest(name = "{0} is a current Lootbox container")
    @MethodSource("supportedContainerMaterials")
    void everySupportedContainerIsCurrentAndClassified(Material material) {
        assertFalse(material.name().startsWith("LEGACY_"));
        assertTrue(Mat.isLootChestMaterial(material));
    }

    @Test
    void rejectsSimilarMaterialsThatAreNotSharedPhysicalContainers() {
        assertFalse(Mat.isLootChestMaterial(Material.ENDER_CHEST));
        assertFalse(Mat.isLootChestMaterial(Material.CHEST_MINECART));
        assertFalse(Mat.isLootChestMaterial(Material.HOPPER));
        assertFalse(Mat.isLootChestMaterial(Material.COPPER_CHESTPLATE));
        assertFalse(Mat.isLootChestMaterial(Material.SHULKER_SHELL));
    }

    @Test
    void onlyCopperOxidationAndWaxStatesCanDifferDuringContainerMatching() {
        assertTrue(Mat.matchesContainerType(Material.CHEST, Material.CHEST));
        assertTrue(Mat.matchesContainerType(
                Material.COPPER_CHEST,
                Material.EXPOSED_COPPER_CHEST));
        assertTrue(Mat.matchesContainerType(
                Material.WEATHERED_COPPER_CHEST,
                Material.WAXED_OXIDIZED_COPPER_CHEST));
        assertFalse(Mat.matchesContainerType(Material.CHEST, Material.TRAPPED_CHEST));
        assertFalse(Mat.matchesContainerType(
                Material.WHITE_SHULKER_BOX,
                Material.BLACK_SHULKER_BOX));
        assertFalse(Mat.matchesContainerType(Material.COPPER_CHEST, Material.BARREL));
        assertFalse(Mat.matchesContainerType(null, Material.COPPER_CHEST));
    }

    static Stream<Material> supportedContainerMaterials() {
        return Stream.of(BASE_CONTAINERS, COPPER_CHESTS, SHULKER_BOXES)
                .flatMap(Set::stream);
    }

    private Set<Material> currentMaterialsEndingWith(String suffix) {
        return Arrays.stream(Material.values())
                .filter(material -> !material.name().startsWith("LEGACY_"))
                .filter(material -> material.name().endsWith(suffix))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Material.class)));
    }
}
