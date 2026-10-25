package com.mmm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DailyProgressPolicyTest
{
    private static final long NOW = Instant.parse("2026-07-23T12:00:00Z").toEpochMilli();

    @Test
    void currentPeriodPreservesProgress()
    {
        DailyProgressPolicy.Result result = DailyProgressPolicy.evaluate(12_000L, 12_000L, "23-07-2026", NOW, NOW);

        assertEquals(12_000L, result.blocks());
        assertEquals(12_000L, result.progress());
        assertFalse(result.reset());
    }

    @Test
    void futureResetTimestampIsRepairedWithoutResettingProgress()
    {
        DailyProgressPolicy.Result result = DailyProgressPolicy.evaluate(
                12_000L,
                12_000L,
                "23-07-2026",
                NOW + 60_000L,
                NOW);

        assertEquals(12_000L, result.blocks());
        assertEquals(12_000L, result.progress());
        assertEquals(PeriodKeys.currentDailyStartMs(NOW), result.lastResetAtMs());
        assertFalse(result.reset());
        assertTrue(result.changed());
    }

    @Test
    void olderPeriodResetsExactlyOnce()
    {
        DailyProgressPolicy.Result first = DailyProgressPolicy.evaluate(12_000L, 12_000L, "22-07-2026", NOW - 1L, NOW);
        DailyProgressPolicy.Result second = DailyProgressPolicy.evaluate(
                first.blocks(),
                first.progress(),
                first.periodKey(),
                first.lastResetAtMs(),
                NOW);

        assertTrue(first.reset());
        assertEquals(0L, first.blocks());
        assertEquals(0L, first.progress());
        assertFalse(second.reset());
    }

    @Test
    void malformedAndFutureKeysRepairWithoutDataLoss()
    {
        DailyProgressPolicy.Result malformed = DailyProgressPolicy.evaluate(8_000L, 7_500L, "broken", 0L, NOW);
        DailyProgressPolicy.Result future = DailyProgressPolicy.evaluate(8_000L, 7_500L, "24-07-2026", NOW + 5_000L, NOW);

        assertEquals(8_000L, malformed.blocks());
        assertEquals(7_500L, malformed.progress());
        assertFalse(malformed.reset());
        assertEquals(8_000L, future.blocks());
        assertEquals(7_500L, future.progress());
        assertFalse(future.reset());
        assertEquals("23-07-2026", future.periodKey());
    }
}
