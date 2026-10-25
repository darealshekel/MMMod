package com.mmm.timer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmedBreaksTest {
    @Test void onlyCompletedLocalBreaksCanBeConfirmedAndOnlyOnce() {
        var breaks = new ConfirmedBreaks<Integer, String>();
        Object world = new Object();
        assertNull(breaks.confirm(world, 1, true, 100));
        breaks.completed(world, 1, "stone", 20000);
        assertEquals("stone", breaks.confirm(world, 1, true, 20050));
        assertNull(breaks.confirm(world, 1, true, 20100));
    }

    @Test void rejectionAndDimensionChangesCannotCreditBlocks() {
        var breaks = new ConfirmedBreaks<Integer, String>();
        Object world = new Object();
        breaks.completed(world, 1, "stone", 100);
        assertNull(breaks.confirm(world, 1, false, 101));
        assertNull(breaks.confirm(world, 1, true, 102));
        breaks.completed(world, 1, "stone", 200);
        assertNull(breaks.confirm(new Object(), 1, true, 201));
        assertNull(breaks.confirm(world, 1, true, 202));
    }

    @Test void pendingBreaksAreBoundedAndExpired() {
        var breaks = new ConfirmedBreaks<Integer, String>();
        Object world = new Object();
        for (int i = 0; i < 10000; i++) breaks.completed(world, i, "stone", i);
        assertTrue(breaks.size() <= 2048);
        breaks.expire(world, 100000);
        assertEquals(0, breaks.size());
    }
}
