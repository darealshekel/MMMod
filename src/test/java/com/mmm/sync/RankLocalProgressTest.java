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
        var progress = new RankLocalProgress(1000, 0);
        for (int i = 0; i < 150; i++) progress.mined();
        progress.acknowledge(progress.receipt(""));
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
        var progress = new RankLocalProgress(1000, 0);
        for (int i = 0; i < 10; i++) progress.mined("world-a");
        var receipt = progress.receipt("world-a");
        progress.mined();
        progress.acknowledge(receipt);
        progress.reconcile(1010);
        assertEquals(1011, progress.total());
    }

    @Test void lowerManualCorrectionReplacesBaseline()
    {
        var progress = new RankLocalProgress(1000, 10);
        progress.reconcile(900);
        assertEquals(910, progress.total());
    }

    @Test void unrelatedGainsNeverAcknowledgePendingMining() {
        var progress = new RankLocalProgress(1000, 0);
        for (int i = 0; i < 1000; i++) progress.mined("a");
        progress.reconcile(2000);
        assertEquals(3000, progress.total());
        progress.acknowledge(progress.receipt("b"));
        progress.reconcile(2100);
        assertEquals(3100, progress.total());
    }

    @Test void receiptsAreWorldScopedDurableAndIdempotent() {
        var progress = new RankLocalProgress(1000, 0);
        for (int i = 0; i < 10; i++) progress.mined("a");
        var receipt = progress.receipt("a");
        progress.mined("a");
        progress.mined("b");
        progress.acknowledge(receipt);
        progress = RankLocalProgress.load(progress.save());
        progress.acknowledge(receipt);
        progress.reconcile(1010);
        assertEquals(2, progress.pending());
        progress.acknowledge(receipt);
        progress.reconcile(1015);
        assertEquals(2, progress.pending());
    }

    @Test void websiteRefreshBeforeReceiptDoesNotDoubleCount() {
        var progress = new RankLocalProgress(1000, 0);
        for (int i = 0; i < 10; i++) progress.mined("a");
        var receipt = progress.receipt("a");
        progress.reconcile(1010);
        assertEquals(1020, progress.total());
        progress.acknowledge(receipt);
        assertEquals(1010, progress.total());
        progress.mined("b");
        progress.acknowledge(receipt);
        assertEquals(1011, progress.total());
    }

    @Test void migrationDoesNotConvertNewMiningIntoLegacyProgress() {
        var state = new com.google.gson.JsonObject();
        state.addProperty("website", 1000); state.addProperty("pending", 10);
        var progress = RankLocalProgress.load(state);
        progress.mined("a");
        progress = RankLocalProgress.load(progress.save());
        progress.reconcile(1100);
        assertEquals(1101, progress.total());
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
