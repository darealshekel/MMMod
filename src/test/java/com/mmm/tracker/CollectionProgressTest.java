package com.mmm.tracker;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionProgressTest
{
    @Test void requestedCapsApplyWithoutCappingMinedCounts() {
        for (String id : List.of("diamond_ore", "emerald_ore", "deepslate_emerald_ore", "raw_iron_block", "spawner")) {
            assertEquals(List.of(100L, 250L, 500L, 750L, 1000L), CollectionProgress.milestones("minecraft:" + id));
            assertEquals(0, CollectionProgress.next(id, 1000));
        }
        for (String id : List.of("oak_log", "dark_oak_log", "pale_oak_log", "poplar_log", "obsidian")) {
            assertEquals(100000L, CollectionProgress.milestones("minecraft:" + id).getLast());
            assertEquals(0, CollectionProgress.next(id, 100000));
        }
        assertEquals(100000L, CollectionProgress.milestones("minecraft:crimson_stem").getLast());
        assertEquals(250000L, CollectionProgress.cap("minecraft:flowering_azalea_leaves"));
        assertEquals(5000L, CollectionProgress.cap("minecraft:gilded_blackstone"));
        assertEquals(25000L, CollectionProgress.cap("minecraft:rooted_dirt"));
        assertEquals(2500000L, CollectionProgress.cap("minecraft:dirt"));
        assertEquals(50000L, CollectionProgress.cap("minecraft:glowstone"));
    }
    @Test void smallerCapsHaveFiveReachableTiers() {
        assertEquals(List.of(1000L, 2500L, 5000L, 7500L, 10000L), CollectionProgress.milestones("minecraft:ancient_debris"));
        assertEquals(List.of(500L, 1250L, 2500L, 3750L, 5000L), CollectionProgress.milestones("minecraft:gilded_blackstone"));
        for (String id : List.of("diamond_ore", "ancient_debris", "gilded_blackstone")) {
            var tiers = CollectionProgress.milestones(id);
            for (int i = 0; i < tiers.size(); i++) {
                long goal = tiers.get(i);
                assertEquals(i, CollectionProgress.completed(id, goal - 1));
                assertEquals(i + 1, CollectionProgress.completed(id, goal));
                assertEquals(goal, CollectionProgress.next(id, goal - 1));
                assertTrue(CollectionProgress.OVERVIEW_MILESTONES.contains(goal));
            }
        }
    }
    @Test void scaffoldingStopsAtOneMillionAndLabelsRetainSmallValues() {
        assertEquals(List.of(10000L, 100000L, 1000000L), CollectionProgress.milestones("minecraft:scaffolding"));
        assertEquals(0, CollectionProgress.next("minecraft:scaffolding", 1000000L));
        assertEquals("100", CollectionProgress.targetLabel(100));
        assertEquals("750", CollectionProgress.targetLabel(750));
        assertEquals("1.25K", CollectionProgress.targetLabel(1250));
        assertEquals("2.5K", CollectionProgress.targetLabel(2500));
        assertEquals("7.5K", CollectionProgress.targetLabel(7500));
        assertEquals("1M", CollectionProgress.targetLabel(1000000));
    }
    @Test void stoneAndNetherrackEndAtTwentyFiveMillion() {
        for (String block : List.of("stone", "netherrack")) {
            assertEquals(5, CollectionProgress.milestones("minecraft:" + block).size());
            assertEquals(25000000L, CollectionProgress.next(block, 10000000L));
            assertEquals(4, CollectionProgress.completed(block, 24999999L));
            assertEquals(1, CollectionProgress.remaining(block, 24999999L));
            assertEquals(5, CollectionProgress.completed(block, 25000000L));
            assertEquals(0, CollectionProgress.next(block, 25000000L));
            assertEquals(Long.MAX_VALUE, CollectionProgress.remaining(block, Long.MAX_VALUE));
        }
        assertEquals(10000000L, CollectionProgress.cap("minecraft:deepslate"));
        assertEquals(1000000L, CollectionProgress.cap("mmm:non_insta_deepslate"));
    }
    @Test void combinedCollectionsSumTheirMembersOnly() {
        var grouped = CollectionProgress.groupCounts(Map.of("minecraft:terracotta", 2L, "minecraft:red_terracotta", 3L,
                "minecraft:tube_coral_block", 7L, "minecraft:dead_brain_coral_block", 11L, "minecraft:brain_coral_fan", 13L));
        assertEquals(5L, grouped.get("minecraft:terracotta"));
        assertEquals(18L, grouped.get("mmm:coral"));
        assertEquals(13L, grouped.get("minecraft:brain_coral_fan"));
        assertFalse(grouped.containsKey("minecraft:red_terracotta"));
    }
    @Test void milestoneBoundariesAndCompletion() {
        assertEquals(0, CollectionProgress.completed(9999));
        assertEquals(1, CollectionProgress.completed(10000));
        assertEquals(100000, CollectionProgress.next(10000));
        assertEquals(1, CollectionProgress.remaining(9999));
        assertEquals(4, CollectionProgress.completed(10000000));
        assertEquals(0, CollectionProgress.next(10000000));
        assertEquals(Long.MAX_VALUE, CollectionProgress.remaining(10000000));
    }
    @Test void duplicateWorldRecordsDoNotAddTwice() {
        var sum = CollectionProgress.aggregate(List.of(
                new CollectionProgress.Source("a", Map.of("stone", 100L, "dirt", 10L)),
                new CollectionProgress.Source("a", Map.of("stone", 110L)),
                new CollectionProgress.Source("b", Map.of("stone", 30L))));
        assertEquals(140L, sum.get("stone")); assertEquals(10L, sum.get("dirt"));
    }
    @Test void invalidAndHugeValuesAreSafe() {
        var sum = CollectionProgress.aggregate(List.of(
                new CollectionProgress.Source("a", Map.of("stone", Long.MAX_VALUE, "dirt", -1L)),
                new CollectionProgress.Source("b", Map.of("stone", 100L)),
                new CollectionProgress.Source("", Map.of("stone", 999L))));
        assertEquals(Long.MAX_VALUE, sum.get("stone")); assertFalse(sum.containsKey("dirt"));
    }
}
