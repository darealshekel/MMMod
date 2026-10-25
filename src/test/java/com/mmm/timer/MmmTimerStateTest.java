package com.mmm.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MmmTimerStateTest
{
    @Test
    void formatsTimerClockWithoutGenericStringFormatting()
    {
        assertEquals("00:00:00", MmmTimerState.formatTime(0L));
        assertEquals("01:02:03", MmmTimerState.formatTime(3_723_000L));
        assertEquals("24:00:00", MmmTimerState.formatTime(MmmTimerState.MAX_DURATION_MS));
    }
}