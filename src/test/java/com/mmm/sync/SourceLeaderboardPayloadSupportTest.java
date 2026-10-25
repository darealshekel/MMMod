package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SourceLeaderboardPayloadSupportTest
{
    @Test
    void completeClientEvidenceKeepsEveryValidScoreboardPlayer()
    {
        List<SourceLeaderboardEntry> rows = List.of(
                new SourceLeaderboardEntry("alex4", 300L, 1),
                new SourceLeaderboardEntry("Timer_16K", 200L, 2),
                new SourceLeaderboardEntry("5hekel", 100L, 3));

        SourceLeaderboardPayloadSupport.FilterResult result = SourceLeaderboardPayloadSupport.filterEntries(null, rows);

        assertEquals(rows, result.entries());
        assertTrue(result.fakeUsernames().isEmpty());
        assertFalse(result.filterCollapsedScoreboard());
        assertEquals(0L, result.removedDigs());
    }

    @Test
    void sourceTotalCannotBeLowerThanTheActiveRowSum()
    {
        List<SourceLeaderboardEntry> rows = List.of(
                new SourceLeaderboardEntry("One", 400L, 1),
                new SourceLeaderboardEntry("Two", 300L, 2));
        SourceLeaderboardSnapshot snapshot = new SourceLeaderboardSnapshot("Server", "Total", 1L, 500L, rows);

        assertEquals(700L, SourceLeaderboardPayloadSupport.resolveTotal(snapshot, rows));
    }

    @Test
    void deduplicatesCaseOnlyRowsWithoutAddingBothScores()
    {
        List<SourceLeaderboardEntry> rows = List.of(
                new SourceLeaderboardEntry("CurrentName", 500L, 1),
                new SourceLeaderboardEntry("currentname", 480L, 2));
        SourceLeaderboardSnapshot snapshot = new SourceLeaderboardSnapshot("Server", "Total", 1L, 980L, rows);

        SourceLeaderboardPayloadSupport.FilterResult result = SourceLeaderboardPayloadSupport.filterEntries(null, rows);

        assertEquals(1, result.entries().size());
        assertEquals("CurrentName", result.entries().get(0).username());
        assertEquals(500L, result.entries().get(0).digs());
        assertEquals(480L, result.removedDigs());
        assertEquals(500L, SourceLeaderboardPayloadSupport.resolveTotal(snapshot, result));
    }
}
