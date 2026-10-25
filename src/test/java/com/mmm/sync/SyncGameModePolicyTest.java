package com.mmm.sync;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SyncGameModePolicyTest {
    @Test void onlySurvivalAndAdventureAreAllowed() {
        assertTrue(SyncGameModePolicy.allowed("survival"));
        assertTrue(SyncGameModePolicy.allowed("adventure"));
        for (String mode : new String[]{"creative", "spectator", "unknown", "", null})
            assertFalse(SyncGameModePolicy.allowed(mode));
    }
    @Test void legacyAndCreativeQueueItemsCannotBeSent() {
        JsonObject payload = new JsonObject();
        assertFalse(SyncGameModePolicy.allowed(payload));
        payload.addProperty("game_mode", "creative");
        assertFalse(SyncGameModePolicy.allowed(payload));
        payload.addProperty("game_mode", "survival");
        assertTrue(SyncGameModePolicy.allowed(payload));
        JsonObject world = new JsonObject();
        world.addProperty("game_mode", "creative");
        payload.add("world", world);
        assertFalse(SyncGameModePolicy.allowed(payload));
    }
}
