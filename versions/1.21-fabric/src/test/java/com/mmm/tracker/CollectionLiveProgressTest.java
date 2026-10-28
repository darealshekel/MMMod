package com.mmm.tracker;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionLiveProgressTest {
    private static final String STONE = "minecraft:stone";
    @Test void nonInstantDeepslateSurvivesWebsiteRefreshAndRestart() {
        String regular = "minecraft:deepslate", special = "mmm:non_insta_deepslate";
        var progress = new CollectionLiveProgress();
        progress.reconcile(Map.of(regular, 100000L));
        progress.mined(regular);
        progress.mined(special);
        progress = CollectionLiveProgress.load(progress.save());
        progress.acknowledge(progress.receipt(""));
        progress.reconcile(Map.of(regular, 100001L));
        assertEquals(100001L, progress.counts().get(regular));
        assertEquals(1L, progress.counts().get(special));
        progress.mined(regular);
        assertEquals(1L, progress.counts().get(special));
    }
    @Test void combinedCoralAnnouncesOnceAndAbsorbsItsSync() {
        var progress = new CollectionLiveProgress();
        progress.reconcile(Map.of("minecraft:tube_coral_block", 5000L, "minecraft:dead_brain_coral_block", 4999L));
        assertEquals(List.of(10000L), progress.mined("minecraft:tube_coral_block"));
        progress = CollectionLiveProgress.load(progress.save());
        progress.acknowledge(progress.receipt(""));
        progress.reconcile(Map.of("minecraft:tube_coral_block", 5001L, "minecraft:dead_brain_coral_block", 4999L));
        assertEquals(10000L, progress.counts().get("mmm:coral"));
        assertTrue(progress.mined("minecraft:dead_brain_coral_block").isEmpty());
    }
    @Test void migratingOldColoredTerracottaDoesNotAnnounceHistoricalTiers() {
        var saved = new com.google.gson.JsonObject();
        for (String color : List.of("red", "blue")) {
            var old = new com.google.gson.JsonObject();
            old.addProperty("website", 6000L); old.addProperty("pending", 0L); old.addProperty("announced", 0L);
            saved.add("minecraft:" + color + "_terracotta", old);
        }
        var progress = CollectionLiveProgress.load(saved);
        assertEquals(12000L, progress.counts().get("minecraft:terracotta"));
        assertTrue(progress.mined("minecraft:red_terracotta").isEmpty());
    }
    @Test void announcesBeforeSyncAndNeverAgainAfterSyncOrRestart() {
        var progress = new CollectionLiveProgress();
        progress.reconcile(Map.of(STONE, 9999L));
        assertEquals(List.of(10000L), progress.mined(STONE));
        assertEquals(10000L, progress.counts().get(STONE));
        progress.reconcile(Map.of(STONE, 9999L));
        assertEquals(10000L, progress.counts().get(STONE));
        progress = CollectionLiveProgress.load(progress.save());
        progress.acknowledge(progress.receipt(""));
        progress.reconcile(Map.of(STONE, 10000L));
        assertEquals(10000L, progress.counts().get(STONE));
        assertTrue(progress.mined(STONE).isEmpty());
        progress.reconcile(Map.of(STONE, 9999L));
        assertTrue(progress.mined(STONE).isEmpty());
    }
    @Test void importedHistoryIsNotAnnounced() {
        var progress = new CollectionLiveProgress();
        progress.seed(Map.of(STONE, 1000000L));
        progress.reconcile(Map.of(STONE, 10000000L));
        assertTrue(progress.mined(STONE).isEmpty());
        assertEquals(10000001L, progress.counts().get(STONE));
    }
    @Test void partialSyncAbsorbsOnlyPublishedBlocks() {
        var progress = new CollectionLiveProgress();
        progress.reconcile(Map.of(STONE, 100L));
        for (int i = 0; i < 10; i++) progress.mined(STONE);
        progress.acknowledge(progress.receipt(""));
        progress.reconcile(Map.of(STONE, 104L));
        assertEquals(110L, progress.counts().get(STONE));
        progress.reconcile(Map.of(STONE, 104L));
        assertEquals(110L, progress.counts().get(STONE));
        progress.reconcile(Map.of(STONE, 110L));
        assertEquals(110L, progress.counts().get(STONE));
    }
    @Test void firstBaselineRetainsMissingLocalBlocks() {
        var progress = new CollectionLiveProgress();
        progress.seed(Map.of(STONE, 100L));
        progress.mined(STONE);
        progress.reconcile(Map.of(STONE, 80L));
        assertEquals(101L, progress.counts().get(STONE));
        progress.acknowledge(progress.receipt(""));
        progress.reconcile(Map.of(STONE, 101L));
        assertEquals(101L, progress.counts().get(STONE));
    }
    @Test void cappedCollectionsHaveNoExtraNotifications() {
        String block = "minecraft:gilded_blackstone";
        var progress = new CollectionLiveProgress();
        progress.reconcile(Map.of(block, 4999L));
        assertEquals(List.of(5000L), progress.mined(block));
        progress.reconcile(Map.of(block, 99999L));
        assertTrue(progress.mined(block).isEmpty());
    }
    @Test void accountInstancesAreIndependentAndValuesDoNotOverflow() {
        var first = new CollectionLiveProgress();
        first.reconcile(Map.of(STONE, Long.MAX_VALUE));
        assertTrue(first.mined(STONE).isEmpty());
        assertEquals(Long.MAX_VALUE, first.counts().get(STONE));
        var second = new CollectionLiveProgress();
        second.reconcile(Map.of(STONE, 9999L));
        assertEquals(List.of(10000L), second.mined(STONE));
    }
    @Test void existingTotalsGetOneCatchUpSummaryAndNoIndividualReplay() {
        var saved = new com.google.gson.JsonObject();
        var stone = new com.google.gson.JsonObject();
        stone.addProperty("website", 20000000L);
        stone.addProperty("pending", 0L);
        stone.addProperty("announced", 10000000L);
        saved.add(STONE, stone);
        var progress = CollectionLiveProgress.load(saved);
        progress.seed(Map.of("minecraft:oak_log", 100000L, "minecraft:diamond_ore", 1000L));
        assertTrue(progress.needsCatchUp());
        // Saving while loading website history must not discard the pending summary.
        progress = CollectionLiveProgress.load(progress.save());
        assertEquals(new CollectionLiveProgress.CatchUpSummary(16, 2), progress.finishCatchUp());
        assertFalse(progress.needsCatchUp());
        assertTrue(progress.mined(STONE).isEmpty());
        assertTrue(progress.mined("minecraft:oak_log").isEmpty());
        assertEquals(new CollectionLiveProgress.CatchUpSummary(0, 0), progress.finishCatchUp());
        progress = CollectionLiveProgress.load(progress.save());
        assertFalse(progress.needsCatchUp());
        assertEquals(new CollectionLiveProgress.CatchUpSummary(0, 0), progress.finishCatchUp());
        assertTrue(progress.mined(STONE).isEmpty());
    }
    @Test void catchUpIncludesWebsiteHistoryAndPreservesFutureMilestoneNotifications() {
        var progress = new CollectionLiveProgress();
        progress.seed(Map.of(STONE, 1000L));
        progress.reconcile(Map.of(STONE, 24999L));
        assertEquals(new CollectionLiveProgress.CatchUpSummary(3, 1), progress.finishCatchUp());
        assertEquals(List.of(25000L), progress.mined(STONE));
        progress = CollectionLiveProgress.load(progress.save());
        assertTrue(progress.mined(STONE).isEmpty());
    }
    @Test void freshPlayersAndUnchangedRareCollectionsHaveNoCatchUpAchievements() {
        var progress = new CollectionLiveProgress();
        progress.seed(Map.of("minecraft:diamond_ore", 1000L, "minecraft:gilded_blackstone", 5000L));
        assertEquals(new CollectionLiveProgress.CatchUpSummary(0, 0), progress.finishCatchUp());
        assertEquals(new CollectionLiveProgress.CatchUpSummary(0, 0), new CollectionLiveProgress().finishCatchUp());
    }
    @Test void miningWhileHistoryLoadsIsNotCountedAgainInTheCatchUpSummary() {
        var progress = new CollectionLiveProgress();
        progress.seed(Map.of(STONE, 2499L));
        progress.reconcile(Map.of(STONE, 2499L));
        assertEquals(List.of(2500L), progress.mined(STONE));
        progress = CollectionLiveProgress.load(progress.save());
        progress.acknowledge(progress.receipt(""));
        progress.reconcile(Map.of(STONE, 2500L, "minecraft:oak_log", 100000L));
        assertEquals(new CollectionLiveProgress.CatchUpSummary(6, 2), progress.finishCatchUp());
        assertEquals(2500L, progress.counts().get(STONE));
        assertTrue(progress.mined(STONE).isEmpty());
    }
}
