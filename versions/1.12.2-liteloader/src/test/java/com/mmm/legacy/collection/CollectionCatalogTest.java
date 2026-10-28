package com.mmm.legacy.collection;

import com.mmm.leaderboard.BlockRankingCategory;
import com.mmm.tracker.CollectionProgress;
import com.mmm.tracker.CollectionRankClient;
import com.mmm.util.BlockBreakdownCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionCatalogTest {
    @Test void onlyLegacyBlocksAndMetadataVariantsAppearInCatalog() {
        var ids = CollectionCatalog.blockIds();
        assertEquals(ids.size(), new HashSet<>(ids).size());
        for (String id : List.of("minecraft:stone", "minecraft:diorite", "minecraft:andesite", "minecraft:granite",
                "minecraft:red_sand", "minecraft:sand", "minecraft:coarse_dirt", "minecraft:podzol",
                "minecraft:oak_leaves", "minecraft:dark_oak_log", "minecraft:nether_wart_block")) assertTrue(ids.contains(id), id);
        for (String id : List.of("minecraft:deepslate", "minecraft:pale_moss_block", "minecraft:ancient_debris",
                "minecraft:copper_ore", "minecraft:scaffolding", "minecraft:tube_coral_block", "mmm:non_insta_deepslate")) {
            assertFalse(ids.contains(id), id);
            assertFalse(CollectionIcons.valid(id), id);
        }
        for (String id : ids) assertFalse(CollectionIcons.unavailable(id), id);
        for (String id : ids) assertTrue(BlockBreakdownCatalog.isValid(id), id);
        assertThrows(UnsupportedOperationException.class, () -> ids.add("minecraft:made_up"));
    }
    @Test void groupedCollectionsHaveEveryTierInTheOverviewAndFitFiveBadges() {
        for (String id : CollectionCatalog.blockIds()) {
            var tiers = CollectionProgress.milestones(CollectionProgress.collectionId(id));
            assertTrue(tiers.size() <= 5, id);
            assertTrue(CollectionProgress.OVERVIEW_MILESTONES.containsAll(tiers), id);
        }
        assertTrue(BlockRankingCategory.PICKAXE.includes("mmm:coral"));
        assertTrue(BlockRankingCategory.PICKAXE.includes("mmm:non_insta_deepslate"));
        assertTrue(BlockRankingCategory.SHOVEL.includes("minecraft:red_sand"));
        assertTrue(BlockRankingCategory.AXE.includes("minecraft:oak_log"));
    }
    @Test void rankUiUsesServerPopulationAndExplicitUnsupportedState() {
        assertTrue(CollectionRankText.format(new CollectionRankClient.Rank(CollectionRankClient.Status.RANKED, 2, 1000)).contains("0.2%"));
        assertTrue(CollectionRankText.format(new CollectionRankClient.Rank(CollectionRankClient.Status.UNSUPPORTED, 0, 0)).contains("No website ranking"));
    }
    @Test void notificationTransportAcknowledgesSuccessAndContainsSynchronousFailures() {
        CollectionNotifications.setSender(message -> CompletableFuture.completedFuture(message.equals("accepted")));
        assertTrue(CollectionNotifications.send("accepted").join());
        assertFalse(CollectionNotifications.send("rejected").join());
        CollectionNotifications.setSender(message -> { throw new IllegalStateException("offline"); });
        assertFalse(CollectionNotifications.send("retry").join());
        CollectionNotifications.setSender(message -> CompletableFuture.completedFuture(false));
    }
}
