package com.mmm.tracker;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionLegacyHistoryTest {
    @Test void preservesExplicitNonInstantWebsiteHistoryWithoutInventingLegacyMining() {
        var raw = CollectionWebsiteClient.parse("""
                {"blockBreakdowns":[{"worldId":"modern","items":[
                  {"blockId":"minecraft:deepslate","count":800},
                  {"blockId":"mmm:non_insta_deepslate","count":125}]}]}
                """);
        var progress = new CollectionLiveProgress(); progress.reconcile(raw.counts());
        var counts = LiveCollections.retainWebsiteHistory(progress.counts(), raw);
        assertEquals(125, counts.get("mmm:non_insta_deepslate"));
        assertEquals(800, counts.get("minecraft:deepslate"));
        assertEquals(0, progress.counts().getOrDefault("mmm:non_insta_deepslate", 0L));
    }
    @Test void doesNotDoubleCountSavedNonInstantHistoryOrModifyInput() {
        var local = Map.of("mmm:non_insta_deepslate", 300L, "minecraft:stone", 10L);
        var remote = new CollectionWebsiteClient.Snapshot(Map.of("mmm:non_insta_deepslate", 200L), 1);
        assertSame(local, LiveCollections.retainWebsiteHistory(local, remote));
        assertSame(local, LiveCollections.retainWebsiteHistory(local, null));
    }
    @Test void websitePlusUnsyncedUsesModernReconciliationWithoutDuplicatingPublishedBlocks() {
        var progress = new CollectionLiveProgress();
        progress.seed(Map.of("minecraft:stone", 100L));
        progress.reconcile(Map.of("minecraft:stone", 1000L));
        progress.mined("minecraft:stone"); progress.mined("minecraft:stone");
        assertEquals(1002L, progress.counts().get("minecraft:stone"));
        progress.reconcile(Map.of("minecraft:stone", 1001L));
        assertEquals(1002L, progress.counts().get("minecraft:stone"));
        progress.reconcile(Map.of("minecraft:stone", 1002L));
        assertEquals(1002L, progress.counts().get("minecraft:stone"));
    }
}
