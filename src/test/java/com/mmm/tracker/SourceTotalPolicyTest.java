package com.mmm.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SourceTotalPolicyTest
{
    @Test
    void authoritativeScoreboardDoesNotIncludeLocallyPredictedBlocks()
    {
        assertEquals(1_000L, SourceTotalPolicy.resolve(1_003L, 1_000L, true));
    }

    @Test
    void sourceWithoutAnAuthoritativeScoreboardUsesLocalTracking()
    {
        assertEquals(1_003L, SourceTotalPolicy.resolve(1_003L, 1_000L, false));
    }

    @Test
    void explicitAuthorityNeverUsesTheLargerFallback()
    {
        assertEquals(1_000L, SourceTotalPolicy.preferAuthoritative(1_003L, 1_000L, true));
        assertEquals(1_003L, SourceTotalPolicy.preferAuthoritative(1_003L, 1_000L, false));
    }

    @Test
    void authoritativeCorrectionCanMoveDown()
    {
        assertEquals(4_848_277L, SourceTotalPolicy.resolve(64_000_000L, 4_848_277L, true));
    }
}