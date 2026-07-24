package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}