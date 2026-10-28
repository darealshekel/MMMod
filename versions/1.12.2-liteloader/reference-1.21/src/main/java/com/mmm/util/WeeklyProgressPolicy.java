package com.mmm.util;

public final class WeeklyProgressPolicy
{
    public record Result(
            long blocks,
            String periodKey,
            long lastResetAtMs,
            long nextResetAtMs,
            boolean changed,
            boolean reset,
            String reason)
    {
    }

    private WeeklyProgressPolicy()
    {
    }

    public static Result evaluate(long blocks, String periodKey, long lastResetAtMs, long now)
    {
        long safeBlocks = Math.max(0L, blocks);
        long safeLastReset = Math.max(0L, lastResetAtMs);
        String currentKey = PeriodKeys.currentWeeklyKey(now);
        PeriodKeys.Relation relation = PeriodKeys.weeklyRelation(periodKey, now);

        return switch (relation)
        {
            case CURRENT -> {
                String normalized = PeriodKeys.normalizeWeeklyKey(periodKey, now);
                long normalizedLastReset = safeLastReset > now
                        ? PeriodKeys.currentWeeklyStartMs(now)
                        : safeLastReset;
                yield new Result(
                        safeBlocks,
                        normalized,
                        normalizedLastReset,
                        PeriodKeys.nextWeeklyBoundaryMs(now),
                        normalized.equals(clean(periodKey)) == false
                                || safeBlocks != blocks
                                || normalizedLastReset != lastResetAtMs,
                        false,
                        "current_period");
            }
            case OLDER -> new Result(
                    0L,
                    currentKey,
                    now,
                    PeriodKeys.nextWeeklyBoundaryMs(now),
                    true,
                    true,
                    "utc_wednesday_boundary");
            case MISSING -> recovery(safeBlocks, currentKey, safeLastReset, now, "missing_period_key_recovered");
            case INVALID -> recovery(safeBlocks, currentKey, safeLastReset, now, "invalid_period_key_recovered");
            case FUTURE -> recovery(safeBlocks, currentKey, safeLastReset, now, "future_period_key_clock_recovery");
        };
    }

    private static Result recovery(long blocks, String currentKey, long lastResetAtMs, long now, String reason)
    {
        long recoveredResetAt = lastResetAtMs > 0L && lastResetAtMs <= now
                ? lastResetAtMs
                : PeriodKeys.currentWeeklyStartMs(now);
        return new Result(
                blocks,
                currentKey,
                recoveredResetAt,
                PeriodKeys.nextWeeklyBoundaryMs(now),
                true,
                false,
                reason);
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim();
    }
}