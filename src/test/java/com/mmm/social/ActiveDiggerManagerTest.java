package com.mmm.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.mmm.config.Configs;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveDiggerManagerTest
{
    @AfterEach
    void clearState()
    {
        ActiveDiggerManager.clear();
    }

    @Test
    void appliesActiveDiggersAndWebsiteFriendsCaseInsensitively()
    {
        JsonObject state = new JsonObject();
        JsonArray active = new JsonArray();
        active.add("ActiveMiner");
        state.add("activeDiggers", active);
        JsonArray friends = new JsonArray();
        friends.add("ACTIVEMINER");
        state.add("friends", friends);
        state.addProperty("expiresInSeconds", 75);

        ActiveDiggerManager.applySocialState(state);

        assertTrue(ActiveDiggerManager.isVisible("activeminer", false, System.currentTimeMillis()));
        assertTrue(ActiveDiggerManager.isVisible("ActiveMiner", true, System.currentTimeMillis()));
        assertFalse(ActiveDiggerManager.isVisible("SomeoneElse", false, System.currentTimeMillis()));
        assertTrue(ActiveDiggerManager.visibleRemoteDiggers(List.of("SomeoneElse")).contains("ActiveMiner"));
        assertFalse(ActiveDiggerManager.visibleRemoteDiggers(List.of("ActiveMiner")).contains("ActiveMiner"));

        JsonObject refreshedFriends = new JsonObject();
        refreshedFriends.add("friends", new JsonArray());
        ActiveDiggerManager.applyFriends(refreshedFriends);
        assertFalse(ActiveDiggerManager.isVisible("ActiveMiner", true, System.currentTimeMillis()));
    }

    @Test
    void removesInactiveAndExpiredDiggers()
    {
        JsonObject event = new JsonObject();
        event.addProperty("username", "MinerOne");
        event.addProperty("active", true);
        event.addProperty("expiresInSeconds", 15);
        ActiveDiggerManager.applyPresence(event);
        assertTrue(ActiveDiggerManager.isVisible("MinerOne", false, System.currentTimeMillis()));

        event.addProperty("active", false);
        ActiveDiggerManager.applyPresence(event);
        assertFalse(ActiveDiggerManager.isVisible("MinerOne", false, System.currentTimeMillis()));
    }

    @Test
    void expiresPresenceAndRejectsNonBooleanActiveFields()
    {
        JsonObject event = new JsonObject();
        event.addProperty("username", "MinerOne");
        event.addProperty("active", true);
        event.addProperty("expiresInSeconds", 15);
        ActiveDiggerManager.applyPresence(event);
        assertFalse(ActiveDiggerManager.isVisible("MinerOne", false, System.currentTimeMillis() + 16_000));
        event.addProperty("active", "true");
        ActiveDiggerManager.applyPresence(event);
        assertFalse(ActiveDiggerManager.isVisible("MinerOne", false, System.currentTimeMillis()));
        ActiveDiggerManager.applyPresence(null);
    }

    @Test
    void filtersRemoteDiggersByWebsiteFriendsAndPreservesFriendsOnPartialResponses()
    {
        boolean show = Configs.Generic.SHOW_ACTIVE_DIGGERS.getBooleanValue();
        boolean friendsOnly = Configs.Generic.ACTIVE_DIGGERS_FRIENDS_ONLY.getBooleanValue();
        try {
            Configs.Generic.SHOW_ACTIVE_DIGGERS.setBooleanValue(true);
            Configs.Generic.ACTIVE_DIGGERS_FRIENDS_ONLY.setBooleanValue(true);
            JsonObject state = com.google.gson.JsonParser.parseString(
                    "{\"activeDiggers\":[\"Remote\",\"Other\",\"Local\"],\"friends\":[\"REMOTE\",\"Local\"]}").getAsJsonObject();
            ActiveDiggerManager.applySocialState(state);
            ActiveDiggerManager.applyFriends(new JsonObject());
            assertEquals(List.of("Remote"), ActiveDiggerManager.visibleRemoteDiggers(List.of("LOCAL")));
            Configs.Generic.SHOW_ACTIVE_DIGGERS.setBooleanValue(false);
            assertTrue(ActiveDiggerManager.visibleRemoteDiggers(List.of()).isEmpty());
        } finally {
            Configs.Generic.SHOW_ACTIVE_DIGGERS.setBooleanValue(show);
            Configs.Generic.ACTIVE_DIGGERS_FRIENDS_ONLY.setBooleanValue(friendsOnly);
        }
    }
}
