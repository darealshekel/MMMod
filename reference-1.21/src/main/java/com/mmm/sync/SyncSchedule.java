package com.mmm.sync;

final class SyncSchedule
{
    private SyncSchedule()
    {
    }

    static boolean isDue(long lastSuccessfulSyncAtMs, long now, long intervalMs)
    {
        if (lastSuccessfulSyncAtMs <= 0L)
        {
            return true;
        }
        if (now < lastSuccessfulSyncAtMs)
        {
            return false;
        }
        return now - lastSuccessfulSyncAtMs >= Math.max(1L, intervalMs);
    }

    static long remainingMs(long lastSuccessfulSyncAtMs, long now, long intervalMs)
    {
        if (lastSuccessfulSyncAtMs <= 0L)
        {
            return 0L;
        }

        long safeInterval = Math.max(1L, intervalMs);
        if (now < lastSuccessfulSyncAtMs)
        {
            return safeInterval;
        }

        long elapsed = now - lastSuccessfulSyncAtMs;
        return elapsed >= safeInterval ? 0L : safeInterval - elapsed;
    }
}