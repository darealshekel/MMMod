package com.mmm.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MmmAdvancementDefinitionTest
{
    private static final long HOUR_MS = 3_600_000L;

    @Test
    void exposesAllRequestedAchievements()
    {
        assertEquals(19, MmmAdvancementDefinition.ORDERED.size());
        assertEquals(5, count(MmmAdvancementDefinition.Kind.FIRST_USE) + count(MmmAdvancementDefinition.Kind.SESSION_HOURS));
        assertEquals(3, count(MmmAdvancementDefinition.Kind.STREAK_DAYS));
        assertEquals(4, count(MmmAdvancementDefinition.Kind.BEST_HOUR_BLOCKS));
        assertEquals(5, count(MmmAdvancementDefinition.Kind.SESSION_ENDURANCE_HOURS));
        assertEquals(1, count(MmmAdvancementDefinition.Kind.PRECISION_40K_SESSIONS));
        assertEquals(1, count(MmmAdvancementDefinition.Kind.PRECISION_50K_SESSIONS));
    }

    @Test
    void sessionHourThresholdsUseCumulativeActiveTime()
    {
        MmmAdvancementSnapshot below = new MmmAdvancementSnapshot(100L * HOUR_MS - 1L, 0, 0, 0L);
        MmmAdvancementSnapshot reached = new MmmAdvancementSnapshot(100L * HOUR_MS, 0, 0, 0L);

        assertFalse(MmmAdvancementDefinition.ENTRY_LEVEL.isReached(below));
        assertTrue(MmmAdvancementDefinition.ENTRY_LEVEL.isReached(reached));
    }

    @Test
    void streakBestHourAndEnduranceUseTheirOwnMetrics()
    {
        MmmAdvancementSnapshot snapshot = new MmmAdvancementSnapshot(0L, 30, 60_000, 24L * HOUR_MS);

        assertTrue(MmmAdvancementDefinition.UNSTOPPABLE.isReached(snapshot));
        assertTrue(MmmAdvancementDefinition.DIG_MASTER.isReached(snapshot));
        assertTrue(MmmAdvancementDefinition.ENDURANCE_IV.isReached(snapshot));
        assertFalse(MmmAdvancementDefinition.ETERNAL_MINER.isReached(snapshot));
        assertFalse(MmmAdvancementDefinition.HUMAN_QUARRY.isReached(snapshot));
        assertFalse(MmmAdvancementDefinition.ENDURANCE_V.isReached(snapshot));
    }

    @Test
    void reportsExactCurrentAndTargetProgress()
    {
        MmmAdvancementSnapshot snapshot = new MmmAdvancementSnapshot(200L * HOUR_MS, 21, 45_000, 6L * HOUR_MS);

        assertEquals("200h 00m / 100h", MmmAdvancementDefinition.ENTRY_LEVEL.progressLabel(snapshot));
        assertEquals("200h 00m / 500h", MmmAdvancementDefinition.VETERAN.progressLabel(snapshot));
        assertEquals("21 / 30 days", MmmAdvancementDefinition.UNSTOPPABLE.progressLabel(snapshot));
        assertEquals(0.4F, MmmAdvancementDefinition.VETERAN.progressFraction(snapshot), 0.001F);
        assertEquals(0.7F, MmmAdvancementDefinition.UNSTOPPABLE.progressFraction(snapshot), 0.001F);
    }

    @Test
    void sessionProgressIncludesCurrentMinutes()
    {
        MmmAdvancementSnapshot snapshot = new MmmAdvancementSnapshot(
                155L * HOUR_MS + 33L * 60_000L,
                21,
                45_000,
                9L * HOUR_MS + 7L * 60_000L);

        assertEquals("155h 33m / 500h", MmmAdvancementDefinition.VETERAN.progressLabel(snapshot));
        assertEquals("9h 07m / 10h", MmmAdvancementDefinition.ENDURANCE_II.progressLabel(snapshot));
    }

    @Test
    void precisionUsesDistinctQualifyingSessionCounts()
    {
        MmmAdvancementSnapshot snapshot = new MmmAdvancementSnapshot(0L, 0, 0, 0L, 10, 7);

        assertTrue(MmmAdvancementDefinition.PRECISION.isReached(snapshot));
        assertFalse(MmmAdvancementDefinition.OPTIMIZATION.isReached(snapshot));
        assertEquals("10 / 10 sessions", MmmAdvancementDefinition.PRECISION.progressLabel(snapshot));
        assertEquals("7 / 10 sessions", MmmAdvancementDefinition.OPTIMIZATION.progressLabel(snapshot));
    }

    private static long count(MmmAdvancementDefinition.Kind kind)
    {
        return MmmAdvancementDefinition.ORDERED.stream().filter(definition -> definition.kind() == kind).count();
    }
}
