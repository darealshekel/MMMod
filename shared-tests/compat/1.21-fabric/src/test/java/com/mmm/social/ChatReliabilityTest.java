package com.mmm.social;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatReliabilityTest {
    @Test void busyAndOverlengthDraftsHaveExplicitErrors() {
        assertFalse(PublicChatClient.validateDraft("hello", true).isEmpty());
        assertFalse(PublicChatClient.validateDraft("x".repeat(201), false).isEmpty());
        assertEquals("", PublicChatClient.validateDraft("x".repeat(200), false));
    }

    @Test void idleStreamExpiresButHeartbeatKeepsItAlive() {
        assertFalse(PublicChatClient.streamExpired(100_000L, 75_000L));
        assertTrue(PublicChatClient.streamExpired(100_000L, 24_999L));
    }
}
