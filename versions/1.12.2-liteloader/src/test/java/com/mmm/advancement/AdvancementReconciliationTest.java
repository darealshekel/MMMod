package com.mmm.advancement;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementReconciliationTest {
    @Test void firstUseDependsOnSavedHistoryNotTheNewLiveSession() {
        assertFalse(AdvancementReconciliation.hasHistory(new MmmAdvancementSnapshot(0, 0, 0, 0)));
        assertTrue(AdvancementReconciliation.hasHistory(new MmmAdvancementSnapshot(1, 0, 0, 0)));
        assertTrue(AdvancementReconciliation.hasHistory(new MmmAdvancementSnapshot(0, 1, 0, 0)));
        assertTrue(AdvancementReconciliation.hasHistory(new MmmAdvancementSnapshot(0, 0, 1, 0)));
        assertTrue(AdvancementReconciliation.hasHistory(new MmmAdvancementSnapshot(0, 0, 0, 1)));
        assertTrue(AdvancementReconciliation.hasHistory(new MmmAdvancementSnapshot(0, 0, 0, 0, 1, 0)));
    }

    @Test void recomputesRatherThanTrustingPersistedUnlocks() {
        var unlocked = EnumSet.allOf(MmmAdvancementDefinition.class);
        var result = AdvancementReconciliation.apply(unlocked, new MmmAdvancementSnapshot(0, 0, 0, 0));
        assertEquals(EnumSet.of(MmmAdvancementDefinition.FIRST_TIME), unlocked);
        assertTrue(result.changed());
        assertTrue(result.newlyUnlocked().isEmpty());
    }

    @Test void onlyNewlyCrossedAchievementsAreReturnedOnce() {
        var unlocked = EnumSet.of(MmmAdvancementDefinition.FIRST_TIME);
        var progress = new MmmAdvancementSnapshot(0, 7, 50_000, 5 * 3_600_000L, 10, 9);
        var result = AdvancementReconciliation.apply(unlocked, progress);
        assertEquals(EnumSet.of(MmmAdvancementDefinition.CONSISTENT, MmmAdvancementDefinition.DIG_AWARD,
                MmmAdvancementDefinition.MINER_AWARD, MmmAdvancementDefinition.ENDURANCE_I,
                MmmAdvancementDefinition.PRECISION), result.newlyUnlocked());
        assertFalse(AdvancementReconciliation.apply(unlocked, progress).changed());
    }

    @Test void correctedHistoryCanRelockAndThenUnlock() {
        var unlocked = EnumSet.noneOf(MmmAdvancementDefinition.class);
        var high = new MmmAdvancementSnapshot(100 * 3_600_000L, 0, 0, 0);
        AdvancementReconciliation.apply(unlocked, high);
        AdvancementReconciliation.apply(unlocked, new MmmAdvancementSnapshot(0, 0, 0, 0));
        assertFalse(unlocked.contains(MmmAdvancementDefinition.ENTRY_LEVEL));
        assertEquals(EnumSet.of(MmmAdvancementDefinition.ENTRY_LEVEL),
                AdvancementReconciliation.apply(unlocked, high).newlyUnlocked());
    }

    @Test void invalidMetricsAreClamped() {
        assertEquals(new MmmAdvancementSnapshot(0, 0, 0, 0, 0, 0), new MmmAdvancementSnapshot(-1, -2, -3, -4, -5, -6));
    }
}
