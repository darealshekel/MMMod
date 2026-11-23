package com.mmm.social;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilestoneChatTextTest
{
    @Test void onlyExistingStreaksGetText()
    {
        assertEquals("", MilestoneChatText.streakSuffix(0));
        assertEquals("", MilestoneChatText.streakSuffix(-1));
        assertEquals(" Daily streak: 1 day.", MilestoneChatText.streakSuffix(1));
        assertEquals(" Daily streak: 12 days.", MilestoneChatText.streakSuffix(12));
    }

    @Test void remoteStreakMustBeAnExplicitPositiveInteger()
    {
        assertEquals(7, MilestoneChatText.eventStreakDays(JsonParser.parseString("{\"streakDays\":7}").getAsJsonObject()));
        for (String json : new String[] {"{}", "{\"streakDays\":null}", "{\"streakDays\":-1}",
                "{\"streakDays\":\"7\"}", "{\"streakDays\":true}", "{\"streakDays\":1.5}",
                "{\"streakDays\":2147483648}", "{\"streakDays\":[]}"})
            assertEquals(0, MilestoneChatText.eventStreakDays(JsonParser.parseString(json).getAsJsonObject()), json);
    }
}
