package com.mmm.legacy.visual;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BreakIndicatorStateTest {
    @Test void instantBreakKeepsCapturedBoundsAndExpires() {
        var state = new BreakIndicatorState();
        var pos = new BlockPos(4, 20, -8);
        var box = new Box(4, 20, -8, 5, 20.5, -7);
        state.complete(pos, box, 10);
        assertEquals(box, state.active(10).get(pos).bounds());
        assertEquals(0.5F, state.active(10 + BreakIndicatorState.FLASH_NANOS / 2).get(pos).opacity(10 + BreakIndicatorState.FLASH_NANOS / 2), 0.001);
        assertTrue(state.active(10 + BreakIndicatorState.FLASH_NANOS).isEmpty());
    }
    @Test void completionQueueIsBoundedAndClearable() {
        var state = new BreakIndicatorState();
        for (int i = 0; i < 100; i++) state.complete(new BlockPos(i, 0, 0), new Box(i, 0, 0, i + 1, 1, 1), 0);
        assertEquals(64, state.active(0).size());
        assertFalse(state.active(0).containsKey(new BlockPos(0, 0, 0)));
        state.clear(); assertTrue(state.active(0).isEmpty());
    }
    @Test void progressGrowsFromCenterAndUsesActualSlabBounds() {
        Box box = BreakIndicatorState.scaledBounds(new Box(10, 64, 30, 11, 64.5, 31), 0.5);
        assertEquals(10.247, box.minX, 0.00001);
        assertEquals(64.122, box.minY, 0.00001);
        assertEquals(30.753, box.maxZ, 0.00001);
        assertEquals(0.126, BreakIndicatorState.scaledBounds(new Box(0, 0, 0, 1, 1, 1), 0).maxX
                - BreakIndicatorState.scaledBounds(new Box(0, 0, 0, 1, 1, 1), 0).minX, 0.00001);
    }
    @Test void invalidProgressIsClampedAndColorsInterpolateAllChannels() {
        Box result = BreakIndicatorState.scaledBounds(new Box(0, 0, 0, 1, 1, 1), Double.NaN);
        assertFalse(result.isInvalid());
        assertEquals(0x80808080, VisualColors.interpolate(0, 0xFFFFFFFF, 0.5F));
        assertEquals(0x40123456, VisualColors.alpha(0x80123456, 0.5F));
    }
}

