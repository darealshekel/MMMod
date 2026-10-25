package com.mmm.tracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PinnedCollectionTest
{
    @Test void showsProgressAndCapsCompletedBar() {
        var partial = new PinnedCollection.View("minecraft:stone", "Stone", "World", 25000, 100000);
        assertEquals(0.25, partial.fraction());
        assertFalse(partial.completed());
        var done = new PinnedCollection.View("minecraft:dirt", "Dirt", "World", 12000000, 10000000);
        assertEquals(1, done.fraction());
        assertTrue(done.completed());
    }

    @Test void emptyProgressDoesNotDivideByZero() {
        assertEquals(0, new PinnedCollection.View("minecraft:stone", "Stone", "World", 0, 0).fraction());
        assertEquals(0, new PinnedCollection.View("minecraft:stone", "Stone", "World", -10, 10000).fraction());
    }

    @Test void extendedCollectionIsNotCompleteAtTenMillion() {
        var stone = new PinnedCollection.View("minecraft:stone", "Stone", "World", 10000000, 25000000);
        assertFalse(stone.completed());
        assertEquals(0.4, stone.fraction());
        assertTrue(new PinnedCollection.View("minecraft:stone", "Stone", "World", 25000000, 25000000).completed());
    }
}
