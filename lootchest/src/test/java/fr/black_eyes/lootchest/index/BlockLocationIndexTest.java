package fr.black_eyes.lootchest.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BlockLocationIndexTest {
    private static final UUID WORLD = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_WORLD = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void separatesWorldsAndBlockCoordinates() {
        BlockLocationIndex<Object> index = new BlockLocationIndex<>();
        Object first = new Object();
        Object second = new Object();

        index.put(first, WORLD, 10, 64, -5);
        index.put(second, OTHER_WORLD, 10, 64, -5);

        assertEquals(first, index.get(WORLD, 10, 64, -5));
        assertEquals(second, index.get(OTHER_WORLD, 10, 64, -5));
        assertNull(index.get(WORLD, 11, 64, -5));
        assertEquals(2, index.size());
    }

    @Test
    void movingAnObjectRemovesItsOldLocation() {
        BlockLocationIndex<Object> index = new BlockLocationIndex<>();
        Object chest = new Object();

        index.put(chest, WORLD, 1, 2, 3);
        index.put(chest, WORLD, 4, 5, 6);

        assertNull(index.get(WORLD, 1, 2, 3));
        assertEquals(chest, index.get(WORLD, 4, 5, 6));
        assertEquals(1, index.size());
    }

    @Test
    void removingDisplacedObjectDoesNotRemoveCurrentOwner() {
        BlockLocationIndex<Object> index = new BlockLocationIndex<>();
        Object displaced = new Object();
        Object current = new Object();

        index.put(displaced, WORLD, 1, 2, 3);
        index.put(current, WORLD, 1, 2, 3);
        index.remove(displaced);
        index.put(displaced, WORLD, 4, 5, 6);

        assertEquals(current, index.get(WORLD, 1, 2, 3));
        assertEquals(displaced, index.get(WORLD, 4, 5, 6));
        assertEquals(2, index.size());
    }

    @Test
    void clearRemovesAllForwardAndReverseEntries() {
        BlockLocationIndex<Object> index = new BlockLocationIndex<>();
        Object chest = new Object();
        index.put(chest, WORLD, 1, 2, 3);

        index.clear();
        index.put(chest, WORLD, 7, 8, 9);

        assertNull(index.get(WORLD, 1, 2, 3));
        assertEquals(chest, index.get(WORLD, 7, 8, 9));
        assertEquals(1, index.size());
    }
}
