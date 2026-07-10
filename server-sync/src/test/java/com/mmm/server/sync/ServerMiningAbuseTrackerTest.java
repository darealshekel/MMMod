package com.mmm.server.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ServerMiningAbuseTrackerTest
{
    @Test
    void normalMovementIsNotFlagged()
    {
        ServerMiningAbuseTracker tracker = new ServerMiningAbuseTracker();
        UUID playerId = UUID.randomUUID();
        ServerMiningAbuseTracker.Evidence evidence = ServerMiningAbuseTracker.Evidence.empty();
        for (int index = 0; index < 100; index++)
        {
            evidence = tracker.record(playerId, index * 500L, index, 64, 0, index, 64, 0, index, 0);
        }
        assertFalse(evidence.suspicious());
    }

    @Test
    void repeatedCoordinateMiningIsFlaggedForReview()
    {
        ServerMiningAbuseTracker tracker = new ServerMiningAbuseTracker();
        UUID playerId = UUID.randomUUID();
        ServerMiningAbuseTracker.Evidence evidence = ServerMiningAbuseTracker.Evidence.empty();
        for (int index = 0; index < 4; index++)
        {
            evidence = tracker.record(playerId, index * 100L, 10, 64, 10, 10, 64, 10, 0, 0);
        }
        assertTrue(evidence.flags().containsKey("PLACE_AND_BREAK_PATTERN"));
    }

    @Test
    void stationaryHighVolumeMiningIsFlaggedAsAfkPattern()
    {
        ServerMiningAbuseTracker tracker = new ServerMiningAbuseTracker();
        UUID playerId = UUID.randomUUID();
        ServerMiningAbuseTracker.Evidence evidence = ServerMiningAbuseTracker.Evidence.empty();
        for (int index = 0; index < 300; index++)
        {
            evidence = tracker.record(playerId, index * 150L, index, 64, 0, 0, 64, 0, 0, 0);
        }
        assertTrue(evidence.flags().containsKey("AFK_MINING_PATTERN"));
    }
}
