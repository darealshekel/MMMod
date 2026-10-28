package com.mmm.legacy.social;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatChannelStateTest {
    @Test void startsInVanillaAndReturnsToVanillaOnEveryClose() {
        var state = new ChatChannelState();
        assertFalse(state.isPublicChannel());
        state.select(true);
        state.initialize("");
        assertTrue(state.isPublicChannel());
        state.close();
        assertFalse(state.isPublicChannel());
    }
    @Test void prefilledCommandsSelectVanilla() {
        var state = new ChatChannelState();
        state.select(true);
        state.initialize("/");
        assertFalse(state.isPublicChannel());
    }
}
