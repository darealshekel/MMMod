package com.mmm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class MmmConfigMalformedTest
{
    @Test
    void malformedOrNonObjectConfigIsRejectedWithoutThrowing()
    {
        assertNull(MmmConfigMigration.parseJsonObject("{\"Generic\":"));
        assertNull(MmmConfigMigration.parseJsonObject("[1,2,3]"));
        assertNull(MmmConfigMigration.parseJsonObject(""));
    }

    @Test
    void unknownFieldsDoNotPreventLegacySettingsMigration()
    {
        JsonObject legacy = MmmConfigMigration.parseJsonObject("""
                {
                  "unknownFutureField": {"keep": true},
                  "Generic": {"dailyGoal": 35000},
                  "TweakToggles": {"tweakDailyGoal": true}
                }
                """);

        JsonObject migrated = MmmConfigMigration.mergeMissingValues(new JsonObject(), legacy);

        assertEquals(35_000, migrated.getAsJsonObject("Generic").get("dailyGoal").getAsInt());
        assertTrue(migrated.getAsJsonObject(MmmConfigMigration.TOGGLES_SECTION)
                .get("mmmDailyGoal").getAsBoolean());
    }
}
