package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SessionDataBestHourTest
{
    @Test
    void usesAllAvailableMinutesForSessionsShorterThanOneHour()
    {
        SessionData session = new SessionData(0L);
        session.recordMinedAmount(0L, 1_000L);
        session.recordMinedAmount(30L * 60_000L, 2_000L);

        assertEquals(3_000, session.getBestHourBlocks());
    }

    @Test
    void returnsTheLargestRollingSixtyMinuteWindow()
    {
        SessionData session = new SessionData(0L);
        for (int minute = 0; minute < 60; minute++)
        {
            session.recordMinedAmount(minute * 60_000L, 100L);
        }
        for (int minute = 60; minute < 120; minute++)
        {
            session.recordMinedAmount(minute * 60_000L, 200L);
        }

        assertEquals(12_000, session.getBestHourBlocks());
    }
}