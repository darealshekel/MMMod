package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SyncScheduleManualTest
{
    private static final long DAY = 24L * 60L * 60L * 1_000L;

    @Test
    void successfulManualSyncRestartsFullAutomaticInterval()
    {
        long manualSuccessAt = 500_000L;

        assertFalse(SyncSchedule.isDue(manualSuccessAt, manualSuccessAt + DAY - 1L, DAY));
        assertTrue(SyncSchedule.isDue(manualSuccessAt, manualSuccessAt + DAY, DAY));
        assertEquals(DAY, SyncSchedule.remainingMs(manualSuccessAt, manualSuccessAt, DAY));
    }

    @Test
    void repeatedSchedulerChecksRemainDueWithoutMutatingState()
    {
        long lastSuccessAt = 500_000L;
        long now = lastSuccessAt + DAY;

        assertTrue(SyncSchedule.isDue(lastSuccessAt, now, DAY));
        assertTrue(SyncSchedule.isDue(lastSuccessAt, now, DAY));
    }
}
