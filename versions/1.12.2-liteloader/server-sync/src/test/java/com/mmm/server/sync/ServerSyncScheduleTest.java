package com.mmm.server.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

final class ServerSyncScheduleTest
{
    private static final long DAILY_INTERVAL_SECONDS = 86_400L;
    private static final Instant NOW = Instant.parse("2026-06-23T12:00:00Z");

    @Test
    void newInstallRegistersPromptly()
    {
        assertEquals(
                NOW.plusSeconds(2L),
                ServerSyncSchedule.startupAttempt("unknown", null, null, NOW, DAILY_INTERVAL_SECONDS));
    }

    @Test
    void approvedInstallRestartKeepsPersistedDailySchedule()
    {
        Instant lastSuccess = NOW.minusSeconds(3_600L);
        Instant expected = lastSuccess.plusSeconds(DAILY_INTERVAL_SECONDS);
        assertEquals(
                expected,
                ServerSyncSchedule.startupAttempt(
                        "approved",
                        lastSuccess.toString(),
                        null,
                        NOW,
                        DAILY_INTERVAL_SECONDS));
    }

    @Test
    void pendingInstallPollsForApprovalWithoutMarkingDailySuccess()
    {
        assertEquals(
                NOW.plusSeconds(300L),
                ServerSyncSchedule.nextAttemptAfterResponse("pending", false, NOW, DAILY_INTERVAL_SECONDS));
    }

    @Test
    void approvedAcceptedSnapshotSchedulesNextDay()
    {
        assertEquals(
                NOW.plusSeconds(DAILY_INTERVAL_SECONDS),
                ServerSyncSchedule.nextAttemptAfterResponse("approved", true, NOW, DAILY_INTERVAL_SECONDS));
    }

    @Test
    void failuresUseBoundedBackoff()
    {
        assertEquals(NOW.plusSeconds(60L), ServerSyncSchedule.nextRetryAt(1, NOW));
        assertEquals(NOW.plusSeconds(300L), ServerSyncSchedule.nextRetryAt(2, NOW));
        assertEquals(NOW.plusSeconds(900L), ServerSyncSchedule.nextRetryAt(3, NOW));
        assertEquals(NOW.plusSeconds(3_600L), ServerSyncSchedule.nextRetryAt(4, NOW));
        assertEquals(NOW.plusSeconds(3_600L), ServerSyncSchedule.nextRetryAt(20, NOW));
    }

    @Test
    void shutdownEligibilityUsesLastSuccessfulSync()
    {
        assertFalse(ServerSyncSchedule.dailySyncDue(
                NOW.minusSeconds(3_600L).toString(),
                NOW,
                DAILY_INTERVAL_SECONDS));
        assertTrue(ServerSyncSchedule.dailySyncDue(
                NOW.minusSeconds(DAILY_INTERVAL_SECONDS).toString(),
                NOW,
                DAILY_INTERVAL_SECONDS));
        assertTrue(ServerSyncSchedule.dailySyncDue(null, NOW, DAILY_INTERVAL_SECONDS));
    }
}
