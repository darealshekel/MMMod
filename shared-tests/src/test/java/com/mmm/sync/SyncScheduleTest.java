package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SyncScheduleTest
{
    private static final long HOUR = 60L * 60L * 1_000L;
    private static final long DAY = 24L * HOUR;

    @Test
    void firstSyncIsDue()
    {
        assertTrue(SyncSchedule.isDue(0L, 10_000L, DAY));
    }

    @Test
    void nineteenHoursAfterSuccessIsNotDue()
    {
        long lastSuccess = 1_000_000L;
        assertFalse(SyncSchedule.isDue(lastSuccess, lastSuccess + 19L * HOUR, DAY));
    }

    @Test
    void exactlyTwentyFourHoursAfterSuccessIsDue()
    {
        long lastSuccess = 1_000_000L;
        assertTrue(SyncSchedule.isDue(lastSuccess, lastSuccess + DAY, DAY));
    }

    @Test
    void remainingTimeUsesLastSuccessOnly()
    {
        long lastSuccess = 1_000_000L;
        assertEquals(5L * HOUR, SyncSchedule.remainingMs(lastSuccess, lastSuccess + 19L * HOUR, DAY));
    }

    @Test
    void futureSuccessTimestampDoesNotTriggerImmediateSync()
    {
        long now = 1_000_000L;
        assertFalse(SyncSchedule.isDue(now + HOUR, now, DAY));
        assertEquals(DAY, SyncSchedule.remainingMs(now + HOUR, now, DAY));
    }

    @Test
    void failedAttemptCannotChangeScheduleWithoutARecordedSuccess()
    {
        long lastSuccess = 1_000_000L;
        long failedAttempt = lastSuccess + 23L * HOUR;
        assertFalse(SyncSchedule.isDue(lastSuccess, failedAttempt, DAY));
        assertTrue(SyncSchedule.isDue(lastSuccess, lastSuccess + DAY, DAY));
    }
}
