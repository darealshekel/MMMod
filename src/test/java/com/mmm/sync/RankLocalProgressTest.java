package com.mmm.sync;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RankLocalProgressTest
{
    @Test void unchangedWebsiteDoesNotEraseNewMining()
    {
        var progress = new RankLocalProgress(1000, 0);
        progress.mined();
        progress.mined();
        progress.reconcile(1000);
        assertEquals(1002, progress.total());
    }

    @Test void syncConsumesOnlyBlocksAlreadyPublished()
    {
        var progress = new RankLocalProgress(1000, 150);
        progress.reconcile(1100);
        assertEquals(50, progress.pending());
        assertEquals(1150, progress.total());
        progress.reconcile(1100);
        assertEquals(1150, progress.total());
        progress.reconcile(1150);
        assertEquals(0, progress.pending());
    }

    @Test void newBlocksWhileRequestIsRunningArePreserved()
    {
        var progress = new RankLocalProgress(1000, 10);
        progress.mined();
        progress.reconcile(1010);
        assertEquals(1011, progress.total());
    }

    @Test void lowerManualCorrectionReplacesBaseline()
    {
        var progress = new RankLocalProgress(1000, 10);
        progress.reconcile(900);
        assertEquals(910, progress.total());
    }

    @Test void restoreRetainsProgressAcrossSessionAndWorldChanges()
    {
        var old = new RankLocalProgress(1000, 50);
        var restored = new RankLocalProgress(old.website(), old.pending());
        restored.mined();
        assertEquals(1051, restored.total());
        assertEquals(0, new RankLocalProgress().total());
    }

    @Test void existingWorldLifetimeIsNotAddedOnFirstBaseline()
    {
        var progress = new RankLocalProgress();
        progress.mined();
        progress.reconcile(1_000_000);
        assertEquals(1_000_000, progress.total());
        progress.mined();
        assertEquals(1_000_001, progress.total());
    }

    @Test void crossingSeveralPlayersMovesTargetWithoutWebsiteUpdate()
    {
        var me = new RankOvertakeEstimate.Player("Miner", 4, 100);
        var rows = Map.of(3, new RankOvertakeEstimate.Player("Third", 3, 110),
                2, new RankOvertakeEstimate.Player("Second", 2, 120),
                1, new RankOvertakeEstimate.Player("First", 1, 130));
        assertEquals(4, RankOvertakeEstimate.project(me, rows, 110).player().rank());
        var projected = RankOvertakeEstimate.project(me, rows, 121);
        assertEquals(2, projected.player().rank());
        assertEquals("First", projected.target().name());
        assertEquals(10, projected.blocksNeeded());
        assertNull(RankOvertakeEstimate.project(me, rows, 131).target());
    }

    @Test void missingPageMustNotClaimFirstPlace()
    {
        var me = new RankOvertakeEstimate.Player("Miner", 102, 100);
        var rows = Map.of(101, new RankOvertakeEstimate.Player("Next", 101, 110));
        assertNull(RankOvertakeEstimate.project(me, rows, 111));
    }
}
