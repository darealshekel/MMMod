package com.mmm.feature;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BreakIndicatorStateTest {
    @Test void instantBreakKeepsBoundsAndFadesForExactly150Milliseconds() {
        var state = new BreakIndicatorState();
        var pos = new BlockPos(4, 20, -8);
        var box = new Box(4, 20, -8, 5, 20.5, -7);
        state.complete(pos, box, 10);
        assertEquals(box, state.active(10).get(pos).bounds());
        assertEquals(1, state.active(10).get(pos).opacity(10));
        long halfway = 10 + BreakIndicatorState.FLASH_NANOS / 2;
        assertEquals(0.5F, state.active(halfway).get(pos).opacity(halfway));
        assertTrue(state.active(10 + BreakIndicatorState.FLASH_NANOS).isEmpty());
    }

    @Test void completionQueueIsBoundedAndClearable() {
        var state = new BreakIndicatorState();
        for (int i = 0; i < 100; i++)
            state.complete(new BlockPos(i, 0, 0), new Box(i, 0, 0, i + 1, 1, 1), 0);
        assertEquals(64, state.active(0).size());
        assertFalse(state.active(0).containsKey(BlockPos.ORIGIN));
        state.clear();
        assertTrue(state.active(0).isEmpty());
    }

    @Test void repeatedCompletionRefreshesOneEntryAndCopiesMutablePosition() {
        var state = new BreakIndicatorState();
        var pos = new BlockPos.Mutable(1, 2, 3);
        var box = new Box(1, 2, 3, 2, 3, 4);
        state.complete(pos, box, 0);
        state.complete(pos, box, 100);
        pos.set(9, 9, 9);
        assertEquals(1, state.active(100).size());
        assertEquals(100, state.active(100).get(new BlockPos(1, 2, 3)).started());
    }

    @Test void growsFromCenterUsingActualSlabShapeJustLikeLegacy() {
        Box box = BreakIndicatorState.scaledBounds(new Box(10, 64, 30, 11, 64.5, 31), 0.5);
        assertEquals(10.247, box.minX, 0.00001);
        assertEquals(64.122, box.minY, 0.00001);
        assertEquals(30.753, box.maxZ, 0.00001);
        var cube = new Box(0, 0, 0, 1, 1, 1);
        assertEquals(0.126, BreakIndicatorState.scaledBounds(cube, 0).getLengthX(), 0.00001);
        assertEquals(1.006, BreakIndicatorState.scaledBounds(cube, 2).getLengthX(), 0.00001);
        assertEquals(BreakIndicatorState.scaledBounds(cube, 0), BreakIndicatorState.scaledBounds(cube, Double.NaN));
    }
}
