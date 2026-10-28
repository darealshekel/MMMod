package com.mmm.tracker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionRankClientTest {
    private static String response(String rows, int population) {
        return "{\"blockId\":\"minecraft:stone\",\"totalPlayers\":" + population
                + ",\"matchingPlayers\":2,\"page\":1,\"pageSize\":25,\"rows\":" + rows + "}";
    }
    @Test void percentageUsesAllRankedPlayersNotSearchMatches() {
        var rank = CollectionRankClient.parse("minecraft:stone", "Miner", response(
                "[{\"username\":\"MinerTwo\",\"rank\":1},{\"username\":\"mINER\",\"rank\":28}]", 1000));
        assertEquals(CollectionRankClient.Status.RANKED, rank.status());
        assertEquals(28, rank.position());
        assertEquals("2.8%", rank.topPercent());
    }
    @Test void missingPlayerIsNotAssignedSomeoneElsesRank() {
        var rank = CollectionRankClient.parse("minecraft:stone", "Miner", response("[{\"username\":\"MinerTwo\",\"rank\":1}]", 100));
        assertEquals(CollectionRankClient.Status.UNRANKED, rank.status());
        assertEquals("", rank.topPercent());
    }
    @Test void invalidRankAndWrongBlockAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CollectionRankClient.parse("minecraft:dirt", "Miner", response("[]", 100)));
        for (int position : new int[] {0, 101})
            assertThrows(IllegalArgumentException.class, () -> CollectionRankClient.parse("minecraft:stone", "Miner",
                    response("[{\"username\":\"Miner\",\"rank\":" + position + "}]", 100)));
    }
    @Test void smallPercentagesNeverBecomeZeroAndUnsupportedCollectionsAreExplicit() {
        assertEquals("<0.1%", new CollectionRankClient.Rank(CollectionRankClient.Status.RANKED, 1, 10000).topPercent());
        assertEquals("100.0%", new CollectionRankClient.Rank(CollectionRankClient.Status.RANKED, 1, 1).topPercent());
        for (String collection : new String[] {"mmm:coral", "mmm:non_insta_deepslate", "minecraft:terracotta"})
            assertEquals(CollectionRankClient.Status.UNSUPPORTED, CollectionRankClient.get(collection, "Miner").status());
    }
}
