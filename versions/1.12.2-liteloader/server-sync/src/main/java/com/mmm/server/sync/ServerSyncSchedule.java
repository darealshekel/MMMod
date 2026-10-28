package com.mmm.server.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

final class ServerSyncSchedule
{
    static final Duration INITIAL_REGISTRATION_DELAY = Duration.ofSeconds(2L);
    static final Duration PENDING_APPROVAL_POLL_INTERVAL = Duration.ofMinutes(5L);
    static final Duration REJECTED_INSTALL_POLL_INTERVAL = Duration.ofHours(24L);
    private static final Duration[] RETRY_DELAYS = {
            Duration.ofMinutes(1L),
            Duration.ofMinutes(5L),
            Duration.ofMinutes(15L),
            Duration.ofHours(1L)
    };

    private ServerSyncSchedule()
    {
    }

    static Instant startupAttempt(
            String installStatus,
            String lastSuccessfulSyncAt,
            String persistedNextAttemptAt,
            Instant now,
            long syncIntervalSeconds)
    {
        Instant persistedNextAttempt = parseInstant(persistedNextAttemptAt);
        if ("approved".equals(installStatus))
        {
            Instant lastSuccess = parseInstant(lastSuccessfulSyncAt);
            Instant nextDailySync = lastSuccess == null
                    ? now.plus(INITIAL_REGISTRATION_DELAY)
                    : lastSuccess.plusSeconds(syncIntervalSeconds);
            return maxInstant(now.plus(INITIAL_REGISTRATION_DELAY), persistedNextAttempt, nextDailySync);
        }
        if (persistedNextAttempt != null && persistedNextAttempt.isAfter(now))
        {
            return persistedNextAttempt;
        }
        return now.plus(INITIAL_REGISTRATION_DELAY);
    }

    static Instant nextAttemptAfterResponse(String status, boolean acceptedPublicTotals, Instant now, long syncIntervalSeconds)
    {
        if ("approved".equals(status) && acceptedPublicTotals)
        {
            return now.plusSeconds(syncIntervalSeconds);
        }
        if ("rejected".equals(status))
        {
            return now.plus(REJECTED_INSTALL_POLL_INTERVAL);
        }
        return now.plus(PENDING_APPROVAL_POLL_INTERVAL);
    }

    static Instant nextRetryAt(int consecutiveFailures, Instant now)
    {
        int failureIndex = Math.max(0, consecutiveFailures - 1);
        int index = Math.min(failureIndex, RETRY_DELAYS.length - 1);
        return now.plus(RETRY_DELAYS[index]);
    }

    static boolean dailySyncDue(String lastSuccessfulSyncAt, Instant now, long syncIntervalSeconds)
    {
        Instant lastSuccess = parseInstant(lastSuccessfulSyncAt);
        return lastSuccess == null || !now.isBefore(lastSuccess.plusSeconds(syncIntervalSeconds));
    }

    static Instant parseInstant(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return Instant.parse(value);
        }
        catch (DateTimeParseException ignored)
        {
            return null;
        }
    }

    private static Instant maxInstant(Instant... values)
    {
        Instant maximum = null;
        for (Instant value : values)
        {
            if (value != null && (maximum == null || value.isAfter(maximum)))
            {
                maximum = value;
            }
        }
        return maximum == null ? Instant.now() : maximum;
    }
}
