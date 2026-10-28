package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionHistoryQualificationTest
{
    @Test
    void savesTenThousandBlockSessionsWithoutAMinimumDuration()
    {
        SessionData session = new SessionData(1_000L);
        session.endTimeMs = 2_000L;
        session.totalBlocks = 10_000L;

        assertTrue(SessionHistory.isQualifyingSession(session));
    }

    @Test
    void doesNotSaveSessionsBelowTenThousandBlocks()
    {
        SessionData session = new SessionData(1_000L);
        session.endTimeMs = 3_601_000L;
        session.totalBlocks = 9_999L;

        assertFalse(SessionHistory.isQualifyingSession(session));
    }
}