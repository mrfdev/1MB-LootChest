package fr.black_eyes.lootchest.index;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks object identity by world UUID and block coordinates.
 *
 * @param <T> indexed object type
 */
public final class BlockLocationIndex<T> {
    private final Map<BlockKey, T> byLocation = new HashMap<>();
    private final Map<T, BlockKey> byValue = new IdentityHashMap<>();

    public void put(T value, UUID worldId, int x, int y, int z) {
        Objects.requireNonNull(value, "value");
        BlockKey key = new BlockKey(Objects.requireNonNull(worldId, "worldId"), x, y, z);
        BlockKey previousKey = byValue.put(value, key);
        if (previousKey != null && !previousKey.equals(key)
                && byLocation.get(previousKey) == value) {
            byLocation.remove(previousKey);
        }

        T displaced = byLocation.put(key, value);
        if (displaced != null && displaced != value && key.equals(byValue.get(displaced))) {
            byValue.remove(displaced);
        }
    }

    public T get(UUID worldId, int x, int y, int z) {
        if (worldId == null) {
            return null;
        }
        return byLocation.get(new BlockKey(worldId, x, y, z));
    }

    public void remove(T value) {
        if (value == null) {
            return;
        }
        BlockKey key = byValue.remove(value);
        if (key != null && byLocation.get(key) == value) {
            byLocation.remove(key);
        }
    }

    public void clear() {
        byLocation.clear();
        byValue.clear();
    }

    public int size() {
        return byLocation.size();
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
    }
}
