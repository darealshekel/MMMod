package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
