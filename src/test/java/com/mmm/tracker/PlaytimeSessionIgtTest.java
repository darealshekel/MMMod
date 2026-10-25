package com.mmm.tracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaytimeSessionIgtTest
{
    @Test void combinesSavedAndCurrentSessionIgt()
    {
        assertEquals(5_400_000L, PlaytimeTracker.sessionIgtMillis(3_600_000L, 1_800_000L));
        assertEquals(3_600_000L, PlaytimeTracker.sessionIgtMillis(3_600_000L, 0));
    }

    @Test void guardsInvalidAndOverflowingDurations()
    {
        assertEquals(0, PlaytimeTracker.sessionIgtMillis(-1, -1));
        assertEquals(Long.MAX_VALUE, PlaytimeTracker.sessionIgtMillis(Long.MAX_VALUE, 1));
    }
}
