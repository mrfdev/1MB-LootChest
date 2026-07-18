package fr.black_eyes.lootchest.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void validIndexedHitSkipsFallbackLookup() {
        BlockLocationIndex<Object> index = new BlockLocationIndex<>();
        Object chest = new Object();
        AtomicInteger fallbackCalls = new AtomicInteger();
        index.put(chest, WORLD, 1, 2, 3);

        BlockLocationIndex.Resolution<Object> result = index.resolve(
                WORLD,
                1,
                2,
                3,
                value -> value == chest,
                () -> {
                    fallbackCalls.incrementAndGet();
                    return null;
                },
                false);

        assertEquals(chest, result.value());
        assertEquals(0, fallbackCalls.get());
        assertFalse(result.usedFallback());
        assertFalse(result.mismatch());
    }

    @Test
    void missingEntryUsesFallbackAndCachesResult() {
        BlockLocationIndex<Object> index = new BlockLocationIndex<>();
        Object chest = new Object();

        BlockLocationIndex.Resolution<Object> result = index.resolve(
                WORLD, 1, 2, 3, value -> true, () -> chest, false);

        assertEquals(chest, result.value());
        assertEquals(chest, index.get(WORLD, 1, 2, 3));
        assertTrue(result.usedFallback());
        assertTrue(result.mismatch());
    }

    @Test
    void invalidEntryIsReplacedByFallbackResult() {
        BlockLocationIndex<Object> index = new BlockLocationIndex<>();
        Object stale = new Object();
        Object current = new Object();
        index.put(stale, WORLD, 1, 2, 3);

        BlockLocationIndex.Resolution<Object> result = index.resolve(
                WORLD, 1, 2, 3, value -> value == current, () -> current, false);

        assertEquals(current, result.value());
        assertEquals(current, index.get(WORLD, 1, 2, 3));
        assertTrue(result.mismatch());
        index.put(stale, WORLD, 4, 5, 6);
        assertEquals(stale, index.get(WORLD, 4, 5, 6));
    }

    @Test
    void verificationDetectsDisagreementAndRepairsIndex() {
        BlockLocationIndex<Object> index = new BlockLocationIndex<>();
        Object indexed = new Object();
        Object scanned = new Object();
        index.put(indexed, WORLD, 1, 2, 3);

        BlockLocationIndex.Resolution<Object> result = index.resolve(
                WORLD, 1, 2, 3, value -> true, () -> scanned, true);

        assertEquals(scanned, result.value());
        assertEquals(indexed, result.indexedValue());
        assertTrue(result.usedFallback());
        assertTrue(result.mismatch());
        assertEquals(scanned, index.get(WORLD, 1, 2, 3));
    }
}
