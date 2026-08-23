package fr.black_eyes.lootchest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class SavedChestDataValidatorTest {

    @Test
    void acceptsLegacyDefaultsEmptyInventoryAndUnknownFields() throws Exception {
        YamlConfiguration data = yaml("""
                chests:
                  legacy:
                    position:
                      world: world
                      x: 12
                      y: 64.5
                      z: -8
                    randomradius: 5
                    chance:
                      13: 75
                    custom-third-party-field:
                      keep: true
                """);

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.acceptedNames().contains("legacy"));
        assertTrue(result.rejectedDefinitions().isEmpty());
    }

    @Test
    void rejectsNonItemInventoryValuesButIgnoresStaleChanceOnlySlots() throws Exception {
        YamlConfiguration data = validData("items");
        data.set("chests.items.inventory.13", "DIAMOND");
        data.set("chests.items.chance.13", 50);
        data.set("chests.items.chance.0", "stale-value-is-never-read");

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertFalse(result.acceptedNames().contains("items"));
        assertTrue(result.rejectedDefinitions().get("items").stream()
                .anyMatch(problem -> problem.contains("serialized Bukkit ItemStack")));
        assertFalse(result.rejectedDefinitions().get("items").stream()
                .anyMatch(problem -> problem.contains("chance.0")));
    }

    @Test
    void rejectsOnlyTheMalformedSiblingAndReportsEveryDangerousPath() throws Exception {
        YamlConfiguration data = validData("valid");
        data.set("chests.broken.position.world", "world");
        data.set("chests.broken.position.x", "not-a-number");
        data.set("chests.broken.position.y", 64);
        data.set("chests.broken.position.z", Double.NaN);
        data.set("chests.broken.type", "STONE");
        data.set("chests.broken.inventory.bad-slot", "STONE");
        data.set("chests.broken.inventory.27", "STONE");

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);
        String problems = String.join("\n", result.rejectedDefinitions().get("broken"));

        assertTrue(result.acceptedNames().contains("valid"));
        assertFalse(result.acceptedNames().contains("broken"));
        assertTrue(problems.contains("position.x"));
        assertTrue(problems.contains("position.z"));
        assertTrue(problems.contains("type"));
        assertTrue(problems.contains("inventory.bad-slot"));
        assertTrue(problems.contains("inventory.27"));
    }

    @Test
    void allowsOrdinaryWhitespaceInSavedNamesButRejectsBlankDotAndControlNames() {
        assertTrue(SavedChestDataValidator.validateName("ordinary chest name").isEmpty());
        assertTrue(SavedChestDataValidator.validateName("\t \n").stream()
                .anyMatch(problem -> problem.contains("blank")));
        assertTrue(SavedChestDataValidator.validateName("unsafe.name").stream()
                .anyMatch(problem -> problem.contains("YAML path separator")));
        assertTrue(SavedChestDataValidator.validateName("unsafe\nname").stream()
                .anyMatch(problem -> problem.contains("control characters")));
    }

    @Test
    void rejectsWhitespaceInNewNamesThatCommandsMustAddress() {
        assertTrue(SavedChestDataValidator.validateNewName("ordinary-name").isEmpty());
        assertTrue(SavedChestDataValidator.validateNewName("ordinary name").stream()
                .anyMatch(problem -> problem.contains("whitespace")));
    }

    @Test
    void rejectsScalarDefinitionsAndAnchoredPartialRandomPositions() throws Exception {
        YamlConfiguration data = yaml("""
                chests:
                  scalar: broken
                  partial-random:
                    position:
                      world: world
                      x: 0
                      y: 64
                      z: 0
                    randomradius: 10
                    randomPosition:
                      x: 3
                """);

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.rejectedDefinitions().get("scalar").stream()
                .anyMatch(problem -> problem.contains("YAML section")));
        List<String> randomProblems = result.rejectedDefinitions().get("partial-random");
        assertTrue(randomProblems.stream().anyMatch(problem -> problem.contains("randomPosition.world")));
        assertTrue(randomProblems.stream().anyMatch(problem -> problem.contains("randomPosition.y")));
        assertTrue(randomProblems.stream().anyMatch(problem -> problem.contains("randomPosition.z")));
    }

    @Test
    void requiresNonNegativeIntegralRandomRadius() throws Exception {
        YamlConfiguration data = validData("negative");
        data.set("chests.negative.randomradius", -1);
        addValidDefinition(data, "fractional");
        data.set("chests.fractional.randomradius", new BigDecimal("1.0000000000000000001"));
        addValidDefinition(data, "text");
        data.set("chests.text.randomradius", "10");
        addValidDefinition(data, "overflow");
        data.set("chests.overflow.randomradius", new BigInteger("2147483648"));

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.rejectedDefinitions().get("negative").stream()
                .anyMatch(problem -> problem.contains("randomradius")));
        assertTrue(result.rejectedDefinitions().get("fractional").stream()
                .anyMatch(problem -> problem.contains("randomradius")));
        assertTrue(result.rejectedDefinitions().get("text").stream()
                .anyMatch(problem -> problem.contains("randomradius")));
        assertTrue(result.rejectedDefinitions().get("overflow").stream()
                .anyMatch(problem -> problem.contains("randomradius")));
    }

    @Test
    void ignoresInactiveAndUnanchoredStaleRandomPositions() throws Exception {
        YamlConfiguration data = validData("inactive");
        data.set("chests.inactive.randomradius", 0);
        data.set("chests.inactive.randomPosition.x", "stale");
        data.set("chests.inactive.randomPosition.world", List.of("also", "stale"));
        addValidDefinition(data, "unanchored");
        data.set("chests.unanchored.randomradius", 10);
        data.set("chests.unanchored.randomPosition.world", 42);
        data.set("chests.unanchored.randomPosition.y", "stale");
        data.set("chests.unanchored.randomPosition.z", Double.NaN);

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.acceptedNames().contains("inactive"));
        assertTrue(result.acceptedNames().contains("unanchored"));
    }

    @Test
    void ignoresStaleChanceScalarWhenInventoryIsEmpty() throws Exception {
        YamlConfiguration data = validData("empty-inventory");
        data.createSection("chests.empty-inventory.inventory");
        data.set("chests.empty-inventory.chance", "stale-and-unused");

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.acceptedNames().contains("empty-inventory"));
    }

    @Test
    void acceptsUniqueParseableSlotAliasesButRejectsAmbiguousAndInvalidSlots() throws Exception {
        YamlConfiguration data = validData("aliases");
        data.set("chests.aliases.inventory.00", itemStack());
        data.set("chests.aliases.chance.00", 25);
        data.set("chests.aliases.inventory.+1", itemStack());
        data.set("chests.aliases.chance.+1", 50);

        addValidDefinition(data, "duplicate");
        data.set("chests.duplicate.inventory.1", itemStack());
        data.set("chests.duplicate.inventory.01", itemStack());

        addValidDefinition(data, "out-of-range");
        data.set("chests.out-of-range.inventory.27", itemStack());

        addValidDefinition(data, "noninteger");
        data.set("chests.noninteger.inventory.one", itemStack());

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.acceptedNames().contains("aliases"));
        assertTrue(result.rejectedDefinitions().get("duplicate").stream()
                .anyMatch(problem -> problem.contains("duplicates numeric slot 1")));
        assertTrue(result.rejectedDefinitions().get("out-of-range").stream()
                .anyMatch(problem -> problem.contains("inventory.27")));
        assertTrue(result.rejectedDefinitions().get("noninteger").stream()
                .anyMatch(problem -> problem.contains("inventory.one")));
    }

    @Test
    void preservesUnknownMaterialFallbackButRejectsKnownNonContainers() throws Exception {
        YamlConfiguration data = validData("removed-material");
        data.set("chests.removed-material.type", "REMOVED_CONTAINER_FROM_OLD_SERVER");
        addValidDefinition(data, "known-non-container");
        data.set("chests.known-non-container.type", "STONE");

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.acceptedNames().contains("removed-material"));
        assertTrue(result.rejectedDefinitions().get("known-non-container").stream()
                .anyMatch(problem -> problem.contains("type")));
    }

    @Test
    void ignoresInertFallAndAcceptsScalarCosmeticValues() throws Exception {
        YamlConfiguration data = validData("legacy-scalars");
        data.set("chests.legacy-scalars.fall.obsolete", List.of("anything"));
        data.set("chests.legacy-scalars.holo", 123);
        data.set("chests.legacy-scalars.particle", true);
        data.set("chests.legacy-scalars.direction", 1.5);

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.acceptedNames().contains("legacy-scalars"));
    }

    @Test
    void rejectsComplexCosmeticValues() throws Exception {
        YamlConfiguration data = validData("complex-holo");
        data.set("chests.complex-holo.holo", List.of("line"));
        addValidDefinition(data, "complex-particle");
        data.set("chests.complex-particle.particle.value", "FLAME");
        addValidDefinition(data, "complex-direction");
        data.set("chests.complex-direction.direction", List.of("NORTH"));

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.rejectedDefinitions().get("complex-holo").stream()
                .anyMatch(problem -> problem.contains("holo")));
        assertTrue(result.rejectedDefinitions().get("complex-particle").stream()
                .anyMatch(problem -> problem.contains("particle")));
        assertTrue(result.rejectedDefinitions().get("complex-direction").stream()
                .anyMatch(problem -> problem.contains("direction")));
    }

    @Test
    void validatesIntegralRangesWithoutDoubleRounding() throws Exception {
        YamlConfiguration data = validData("exact-limits");
        data.set("chests.exact-limits.randomradius", Integer.MAX_VALUE);
        data.set("chests.exact-limits.maxFilledSlots", Integer.MIN_VALUE);
        data.set("chests.exact-limits.time", Integer.MAX_VALUE);
        data.set("chests.exact-limits.protectionTime", Long.MAX_VALUE);
        data.set("chests.exact-limits.lastreset", Long.MIN_VALUE);

        addValidDefinition(data, "long-overflow");
        data.set("chests.long-overflow.protectionTime", new BigInteger("9223372036854775808"));

        addValidDefinition(data, "long-fraction");
        data.set("chests.long-fraction.lastreset", new BigDecimal("9223372036854775807.1"));

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.acceptedNames().contains("exact-limits"));
        assertTrue(result.rejectedDefinitions().get("long-overflow").stream()
                .anyMatch(problem -> problem.contains("protectionTime")));
        assertTrue(result.rejectedDefinitions().get("long-fraction").stream()
                .anyMatch(problem -> problem.contains("lastreset")));
    }

    @Test
    void requiresFiniteCoordinatesWithinSafeWorldAndIntegerBounds() throws Exception {
        YamlConfiguration data = validData("coordinate-limits");
        data.set("chests.coordinate-limits.position.x", -30_000_000);
        data.set("chests.coordinate-limits.position.y", Integer.MAX_VALUE);
        data.set("chests.coordinate-limits.position.z", new BigDecimal("29999999.999999"));

        addValidDefinition(data, "horizontal-overflow");
        data.set("chests.horizontal-overflow.position.x", new BigDecimal("30000000"));

        addValidDefinition(data, "vertical-overflow");
        data.set("chests.vertical-overflow.position.y", new BigInteger("2147483648"));

        addValidDefinition(data, "non-finite");
        data.set("chests.non-finite.position.z", Double.POSITIVE_INFINITY);

        addValidDefinition(data, "random-horizontal-overflow");
        data.set("chests.random-horizontal-overflow.randomradius", 1);
        data.set("chests.random-horizontal-overflow.randomPosition.world", "world");
        data.set("chests.random-horizontal-overflow.randomPosition.x", -30_000_001);
        data.set("chests.random-horizontal-overflow.randomPosition.y", 64);
        data.set("chests.random-horizontal-overflow.randomPosition.z", 0);

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);

        assertTrue(result.acceptedNames().contains("coordinate-limits"));
        assertTrue(result.rejectedDefinitions().get("horizontal-overflow").stream()
                .anyMatch(problem -> problem.contains("position.x")));
        assertTrue(result.rejectedDefinitions().get("vertical-overflow").stream()
                .anyMatch(problem -> problem.contains("position.y")));
        assertTrue(result.rejectedDefinitions().get("non-finite").stream()
                .anyMatch(problem -> problem.contains("position.z")));
        assertTrue(result.rejectedDefinitions().get("random-horizontal-overflow").stream()
                .anyMatch(problem -> problem.contains("randomPosition.x")));
    }

    @Test
    void blocksWrongTypesForBehaviorCriticalBooleansAndIntegers() throws Exception {
        YamlConfiguration data = validData("wrong-types");
        data.set("chests.wrong-types.time", "10");
        data.set("chests.wrong-types.respawn_cmd", "true");

        SavedChestDataValidator.Result result = SavedChestDataValidator.validate(data);
        List<String> problems = result.rejectedDefinitions().get("wrong-types");

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("time")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("respawn_cmd")));
    }

    private YamlConfiguration validData(String chestName) throws Exception {
        return yaml("""
                chests:
                  %s:
                    type: CHEST
                    position:
                      world: world
                      x: 1.5
                      y: 65
                      z: -2.5
                """.formatted(chestName));
    }

    private void addValidDefinition(YamlConfiguration data, String chestName) {
        String basePath = "chests." + chestName;
        data.set(basePath + ".type", "CHEST");
        data.set(basePath + ".position.world", "world");
        data.set(basePath + ".position.x", 1.5);
        data.set(basePath + ".position.y", 65);
        data.set(basePath + ".position.z", -2.5);
    }

    private ItemStack itemStack() {
        return new TestItemStack();
    }

    private YamlConfiguration yaml(String source) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(source);
        return configuration;
    }

    private static final class TestItemStack extends ItemStack {
        private TestItemStack() {
            super();
        }
    }
}
