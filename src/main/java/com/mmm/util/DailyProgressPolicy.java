package com.mmm.util;

public final class DailyProgressPolicy
{
    public record Result(
            long blocks,
            long progress,
            String periodKey,
            long lastResetAtMs,
            long nextResetAtMs,
            boolean changed,
            boolean reset,
            String reason)
    {
    }

    private DailyProgressPolicy()
    {
    }

    public static Result evaluate(long blocks, long progress, String periodKey, long lastResetAtMs, long now)
    {
        long safeBlocks = Math.max(0L, blocks);
        long safeProgress = Math.max(0L, progress);
        long safeLastReset = Math.max(0L, lastResetAtMs);
        String currentKey = PeriodKeys.currentDailyKey(now);
        PeriodKeys.Relation relation = PeriodKeys.dailyRelation(periodKey, now);

        return switch (relation)
        {
            case CURRENT -> {
                String normalized = PeriodKeys.normalizeDailyKey(periodKey, now);
                long normalizedLastReset = safeLastReset > now
                        ? PeriodKeys.currentDailyStartMs(now)
                        : safeLastReset;
                yield new Result(
                        safeBlocks,
                        safeProgress,
                        normalized,
                        normalizedLastReset,
                        PeriodKeys.nextDailyBoundaryMs(now),
                        normalized.equals(clean(periodKey)) == false
                                || safeBlocks != blocks
                                || safeProgress != progress
                                || normalizedLastReset != lastResetAtMs,
                        false,
                        "current_period");
            }
            case OLDER -> new Result(
                    0L,
                    0L,
                    currentKey,
                    now,
                    PeriodKeys.nextDailyBoundaryMs(now),
                    true,
                    true,
                    "utc_midnight_boundary");
            case MISSING -> recovery(safeBlocks, safeProgress, currentKey, safeLastReset, now, "missing_period_key_recovered");
            case INVALID -> recovery(safeBlocks, safeProgress, currentKey, safeLastReset, now, "invalid_period_key_recovered");
            case FUTURE -> recovery(safeBlocks, safeProgress, currentKey, safeLastReset, now, "future_period_key_clock_recovery");
        };
    }

    private static Result recovery(
            long blocks,
            long progress,
            String currentKey,
            long lastResetAtMs,
            long now,
            String reason)
    {
        long recoveredResetAt = lastResetAtMs > 0L && lastResetAtMs <= now
                ? lastResetAtMs
                : PeriodKeys.currentDailyStartMs(now);
        return new Result(
                blocks,
                progress,
                currentKey,
                recoveredResetAt,
                PeriodKeys.nextDailyBoundaryMs(now),
                true,
                false,
                reason);
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.trim();
    }
}
