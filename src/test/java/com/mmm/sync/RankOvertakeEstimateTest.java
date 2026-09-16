package com.mmm.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


class RankOvertakeEstimateTest
{
    private static JsonObject response(String rows)
    {
        return JsonParser.parseString("{\"scope\":\"main\",\"rows\":[" + rows + "]}").getAsJsonObject();
    }

    private static String row(String name, int rank, long blocks)
    {
        return "{\"username\":\"" + name + "\",\"rank\":" + rank + ",\"blocksMined\":" + blocks + ",\"canonicalSourceRowTotal\":true}";
    }

    private static RankOvertakeEstimate estimate(long own, long target)
    {
        return RankOvertakeEstimate.fromTarget(new RankOvertakeEstimate.Player("Miner", 5, own),
                response(row("NextMiner", 4, target)));
    }

    @Test void exactCaseInsensitiveMatchDoesNotSelectSimilarNames()
    {
        var result = RankOvertakeEstimate.findPlayer(response(row("Miner2", 1, 200) + "," + row("MINER", 5, 100)), "Miner");
        assertEquals(5, result.rank());
        assertEquals(100, result.blocks());
    }

    @Test void unrankedPlayerHasNoInventedTotal()
    {
        assertNull(RankOvertakeEstimate.findPlayer(response(""), "Unknown"));
    }

    @Test void gapIncludesTheBlockNeededToPassRatherThanTie()
    {
        assertEquals(100_001, estimate(200_000, 300_000).blocksNeeded());
        assertEquals(1, estimate(200_000, 200_000).blocksNeeded());
    }

    @Test void usesTheSuppliedHudBlocksPerHourRate()
    {
        var estimate = estimate(100_000, 199_999);
        assertEquals(7200, estimate.estimatedSeconds(50_000));
        assertEquals(6000, estimate.estimatedSeconds(60_000));
        assertEquals(3600, estimate.estimatedSeconds(100_000));
    }

    @Test void waitsForSufficientPaceData()
    {
        assertEquals(-1, estimate(100, 200).estimatedSeconds(0));
        assertEquals(-1, estimate(100, 200).estimatedSeconds(-1));
        assertEquals(61, estimate(100, 200).estimatedSeconds(6000));
    }

    @Test void leadingPlayerHasNoOvertakeTarget()
    {
        var leading = new RankOvertakeEstimate(new RankOvertakeEstimate.Player("Leader", 1, 200), null);
        assertEquals(0, leading.blocksNeeded());
        assertEquals(-1, leading.estimatedSeconds(6000));
    }

    @Test void rejectsInconsistentOrChangedRankSnapshots()
    {
        var player = new RankOvertakeEstimate.Player("Miner", 5, 100);
        assertThrows(IllegalArgumentException.class, () -> RankOvertakeEstimate.fromTarget(player, response(row("Other", 3, 200))));
        assertThrows(IllegalArgumentException.class, () -> RankOvertakeEstimate.fromTarget(player, response(row("Other", 4, 99))));
        assertThrows(IllegalArgumentException.class, () -> RankOvertakeEstimate.fromTarget(player, response(row("Miner", 4, 200))));
    }

    @Test void rejectsWrongLeaderboardAndMalformedRows()
    {
        JsonObject flc = response(row("Miner", 5, 100));
        flc.addProperty("fastLeftClick", true);
        assertThrows(IllegalArgumentException.class, () -> RankOvertakeEstimate.findPlayer(flc, "Miner"));
        flc.addProperty("fastLeftClick", false);
        flc.addProperty("scope", "source");
        assertThrows(IllegalArgumentException.class, () -> RankOvertakeEstimate.findPlayer(flc, "Miner"));
        assertThrows(IllegalArgumentException.class, () -> RankOvertakeEstimate.findPlayer(response(row("Miner", 0, -1)), "Miner"));
    }

    @Test void largeTotalsDoNotOverflowIntegerArithmetic()
    {
        assertEquals(3_000_000_001L, estimate(1_000_000_000L, 4_000_000_000L).blocksNeeded());
    }

    @Test void bothSearchAndTargetRequestCurrentProfileAlignedRanks()
    {
        assertEquals("dataVersion=profile-aligned-ranks-v1&query=5hekel&pageSize=100",
                RankOvertakeEstimate.leaderboardUri("query=5hekel&pageSize=100").getQuery());
        assertEquals("dataVersion=profile-aligned-ranks-v1&pageSize=1&page=47",
                RankOvertakeEstimate.leaderboardUri("pageSize=1&page=47").getQuery());
    }

    @Test void currentShekelRankTargets47InsteadOfOld111()
    {
        var player = RankOvertakeEstimate.findPlayer(response(row("5hekel", 48, 26_421_703)), "5hekel");
        var result = RankOvertakeEstimate.fromTarget(player, response(row("floppydonkeyni", 47, 27_018_152)));
        assertEquals(48, result.player().rank());
        assertEquals(47, result.target().rank());
        assertEquals(596_450, result.blocksNeeded());
    }

    @Test void refusesUnverifiedSpreadsheetFallback()
    {
        var stale = response("{\"username\":\"5hekel\",\"rank\":112,\"blocksMined\":13345660}");
        assertThrows(IllegalArgumentException.class, () -> RankOvertakeEstimate.findPlayer(stale, "5hekel"));
    }

    @Test void remainingGapAndFasterPaceBothReduceTheEstimate()
    {
        assertEquals(3600, estimate(100_000, 159_999).estimatedSeconds(60_000));
        assertEquals(1800, estimate(130_000, 159_999).estimatedSeconds(60_000));
        assertEquals(900, estimate(130_000, 159_999).estimatedSeconds(120_000));
    }

    @Test void activeTimeIsNotPresentedAsCalendarDaysOrRoundedDown()
    {
        assertEquals("1s", RankOvertakeEstimate.formatActiveTime(1));
        assertEquals("2m", RankOvertakeEstimate.formatActiveTime(61));
        assertEquals("1h 0m", RankOvertakeEstimate.formatActiveTime(3599));
        assertEquals("25h 1m", RankOvertakeEstimate.formatActiveTime(90_001));
        assertEquals("Waiting for mining pace", RankOvertakeEstimate.formatActiveTime(-1));
    }
}
