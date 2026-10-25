package com.mmm.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebsiteAccessFailureTest {
    @Test void proxy403DoesNotEraseLinkOrDropRequest() {
        String body = "error code: 1010";
        assertFalse(SyncQueueManager.shouldInvalidateLink(403, body));
        assertEquals(SyncSendResult.Outcome.RETRY, SyncQueueManager.classifyAccessFailure(403, body).outcome());
        assertTrue(ApiClient.isUnstructuredAccessFailure(403, body));
    }

    @Test void htmlAndEmptyResponsesKeepLink() {
        for (int status : new int[] {401, 403}) {
            for (String body : new String[] {"", "<html>Access denied</html>", "{}"}) {
                assertFalse(SyncQueueManager.shouldInvalidateLink(status, body));
                assertEquals(SyncSendResult.Outcome.RETRY, SyncQueueManager.classifyAccessFailure(status, body).outcome());
            }
        }
    }

    @Test void genuineExpiredCredentialsStillRequireRelinking() {
        String body = "{\"error\":\"Website link expired. Generate a new mod link code.\"}";
        assertTrue(SyncQueueManager.shouldInvalidateLink(401, body));
        assertTrue(SyncQueueManager.shouldInvalidateLink(403, body));
        assertEquals(SyncSendResult.Outcome.DROP, SyncQueueManager.classifyAccessFailure(403, body).outcome());
        assertFalse(ApiClient.isUnstructuredAccessFailure(403, body));
    }

    @Test void SyncPermissionIsSeparateFromAccountLink() {
        String body = "{\"error\":\"Canary sync access required\"}";
        assertFalse(SyncQueueManager.shouldInvalidateLink(403, body));
        assertEquals(SyncSendResult.Outcome.DROP, SyncQueueManager.classifyAccessFailure(403, body).outcome());
        assertEquals("Canary sync access required", SyncQueueManager.classifyAccessFailure(403, body).detail());
    }

    @Test void unrelatedHttpResponsesAreNotAccessFailures() {
        for (int status : new int[] {200, 400, 429, 500}) {
            assertNull(SyncQueueManager.classifyAccessFailure(status, ""));
            assertFalse(SyncQueueManager.shouldInvalidateLink(status, "{\"error\":\"website link\"}"));
            assertFalse(ApiClient.isUnstructuredAccessFailure(status, ""));
        }
    }

    @Test void linkRetriesExplainTheFailure() {
        try {
            WebsiteLinkManager.onQueueRetry("Website connection blocked (HTTP 403).", 1);
            assertTrue(WebsiteLinkManager.getState().detail().contains("HTTP 403"));
            assertTrue(WebsiteLinkManager.getState().detail().contains("Next retry"));
        } finally { WebsiteLinkManager.reset(); }
    }
}
