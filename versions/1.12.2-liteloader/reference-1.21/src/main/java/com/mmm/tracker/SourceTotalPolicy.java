package com.mmm.tracker;

/** Keeps authoritative scoreboard totals separate from local fallback estimates. */
public final class SourceTotalPolicy
{
    private SourceTotalPolicy()
    {
    }

    public static long resolve(long localTrackedTotal,
                               long scoreboardTotal,
                               boolean scoreboardAuthoritative)
    {
        return preferAuthoritative(
                localTrackedTotal,
                scoreboardTotal,
                scoreboardAuthoritative);
    }

    public static long preferAuthoritative(long fallbackTotal,
                                           long authoritativeTotal,
                                           boolean authoritativeAvailable)
    {
        return authoritativeAvailable
                ? Math.max(0L, authoritativeTotal)
                : Math.max(0L, fallbackTotal);
    }
}