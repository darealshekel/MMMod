package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SessionDataDurationTest
{
    @Test
    void persistsActiveAndWallClockDurationsSeparately()
    {
        SessionData session = new SessionData(1_000L);
        session.endTimeMs = 61_000L;
        session.wallDurationMs = 90_000L;

        SessionData restored = SessionData.deserialise(session.serialise());

        assertNotNull(restored);
        assertEquals(60_000L, restored.getActiveDurationMs());
        assertEquals(90_000L, restored.getWallDurationMs());
    }

    @Test
    void legacySessionsUseActiveDurationAsWallDuration()
    {
        SessionData restored = SessionData.deserialise("1000,61000,10,0,0,minecraft:stone=10,10");

        assertNotNull(restored);
        assertEquals(60_000L, restored.getActiveDurationMs());
        assertEquals(60_000L, restored.getWallDurationMs());
    }
}
