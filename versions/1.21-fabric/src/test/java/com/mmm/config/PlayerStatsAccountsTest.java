package com.mmm.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStatsAccountsTest {
    @Test void switchingAccountsKeepsTheirOwnCountersAndPreservesCredentials() {
        JsonObject accounts = new JsonObject(), state = new JsonObject();
        state.addProperty("totalBlocksMined", 100L);
        state.addProperty("personalRecordDailyBlocks", 90L);
        state.addProperty("websiteSyncToken", "unchanged");
        JsonObject b = PlayerStatsAccounts.select(accounts, "a", state, "b");
        assertEquals(0, b.get("totalBlocksMined").getAsLong());
        assertEquals(0, b.get("personalRecordDailyBlocks").getAsLong());
        assertEquals("unchanged", b.get("websiteSyncToken").getAsString());
        b.addProperty("totalBlocksMined", 50L);
        JsonObject a = PlayerStatsAccounts.select(accounts, "b", b, "a");
        assertEquals(100, a.get("totalBlocksMined").getAsLong());
        assertEquals(50, accounts.getAsJsonObject("b").get("totalBlocksMined").getAsLong());
        assertFalse(accounts.getAsJsonObject("a").has("websiteSyncToken"));
    }

    @Test void unattributedLegacyDataIsPreservedButNotAssigned() {
        JsonObject accounts = new JsonObject(), old = new JsonObject();
        old.addProperty("totalBlocksMined", 100);
        assertEquals(0, PlayerStatsAccounts.select(accounts, "", old, "b").get("totalBlocksMined").getAsInt());
        assertEquals(100, accounts.getAsJsonObject("unattributed").get("totalBlocksMined").getAsInt());
    }
}
