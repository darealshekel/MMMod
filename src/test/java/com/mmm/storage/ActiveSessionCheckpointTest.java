package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActiveSessionCheckpointTest
{
    @TempDir
    Path tempDir;

    @Test
    void activeSessionStateSurvivesRestart() throws Exception
    {
        Path target = this.tempDir.resolve("active-session.json");
        SessionData session = session(1_000L, 65_000L, 12_345L);
        session.wallDurationMs = 90_000L;
        ActiveSessionCheckpoint.State expected = new ActiveSessionCheckpoint.State(
                session,
                true,
                true,
                true,
                50_000L,
                4_000L,
                1_000_000L,
                20L,
                30_000L,
                true,
                60_000L);

        ActiveSessionCheckpoint.save(target, expected);
        ActiveSessionCheckpoint.State restored = ActiveSessionCheckpoint.load(target);

        assertNotNull(restored);
        assertEquals(12_345L, restored.session().totalBlocks);
        assertEquals(64_000L, restored.session().getDurationMs());
        assertEquals(90_000L, restored.session().getWallDurationMs());
        assertTrue(restored.paused());
        assertTrue(restored.autoPaused());
        assertTrue(restored.menuPaused());
        assertEquals(4_000L, restored.pausedAccumulatedMs());
        assertEquals(1_000_000L, restored.sessionStartTotalMined());
        assertEquals(60_000L, restored.savedAtMs());
    }

    @Test
    void malformedPrimaryRecoversPreviousCheckpoint() throws Exception
    {
        Path target = this.tempDir.resolve("active-session.json");
        ActiveSessionCheckpoint.save(target, state(100L));
        ActiveSessionCheckpoint.save(target, state(200L));
        Files.writeString(target, "{broken");

        ActiveSessionCheckpoint.State restored = ActiveSessionCheckpoint.load(target);

        assertNotNull(restored);
        assertEquals(100L, restored.session().totalBlocks);
    }

    @Test
    void inflatedSessionTotalCanBeRepairedFromAcceptedBlockBreakdown()
    {
        SessionData session = session(1_000L, 61_000L, 12_345L);
        session.totalBlocks = 20_000_000L;
        session.recordMinedAmount(5_000L, 500_000L);

        assertTrue(session.repairInflatedTotalFromBreakdown());
        assertEquals(12_345L, session.totalBlocks);
        assertTrue(session.miningRateBuckets.isEmpty());
        assertEquals(0, session.getPeakBlocksPerHour());
    }

    private static ActiveSessionCheckpoint.State state(long blocks)
    {
        return new ActiveSessionCheckpoint.State(
                session(1_000L, 61_000L, blocks),
                false,
                false,
                false,
                0L,
                0L,
                0L,
                0L,
                0L,
                false,
                61_000L);
    }

    private static SessionData session(long startMs, long endMs, long blocks)
    {
        SessionData session = new SessionData(startMs);
        session.endTimeMs = endMs;
        session.totalBlocks = blocks;
        session.blockBreakdown.put("minecraft:stone", blocks);
        return session;
    }
}
