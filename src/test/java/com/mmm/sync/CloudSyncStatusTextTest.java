package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CloudSyncStatusTextTest
{
    @Test
    void explainsCommonRetryFailuresInPlainLanguage()
    {
        assertEquals(
                "The MMM sync service is temporarily unavailable.",
                CloudSyncManager.friendlyFailureReason("MMM sync is temporarily disabled."));
        assertEquals(
                "The website did not respond in time.",
                CloudSyncManager.friendlyFailureReason("request timed out"));
        assertEquals(
                "The website asked MMM to wait before trying again.",
                CloudSyncManager.friendlyFailureReason("HTTP 429"));
    }

    @Test
    void explainsRejectedWebsiteLinks()
    {
        assertEquals(
                "Website link required. Generate a new mod link code.",
                CloudSyncManager.friendlyFailureReason("Website link expired or was rejected."));
    }

    @Test
    void preservesUsefulUnknownServerMessages()
    {
        assertEquals(
                "Source approval is still pending.",
                CloudSyncManager.friendlyFailureReason("Source approval is still pending"));
    }

    @Test
    void startsCooldownOnlyForAcceptedOrServerConfirmedSourceSyncs()
    {
        assertEquals(0L, CloudSyncManager.sourceSyncCadenceAnchor(
                "{\"ok\":true,\"source_sync_accepted\":false}"));
        assertTrue(CloudSyncManager.sourceSyncCadenceAnchor(
                "{\"ok\":true,\"source_sync_accepted\":true}") > 0L);

        String nextSync = Instant.ofEpochMilli(System.currentTimeMillis() + 12L * 60L * 60L * 1000L).toString();
        String cooldownResponse = "{\"ok\":true,\"source_sync_accepted\":false,\"sync_skipped\":true,"
                + "\"reason\":\"24_hour_cooldown\",\"next_sync_at\":\"" + nextSync + "\","
                + "\"sync_policy\":{\"interval_ms\":86400000}}";
        assertTrue(CloudSyncManager.sourceSyncCadenceAnchor(cooldownResponse) > 0L);
    }

    @Test
    void explainsScoreboardAcceptanceAndMissingEvidence()
    {
        assertEquals(
                "No valid mining scoreboard was sent. Choose one with Sync Scoreboard.",
                CloudSyncManager.sourceSyncResponseDetail(
                        "{\"reason\":\"no_mining_scoreboard_evidence\"}", false, false, false));
        assertEquals(
                "Accepted scoreboard Digs [Total] with 17 players / 26,907,214 blocks. Next sync in 24 hours.",
                CloudSyncManager.sourceSyncResponseDetail(
                        "{\"source_sync_accepted\":true,\"source_sync\":{\"objective_title\":\"Digs [Total]\",\"player_count\":17,\"total_blocks\":26907214}}",
                        false, true, false));
    }
}