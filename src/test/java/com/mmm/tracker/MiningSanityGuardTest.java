package com.mmm.tracker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MiningSanityGuardTest
{
    @AfterEach
    void reset()
    {
        MiningSanityGuard.resetWorld("test-reset");
    }

    @Test
    void tracksDuplicateCoordinatesPerDimensionWithoutChangingLimits()
    {
        MiningSanityGuard.resetWorld("test-world");
        BlockPos pos = new BlockPos(12, 64, -4);
        long now = 1_000_000L;

        assertTrue(MiningSanityGuard.shouldAcceptBlock(pos, "test-world", "minecraft:overworld", now));
        assertTrue(MiningSanityGuard.shouldAcceptBlock(pos, "test-world", "minecraft:overworld", now + 1L));
        assertTrue(MiningSanityGuard.shouldAcceptBlock(pos, "test-world", "minecraft:overworld", now + 2L));
        assertFalse(MiningSanityGuard.shouldAcceptBlock(pos, "test-world", "minecraft:overworld", now + 3L));

        assertTrue(MiningSanityGuard.shouldAcceptBlock(pos, "test-world", "minecraft:the_nether", now + 4L));
    }

    @Test
    void changingWorldClearsCoordinateHistory()
    {
        BlockPos pos = new BlockPos(0, 32, 0);
        long now = 2_000_000L;
        MiningSanityGuard.resetWorld("world-a");
        for (int count = 0; count < 3; count++)
        {
            assertTrue(MiningSanityGuard.shouldAcceptBlock(pos, "world-a", "minecraft:overworld", now + count));
        }
        assertFalse(MiningSanityGuard.shouldAcceptBlock(pos, "world-a", "minecraft:overworld", now + 3L));
        assertTrue(MiningSanityGuard.shouldAcceptBlock(pos, "world-b", "minecraft:overworld", now + 4L));
    }
}
