package com.mmm.leaderboard;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MMM_LEADERBOARD_LIVE", matches = "true")
class LeaderboardLiveTest
{
    @Test void allViewsLoadRealPublicData() throws Exception {
        for (var kind : LeaderboardData.Kind.values()) {
            var page = LeaderboardClient.load(new LeaderboardData.Query(kind, "minecraft:stone", "", 1), false).get(25, TimeUnit.SECONDS);
            assertFalse(page.rows().isEmpty(), kind.label);
            assertTrue(page.rows().getFirst().blocks() > 0, kind.label);
            System.out.println("LIVE " + kind.label + ": " + page.rows().getFirst());
        }
    }
}
