package fr.black_eyes.lootchest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Performs non-mutating structural validation of saved LootChest definitions.
 *
 * <p>Document-level YAML failures are handled by {@link LootChestFiles}. A bad
 * child definition is deliberately reported here instead of being promoted to
 * a document failure, because restoring the whole file would also roll back
 * unrelated valid chests.</p>
 */
final class SavedChestDataValidator {
    private static final int INVENTORY_SIZE = 27;
    private static final BigDecimal MIN_HORIZONTAL_COORDINATE = BigDecimal.valueOf(-30_000_000L);
    private static final BigDecimal MAX_HORIZONTAL_COORDINATE_EXCLUSIVE = BigDecimal.valueOf(30_000_000L);
    private static final BigDecimal MIN_VERTICAL_COORDINATE = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal MAX_VERTICAL_COORDINATE = BigDecimal.valueOf(Integer.MAX_VALUE);

    private SavedChestDataValidator() {
    }

    static Result validate(ConfigurationSection data) {
        ConfigurationSection chests = data.getConfigurationSection("chests");
        if (chests == null) {
            return new Result(Set.of(), Map.of());
        }

        Set<String> acceptedNames = new LinkedHashSet<>();
        Map<String, List<String>> rejectedDefinitions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : chests.getValues(false).entrySet()) {
            String chestName = entry.getKey();
            List<String> problems = validateDefinition(chestName, entry.getValue());
            if (problems.isEmpty()) {
                acceptedNames.add(chestName);
            } else {
                rejectedDefinitions.put(chestName, problems);
            }
        }
        return new Result(acceptedNames, rejectedDefinitions);
    }

    private static List<String> validateDefinition(String chestName, Object rawDefinition) {
        List<String> problems = new ArrayList<>(validateName(chestName));
        if (!(rawDefinition instanceof ConfigurationSection definition)) {
            problems.add("definition must be a YAML section");
            return List.copyOf(problems);
        }

        Map<String, Object> values = definition.getValues(false);
        Object rawPosition = values.get("position");
        if (!(rawPosition instanceof ConfigurationSection position)) {
            if (values.containsKey("location")) {
                problems.add("position is missing; the obsolete location format must be migrated");
            } else {
                problems.add("position must be a YAML section");
            }
        } else {
            validateWorld(position, "position.world", problems);
            validateRequiredCoordinate(
                    position,
                    "x",
                    "position.x",
                    MIN_HORIZONTAL_COORDINATE,
                    MAX_HORIZONTAL_COORDINATE_EXCLUSIVE,
                    true,
                    problems);
            validateRequiredCoordinate(
                    position,
                    "y",
                    "position.y",
                    MIN_VERTICAL_COORDINATE,
                    MAX_VERTICAL_COORDINATE,
                    false,
                    problems);
            validateRequiredCoordinate(
                    position,
                    "z",
                    "position.z",
                    MIN_HORIZONTAL_COORDINATE,
                    MAX_HORIZONTAL_COORDINATE_EXCLUSIVE,
                    true,
                    problems);
            validateOptionalCoordinate(position, "pitch", "position.pitch", problems);
            validateOptionalCoordinate(position, "yaw", "position.yaw", problems);
        }

        validateContainerType(values.get("type"), values.containsKey("type"), problems);
        Long randomRadius = validateOptionalInteger(
                values,
                "randomradius",
                0,
                Integer.MAX_VALUE,
                problems);
        validateOptionalInteger(values, "maxFilledSlots", Integer.MIN_VALUE, Integer.MAX_VALUE, problems);
        validateOptionalInteger(values, "time", Integer.MIN_VALUE, Integer.MAX_VALUE, problems);
        validateOptionalInteger(values, "protectionTime", Long.MIN_VALUE, Long.MAX_VALUE, problems);
        validateOptionalInteger(values, "lastreset", Long.MIN_VALUE, Long.MAX_VALUE, problems);
        validateOptionalScalar(values, "holo", problems);
        validateOptionalScalar(values, "particle", problems);
        validateOptionalScalar(values, "direction", problems);
        validateOptionalType(values, "respawn_cmd", Boolean.class, problems);
        validateOptionalType(values, "respawn_natural", Boolean.class, problems);
        validateOptionalType(values, "take_message", Boolean.class, problems);

        validateRandomPosition(
                values.get("randomPosition"),
                randomRadius != null && randomRadius > 0,
                problems);
        validateInventory(values.get("inventory"), values.containsKey("inventory"), values.get("chance"), problems);
        return List.copyOf(problems);
    }

    static List<String> validateName(String name) {
        List<String> problems = new ArrayList<>();
        if (name == null) {
            return List.of("name must not be null");
        }
        if (name.isBlank()) {
            problems.add("name must not be blank");
        }
        if (name.indexOf('.') >= 0) {
            problems.add("name must not contain '.' because it is the YAML path separator");
        }
        if (name.codePoints().anyMatch(Character::isISOControl)) {
            problems.add("name must not contain control characters");
        }
        return List.copyOf(problems);
    }

    static List<String> validateNewName(String name) {
        List<String> problems = new ArrayList<>(validateName(name));
        if (name != null && name.codePoints()
                .anyMatch(codePoint -> Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint))) {
            problems.add("new names must not contain whitespace because commands cannot address them");
        }
        return List.copyOf(problems);
    }

    private static void validateContainerType(Object rawType, boolean present, List<String> problems) {
        if (!present) {
            return;
        }
        if (!(rawType instanceof String typeName)) {
            problems.add("type must be a supported container material name");
            return;
        }

        try {
            Material material = Material.valueOf(typeName);
            if (!Mat.isLootChestMaterial(material)) {
                problems.add("type must be CHEST, TRAPPED_CHEST, BARREL, a shulker box, or a copper chest");
            }
        } catch (IllegalArgumentException exception) {
            // Lootchest has historically treated removed or unknown material
            // names as CHEST. Keep that rollback-compatible fallback.
        }
    }

    private static void validateRandomPosition(
            Object rawRandomPosition,
            boolean randomPositionActive,
            List<String> problems) {
        if (!randomPositionActive
                || !(rawRandomPosition instanceof ConfigurationSection randomPosition)
                || !randomPosition.getValues(false).containsKey("x")) {
            return;
        }

        validateWorld(randomPosition, "randomPosition.world", problems);
        validateRequiredCoordinate(
                randomPosition,
                "x",
                "randomPosition.x",
                MIN_HORIZONTAL_COORDINATE,
                MAX_HORIZONTAL_COORDINATE_EXCLUSIVE,
                true,
                problems);
        validateRequiredCoordinate(
                randomPosition,
                "y",
                "randomPosition.y",
                MIN_VERTICAL_COORDINATE,
                MAX_VERTICAL_COORDINATE,
                false,
                problems);
        validateRequiredCoordinate(
                randomPosition,
                "z",
                "randomPosition.z",
                MIN_HORIZONTAL_COORDINATE,
                MAX_HORIZONTAL_COORDINATE_EXCLUSIVE,
                true,
                problems);
        validateOptionalCoordinate(randomPosition, "pitch", "randomPosition.pitch", problems);
        validateOptionalCoordinate(randomPosition, "yaw", "randomPosition.yaw", problems);
    }

    private static void validateInventory(
            Object rawInventory,
            boolean inventoryPresent,
            Object rawChance,
            List<String> problems) {
        if (!inventoryPresent) {
            return;
        }
        if (!(rawInventory instanceof ConfigurationSection inventory)) {
            problems.add("inventory must be a YAML section when present");
            return;
        }

        if (inventory.getValues(false).isEmpty()) {
            return;
        }

        ConfigurationSection chances = rawChance instanceof ConfigurationSection section ? section : null;
        if (rawChance != null && chances == null) {
            problems.add("chance must be a YAML section when inventory items are present");
        }

        Map<Integer, String> numericSlots = new LinkedHashMap<>();
        for (Map.Entry<String, Object> itemEntry : inventory.getValues(false).entrySet()) {
            String slotKey = itemEntry.getKey();
            Integer slot = parseSlot(slotKey);
            if (slot == null || slot < 0 || slot >= INVENTORY_SIZE) {
                problems.add("inventory." + slotKey + " must use an integer slot from 0..26");
                continue;
            }
            String previousKey = numericSlots.putIfAbsent(slot, slotKey);
            if (previousKey != null) {
                problems.add("inventory." + slotKey + " duplicates numeric slot " + slot
                        + " already used by inventory." + previousKey);
            }
            if (!(itemEntry.getValue() instanceof ItemStack)) {
                problems.add("inventory." + slotKey + " must contain a serialized Bukkit ItemStack");
            }

            if (chances != null) {
                Map<String, Object> chanceValues = chances.getValues(false);
                if (chanceValues.containsKey(slotKey)
                        && !isIntegralNumber(chanceValues.get(slotKey), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                    problems.add("chance." + slotKey + " must be an integer when present");
                }
            }
        }
    }

    private static Integer parseSlot(String slotKey) {
        try {
            return Integer.valueOf(slotKey);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static void validateWorld(
            ConfigurationSection location,
            String displayPath,
            List<String> problems) {
        Object rawWorld = location.getValues(false).get("world");
        if (!(rawWorld instanceof String world) || world.isBlank()) {
            problems.add(displayPath + " must be a nonblank world name");
        }
    }

    private static void validateRequiredCoordinate(
            ConfigurationSection location,
            String key,
            String displayPath,
            BigDecimal minimum,
            BigDecimal maximum,
            boolean maximumExclusive,
            List<String> problems) {
        Object value = location.getValues(false).get(key);
        BigDecimal coordinate = finiteDecimal(value);
        if (coordinate == null
                || coordinate.compareTo(minimum) < 0
                || (maximumExclusive
                        ? coordinate.compareTo(maximum) >= 0
                        : coordinate.compareTo(maximum) > 0)) {
            problems.add(displayPath + " must be a finite number from "
                    + minimum.toPlainString() + (maximumExclusive ? " up to but not including " : " through ")
                    + maximum.toPlainString());
        }
    }

    private static void validateOptionalCoordinate(
            ConfigurationSection location,
            String key,
            String displayPath,
            List<String> problems) {
        Map<String, Object> values = location.getValues(false);
        if (values.containsKey(key) && !isFiniteNumber(values.get(key))) {
            problems.add(displayPath + " must be a finite number when present");
        }
    }

    private static Long validateOptionalInteger(
            Map<String, Object> values,
            String key,
            long minimum,
            long maximum,
            List<String> problems) {
        if (!values.containsKey(key)) {
            return 0L;
        }
        Long value = integralValue(values.get(key), minimum, maximum);
        if (value == null) {
            problems.add(key + " must be an integer when present");
        }
        return value;
    }

    private static void validateOptionalType(
            Map<String, Object> values,
            String key,
            Class<?> expectedType,
            List<String> problems) {
        if (values.containsKey(key) && !expectedType.isInstance(values.get(key))) {
            problems.add(key + " must be a " + expectedType.getSimpleName() + " when present");
        }
    }

    private static void validateOptionalScalar(
            Map<String, Object> values,
            String key,
            List<String> problems) {
        if (!values.containsKey(key)) {
            return;
        }
        Object value = values.get(key);
        if (value instanceof ConfigurationSection || value instanceof List<?>) {
            problems.add(key + " must be a scalar value when present");
        }
    }

    private static boolean isFiniteNumber(Object value) {
        return value instanceof Number number && Double.isFinite(number.doubleValue());
    }

    private static boolean isIntegralNumber(Object value, long minimum, long maximum) {
        return integralValue(value, minimum, maximum) != null;
    }

    private static Long integralValue(Object value, long minimum, long maximum) {
        BigDecimal decimal = decimalValue(value);
        if (decimal == null) {
            return null;
        }
        try {
            BigInteger integer = decimal.toBigIntegerExact();
            if (integer.compareTo(BigInteger.valueOf(minimum)) < 0
                    || integer.compareTo(BigInteger.valueOf(maximum)) > 0) {
                return null;
            }
            return integer.longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static BigDecimal finiteDecimal(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            return null;
        }
        return decimalValue(number);
    }

    private static BigDecimal decimalValue(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }
        if (number instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (number instanceof Byte || number instanceof Short
                || number instanceof Integer || number instanceof Long) {
            return BigDecimal.valueOf(number.longValue());
        }
        double doubleValue = number.doubleValue();
        if (!Double.isFinite(doubleValue)) {
            return null;
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            return BigDecimal.valueOf(doubleValue);
        }
    }

    record Result(Set<String> acceptedNames, Map<String, List<String>> rejectedDefinitions) {
        Result {
            acceptedNames = Collections.unmodifiableSet(new LinkedHashSet<>(acceptedNames));
            Map<String, List<String>> immutableProblems = new LinkedHashMap<>();
            rejectedDefinitions.forEach((name, problems) ->
                    immutableProblems.put(name, List.copyOf(problems)));
            rejectedDefinitions = Collections.unmodifiableMap(immutableProblems);
        }

        int totalDefinitions() {
            return acceptedNames.size() + rejectedDefinitions.size();
        }
    }
}
