package com.mmm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WeeklyProgressPolicyTest
{
    private static long instant(String value)
    {
        return Instant.parse(value).toEpochMilli();
    }

    @Test
    void currentWeekSurvivesRestart()
    {
        long now = instant("2026-07-23T12:00:00Z");
        WeeklyProgressPolicy.Result result = WeeklyProgressPolicy.evaluate(
                123_456L,
                PeriodKeys.currentWeeklyKey(now),
                instant("2026-07-22T00:00:00Z"),
                now);

        assertEquals(123_456L, result.blocks());
        assertFalse(result.reset());
    }

    @Test
    void futureResetTimestampIsRepairedWithoutResettingBlocks()
    {
        long now = instant("2026-07-23T12:00:00Z");
        WeeklyProgressPolicy.Result result = WeeklyProgressPolicy.evaluate(
                123_456L,
                PeriodKeys.currentWeeklyKey(now),
                instant("2026-07-30T00:00:00Z"),
                now);

        assertEquals(123_456L, result.blocks());
        assertEquals(PeriodKeys.currentWeeklyStartMs(now), result.lastResetAtMs());
        assertFalse(result.reset());
        assertTrue(result.changed());
    }

    @Test
    void olderWeekResetsExactlyOnce()
    {
        long now = instant("2026-07-29T00:00:00Z");
        WeeklyProgressPolicy.Result first = WeeklyProgressPolicy.evaluate(
                42_000L,
                "22-07-2026",
                instant("2026-07-22T00:00:00Z"),
                now);
        WeeklyProgressPolicy.Result second = WeeklyProgressPolicy.evaluate(
                first.blocks(),
                first.periodKey(),
                first.lastResetAtMs(),
                now + 1_000L);

        assertTrue(first.reset());
        assertEquals(0L, first.blocks());
        assertFalse(second.reset());
        assertEquals(0L, second.blocks());
    }

    @Test
    void missingKeyRecoversWithoutDeletingProgress()
    {
        long now = instant("2026-07-23T12:00:00Z");
        WeeklyProgressPolicy.Result result = WeeklyProgressPolicy.evaluate(88_000L, "", 0L, now);

        assertEquals(88_000L, result.blocks());
        assertFalse(result.reset());
        assertEquals("missing_period_key_recovered", result.reason());
    }

    @Test
    void malformedKeyRecoversWithoutDeletingProgress()
    {
        long now = instant("2026-07-23T12:00:00Z");
        WeeklyProgressPolicy.Result result = WeeklyProgressPolicy.evaluate(91_000L, "not-a-week", 0L, now);

        assertEquals(91_000L, result.blocks());
        assertFalse(result.reset());
        assertEquals("invalid_period_key_recovered", result.reason());
    }

    @Test
    void futureKeyRecoversWithoutDeletingProgress()
    {
        long now = instant("2026-07-23T12:00:00Z");
        WeeklyProgressPolicy.Result result = WeeklyProgressPolicy.evaluate(73_000L, "29-07-2026", 0L, now);

        assertEquals(73_000L, result.blocks());
        assertFalse(result.reset());
        assertEquals("future_period_key_clock_recovery", result.reason());
    }

    @Test
    void legacyIsoWeekNormalizesToWednesdayStart()
    {
        long now = instant("2026-07-23T12:00:00Z");
        WeeklyProgressPolicy.Result result = WeeklyProgressPolicy.evaluate(
                20_000L,
                PeriodKeys.legacyIsoWeekKey(now),
                0L,
                now);

        assertEquals("22-07-2026", result.periodKey());
        assertEquals(20_000L, result.blocks());
        assertFalse(result.reset());
    }

    @Test
    void legacyIsoDateNormalizesWithoutReset()
    {
        long now = instant("2026-07-23T12:00:00Z");
        WeeklyProgressPolicy.Result result = WeeklyProgressPolicy.evaluate(15_000L, "2026-07-22", 0L, now);

        assertEquals("22-07-2026", result.periodKey());
        assertEquals(15_000L, result.blocks());
        assertFalse(result.reset());
    }

    @Test
    void nextBoundaryIsFollowingWednesdayUtc()
    {
        long now = instant("2026-07-23T12:00:00Z");
        assertEquals(instant("2026-07-29T00:00:00Z"), PeriodKeys.nextWeeklyBoundaryMs(now));
    }
}
