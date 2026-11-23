package com.mmm.hud;

import com.mmm.tracker.CollectionProgress;
import com.mmm.tracker.CollectionRankClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinedBlockOverlayStateTest
{
    private static final long SECOND = 1_000_000_000L;

    @Test void holdsTenSecondsThenEasesOut()
    {
        var state = new MinedBlockOverlayState();
        assertEquals(0, state.opacity(0));
        assertTrue(state.mined("minecraft:stone", 0));
        assertEquals(1, state.opacity(10 * SECOND));
        assertEquals(0.125F, state.opacity(10 * SECOND + 400_000_000), 0.0001);
        assertEquals(0, state.opacity(10 * SECOND + 800_000_000));
    }

    @Test void continuousSameBlockMiningKeepsOverlayVisible()
    {
        var state = new MinedBlockOverlayState();
        state.mined("minecraft:netherrack", 0);
        for (int i = 1; i <= 600; i++)
        {
            long now = i * 50_000_000L;
            assertFalse(state.mined("minecraft:netherrack", now), "Same block must reuse its icon");
            assertEquals(1, state.opacity(now), "Continued mining must remain visible");
        }
        assertEquals(1, state.opacity(40 * SECOND));
        assertEquals(0.125F, state.opacity(40 * SECOND + 400_000_000), 0.0001);
        assertEquals(0, state.opacity(41 * SECOND));
    }

    @Test void sameBlockRestoresOverlayDuringFadeAndAfterHiding()
    {
        var state = new MinedBlockOverlayState();
        state.mined("minecraft:netherrack", 0);
        long fading = 10 * SECOND + 400_000_000;
        assertTrue(state.opacity(fading) < 1);
        assertFalse(state.mined("minecraft:netherrack", fading));
        assertEquals(1, state.opacity(fading));
        assertEquals(0, state.opacity(25 * SECOND));
        assertFalse(state.mined("minecraft:netherrack", 25 * SECOND));
        assertEquals(1, state.opacity(25 * SECOND));
    }

    @Test void changingBlockDuringFadeShowsNewBlockImmediately()
    {
        var state = new MinedBlockOverlayState();
        state.mined("minecraft:stone", 0);
        long fading = 10 * SECOND + 200_000_000;
        assertTrue(state.opacity(fading) < 1);
        state.mined("minecraft:dirt", fading);
        assertEquals("minecraft:dirt", state.blockId());
        assertEquals(1, state.opacity(fading));
    }

    @Test void rankRequestWaitsForStableBlockButNotForMiningToStop()
    {
        var state = new MinedBlockOverlayState();
        assertFalse(state.rankRequestReady(SECOND));
        state.mined("minecraft:stone", 0);
        assertFalse(state.rankRequestReady(500_000_000));
        state.mined("minecraft:stone", 700_000_000);
        assertTrue(state.rankRequestReady(800_000_000));
        state.mined("minecraft:dirt", SECOND);
        assertFalse(state.rankRequestReady(SECOND));
        assertTrue(state.rankRequestReady(2 * SECOND));
        assertFalse(state.rankRequestReady(12 * SECOND), "Hidden overlays must not request ranks");
        state.clear();
        assertFalse(state.rankRequestReady(15 * SECOND));
    }

    @Test void onlyDisplaysRealRankedPlacement()
    {
        assertEquals(" #28", MinedBlockOverlayState.rankLabel(
                new CollectionRankClient.Rank(CollectionRankClient.Status.RANKED, 28, 1000)));
        assertEquals("", MinedBlockOverlayState.rankLabel(null));
        for (var status : CollectionRankClient.Status.values())
            if (status != CollectionRankClient.Status.RANKED)
                assertEquals("", MinedBlockOverlayState.rankLabel(new CollectionRankClient.Rank(status, 28, 1000)));
    }

    @Test void clearAllowsSameBlockInNewWorld()
    {
        var state = new MinedBlockOverlayState();
        state.mined("minecraft:stone", 0);
        state.clear();
        assertNull(state.blockId());
        assertEquals(0, state.opacity(0));
        assertTrue(state.mined("minecraft:stone", 20 * SECOND));
        assertEquals(1, state.opacity(20 * SECOND));
    }

    @Test void invalidInputDoesNotReplaceBlock()
    {
        var state = new MinedBlockOverlayState();
        assertFalse(state.mined(null, 0));
        assertFalse(state.mined(" ", 0));
        assertNull(state.blockId());
        state.mined("minecraft:stone", 0);
        assertFalse(state.mined(null, 9 * SECOND));
        assertFalse(state.mined(" ", 9 * SECOND));
        assertEquals("minecraft:stone", state.blockId());
        assertEquals(0, state.opacity(11 * SECOND), "Invalid events must not extend the timer");
    }

    @Test void nextMilestoneChangesAtExactThreshold()
    {
        var before = progress("minecraft:stone", 999);
        var after = progress("minecraft:stone", 1000);
        assertEquals(0, before.tier());
        assertEquals(1000, before.next());
        assertEquals(1, after.tier());
        assertEquals(2500, after.next());
        assertEquals(0.4, after.fraction());
    }

    @Test void lowCapTiersUseExistingCollectionRules()
    {
        var ore = progress("minecraft:diamond_ore", 250);
        assertEquals(2, ore.tier());
        assertEquals(500, ore.next());
        assertEquals(0.5, ore.fraction());
        var debris = progress("minecraft:ancient_debris", 5000);
        assertEquals(7500, debris.next());
    }

    @Test void nearlyCompleteDoesNotRoundUpToCompletedPercentage()
    {
        assertEquals(99.9, progress("minecraft:stone", 4_999_999).percentage());
        assertEquals(100, progress("minecraft:diamond_ore", 1000).percentage());
    }

    @Test void completionRetainsRealCountWithoutInventingAnotherTier()
    {
        var completed = progress("minecraft:stone", 125_000_000);
        assertTrue(completed.complete());
        assertEquals(125_000_000, completed.count());
        assertEquals(25_000_000, completed.cap());
        assertEquals(1, completed.fraction());
        assertEquals(CollectionProgress.milestones("minecraft:stone").size(), completed.tier());
    }

    @Test void usesCombinedCollectionCountAndStillSwitchesRawBlock()
    {
        var progress = MinedBlockOverlayState.Progress.from("minecraft:red_terracotta",
                Map.of("minecraft:terracotta", 5000L));
        assertEquals("minecraft:terracotta", progress.collectionId());
        assertEquals(5000, progress.count());
        var state = new MinedBlockOverlayState();
        state.mined("minecraft:red_terracotta", 0);
        assertTrue(state.mined("minecraft:blue_terracotta", SECOND));
    }

    @Test void emptyOrNegativeCountsStartAtFirstMilestone()
    {
        assertEquals(0, progress("minecraft:stone", -5).count());
        var empty = MinedBlockOverlayState.Progress.from("minecraft:stone", Map.of());
        assertEquals(1000, empty.next());
        assertEquals(0, empty.fraction());
    }

    @Test void compactLayoutLeavesAimHotbarAndPlaytimeClear()
    {
        for (int height : new int[] {240, 270, 300, 360, 540})
        {
            var layout = MinedBlockOverlayState.Layout.fit(320, height, 360);
            assertTrue(layout.x() >= 8);
            assertTrue(layout.x() + 360 * layout.scale() <= 312.01);
            assertTrue(layout.y() >= height / 2F + 12, "Crosshair obscured");
            float bottom = layout.y() + MinedBlockOverlayState.Layout.HEIGHT * layout.scale();
            assertTrue(bottom < height - 48, "Hotbar/health obscured");
            assertTrue(layout.playtimeY() + 9 < layout.y() || layout.playtimeY() > bottom, "Playtime obscured");
        }
    }

    @Test void normalLayoutKeepsExistingPlaytimePosition()
    {
        var layout = MinedBlockOverlayState.Layout.fit(640, 360, 240);
        assertEquals(1, layout.scale());
        assertEquals(228, layout.y());
        assertEquals(278, layout.playtimeY());
    }

    @Test void customPositionCanReachEveryScreenCorner()
    {
        for (int x : new int[] {0, 820}) for (int y : new int[] {0, 460})
        {
            var layout = MinedBlockOverlayState.Layout.fit(640, 360, 220, 1.5, x, y);
            assertEquals(1.5, layout.scale());
            assertEquals(x == 0 ? 0 : 310, layout.x());
            assertEquals(y == 0 ? 0 : 306, layout.y());
        }
    }

    @Test void resizingAndViewportChangesKeepCustomPlacementOnScreen()
    {
        for (int width : new int[] {320, 640, 960}) for (double requested : new double[] {0.25, 1, 1.75, 3})
        {
            var layout = MinedBlockOverlayState.Layout.fit(width, 240, 360, requested, 820, 460);
            assertTrue(layout.x() >= 0);
            assertTrue(layout.y() >= 0);
            assertTrue(layout.x() + 360 * layout.scale() <= width + 0.5);
            assertTrue(layout.y() + MinedBlockOverlayState.Layout.HEIGHT * layout.scale() <= 240.5);
        }
    }

    @Test void resettingPositionRestoresAutomaticPlacement()
    {
        assertEquals(MinedBlockOverlayState.Layout.fit(640, 360, 220),
                MinedBlockOverlayState.Layout.fit(640, 360, 220, 1, -1, -1));
        var moved = MinedBlockOverlayState.Layout.fit(640, 360, 220, 1, 0, 0);
        assertEquals(0, moved.y(), "Custom position must not be forced below the crosshair");
    }

    private static MinedBlockOverlayState.Progress progress(String id, long count)
    {
        return MinedBlockOverlayState.Progress.from(id, Map.of(id, count));
    }
}
