package com.mmm.leaderboard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeaderboardDataTest
{
    @Test void playerQueryPreservesUnderscoresAndUsesCurrentDataVersion() {
        var query = new LeaderboardData.Query(LeaderboardData.Kind.PLAYERS, null, " _SleepyZZZ ", 2);
        assertTrue(query.uri().toString().contains("query=_SleepyZZZ"));
        assertTrue(query.uri().toString().contains("dataVersion=latest-sync-v14"));
        assertTrue(query.uri().toString().contains("page=2"));
    }
    @Test void encodesSearchInsteadOfAllowingAdditionalParameters() {
        var query = new LeaderboardData.Query(LeaderboardData.Kind.PLAYERS, null, "a&source=other", 1);
        assertTrue(query.uri().toString().contains("a%26source%3Dother"));
    }
    @Test void parsesActualBlockValuesAndSearchPagination() {
        var query = new LeaderboardData.Query(LeaderboardData.Kind.BLOCKS, "minecraft:stone", "", 2);
        var page = LeaderboardData.parse(query, """
                {"blockId":"minecraft:stone","page":2,"pageSize":25,"matchingPlayers":26,"totalPlayers":99,"totalBlocks":5000000000,
                 "rows":[{"rank":26,"username":"_SleepyZZZ","blocks":3000000000,"worlds":2}]}
                """);
        assertEquals(2, page.pages());
        assertEquals(26, page.total());
        assertEquals(3000000000L, page.rows().get(0).blocks());
        assertEquals("_SleepyZZZ", page.rows().get(0).name());
        assertThrows(UnsupportedOperationException.class, () -> page.rows().clear());
    }
    @Test void rejectsWrongBlockAndMalformedResponses() {
        var query = new LeaderboardData.Query(LeaderboardData.Kind.BLOCKS, "minecraft:stone", "", 1);
        assertThrows(IllegalArgumentException.class, () -> LeaderboardData.parse(query, "{\"blockId\":\"minecraft:dirt\"}"));
        assertThrows(RuntimeException.class, () -> LeaderboardData.parse(query, "{}"));
    }
    @Test void preservesRanksFromWebsiteSearchRatherThanRenumberingRows() {
        var query = new LeaderboardData.Query(LeaderboardData.Kind.PLAYERS, null, "Miner", 1);
        var result = LeaderboardData.parse(query, """
                {"scope":"main","page":1,"pageSize":25,"totalPages":1,"totalRows":1,"totalBlocks":100,
                "rows":[{"rank":48,"username":"Miner","blocksMined":100,"sourceServer":"Enigma"}]}
                """);
        assertEquals(48, result.rows().get(0).rank());
    }
    @Test void filtersSingleplayerSourcesButHonorsExplicitServers() {
        String json = """
                [{"displayName":"SSP World","sourceType":"ssp","totalBlocks":900},
                 {"displayName":"Beta","sourceType":"server","totalBlocks":200,"playerCount":2},
                 {"displayName":"Alpha","sourceType":"server","totalBlocks":300,"playerCount":3},
                 {"displayName":"Hardcore World","sourceType":"world","totalBlocks":800},
                 {"displayName":"Individual World Digs 01","sourceType":"server","totalBlocks":1000}]
                """;
        var page = LeaderboardData.parse(new LeaderboardData.Query(LeaderboardData.Kind.SERVERS, null, "Beta", 1), json);
        assertEquals(1, page.total());
        assertEquals(2, page.rows().get(0).rank());
        assertEquals(500, page.blocks());
    }
    @Test void validatesQueryBounds() {
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardData.Query(LeaderboardData.Kind.PLAYERS, null, "", 0));
        assertThrows(IllegalArgumentException.class, () -> new LeaderboardData.Query(LeaderboardData.Kind.PLAYERS, null, "x".repeat(65), 1));
    }
    @Test void categoriesIncludeWebsiteBlockGroups() {
        assertTrue(BlockRankingCategory.HOE.includes("minecraft:flowering_azalea_leaves"));
        assertTrue(BlockRankingCategory.AXE.includes("minecraft:crimson_stem"));
        assertTrue(BlockRankingCategory.NETHER.includes("minecraft:glowstone"));
        assertTrue(BlockRankingCategory.OVERWORLD.includes("minecraft:gravel"));
        assertTrue(BlockRankingCategory.END.includes("minecraft:obsidian"));
        assertFalse(BlockRankingCategory.OVERWORLD.includes("minecraft:end_stone"));
        assertFalse(BlockRankingCategory.SHOVEL.includes("minecraft:stone"));
    }
}
