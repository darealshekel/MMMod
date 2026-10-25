package com.mmm.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MmmTimerStateTest
{
    @Test
    void sessionHoursContinuePastTwentyThree() throws Exception
    {
        var active = com.mmm.tracker.MiningStats.class.getDeclaredField("sessionActive");
        active.setAccessible(true);
        Object oldActive = active.get(null);
        java.util.Map<java.lang.reflect.Field, Object> previous = new java.util.LinkedHashMap<>();
        try
        {
            active.set(null, true);
            for (var entry : java.util.Map.<String, Object>of(
                    "running", false, "paused", false, "sessionPausedAtMs", 0L,
                    "runStartedAtMs", 1_000L, "hourStartTimeMs", 1_000L,
                    "completedHour", 23, "currentHourBlocks", 50_000L,
                    "bestHourBlocks", 50_000L, "lastHourBlocks", 0L).entrySet())
            {
                var field = MmmTimerState.class.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                previous.put(field, field.get(null));
                field.set(null, entry.getValue());
            }
            var update = MmmTimerState.class.getDeclaredMethod("updateHourBoundaries", long.class);
            update.setAccessible(true);
            update.invoke(null, 1_000L + 24L * 3_600_000L);
            var completed = MmmTimerState.class.getDeclaredField("completedHour");
            completed.setAccessible(true);
            assertEquals(24, completed.get(null));
            assertEquals(50_000L, MmmTimerState.getBestHourBlocks());
            update.invoke(null, 1_000L + 48L * 3_600_000L);
            assertEquals(48, completed.get(null));
        }
        finally
        {
            active.set(null, oldActive);
            for (var entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
        }
    }
    @Test
    void formatsTimerClockWithoutGenericStringFormatting()
    {
        assertEquals("00:00:00", MmmTimerState.formatTime(0L));
        assertEquals("01:02:03", MmmTimerState.formatTime(3_723_000L));
        assertEquals("24:00:00", MmmTimerState.formatTime(MmmTimerState.MAX_DURATION_MS));
    }
}
