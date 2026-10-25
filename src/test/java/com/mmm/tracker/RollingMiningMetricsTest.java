package com.mmm.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RollingMiningMetricsTest
{
    @Test
    void keepsOnlyTheConfiguredTickWindow()
    {
        RollingMiningMetrics metrics = new RollingMiningMetrics(3);
        metrics.addTick(1);
        metrics.addTick(2);
        metrics.addTick(3);
        metrics.addTick(4);
        assertEquals(3, metrics.size());
        assertEquals(9L, metrics.total());
        assertEquals(7L, metrics.sumLatest(2));
    }

    @Test
    void clampsInvalidCountsAndResetsWithoutReallocating()
    {
        RollingMiningMetrics metrics = new RollingMiningMetrics(5);
        metrics.addTick(-10);
        metrics.addTick(6);
        assertEquals(6L, metrics.total());
        metrics.clear();
        assertEquals(0, metrics.size());
        assertEquals(0L, metrics.total());
        assertEquals(0L, metrics.sumLatest(100));
    }
}
