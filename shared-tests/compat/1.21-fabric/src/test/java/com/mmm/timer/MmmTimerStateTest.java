package com.mmm.timer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MmmTimerStateTest
{
    @Test
    void restoredHourUsesActiveTimeAndPausedSessionRejectsMining() throws Exception
    {
        java.util.Map<java.lang.reflect.Field, Object> previous = new java.util.LinkedHashMap<>();
        try {
            for (var type : java.util.List.of(MmmTimerState.class, com.mmm.tracker.MiningStats.class))
                for (var field : type.getDeclaredFields())
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                            && !java.lang.reflect.Modifier.isFinal(field.getModifiers()) && field.getType().isPrimitive()) {
                        field.setAccessible(true);
                        previous.put(field, field.get(null));
                    }
            set(com.mmm.tracker.MiningStats.class, "sessionActive", true);
            set(com.mmm.tracker.MiningStats.class, "sessionPaused", false);
            set(MmmTimerState.class, "running", false);
            set(MmmTimerState.class, "paused", false);
            set(MmmTimerState.class, "expired", false);
            set(MmmTimerState.class, "currentHourBlocks", 45_000L);
            long now = 10_000_000L;
            MmmTimerState.restoreSessionClock(50L * 60_000L, now, false);
            var update = MmmTimerState.class.getDeclaredMethod("updateHourBoundaries", long.class);
            update.setAccessible(true);
            update.invoke(null, now);
            var current = MmmTimerState.class.getDeclaredField("currentHourBlocks");
            current.setAccessible(true);
            assertEquals(45_000L, current.get(null));
            set(MmmTimerState.class, "running", true);
            set(com.mmm.tracker.MiningStats.class, "sessionPaused", true);
            var eligible = MmmTimerState.class.getDeclaredMethod("shouldCountBlockStats");
            eligible.setAccessible(true);
            assertEquals(false, eligible.invoke(null));
            // A different world's checkpoint must not rewind a countdown that
            // has already advanced since the timer file was loaded.
            set(MmmTimerState.class, "sessionPausedAtMs", 0L);
            set(MmmTimerState.class, "durationMs", 3_600_000L);
            set(MmmTimerState.class, "remainingPausedMs", 3_600_000L);
            set(MmmTimerState.class, "startedAtMs", now - 1_200_000L);
            MmmTimerState.restoreSessionClock(1000L, now, false);
            var remaining = MmmTimerState.class.getDeclaredMethod("getRemainingMs", long.class);
            remaining.setAccessible(true);
            assertEquals(2_400_000L, remaining.invoke(null, now));
        } finally {
            for (var entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
        }
    }

    private static void set(Class<?> type, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

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
