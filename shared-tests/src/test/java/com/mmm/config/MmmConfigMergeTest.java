package com.mmm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class MmmConfigMergeTest
{
    @Test
    void importsMissingGenericSettingsFromAeTweaks()
    {
        JsonObject legacy = new JsonObject();
        JsonObject generic = new JsonObject();
        generic.addProperty("dailyGoal", 250_000);
        generic.addProperty("hudScale", 1.25D);
        legacy.add("Generic", generic);

        JsonObject merged = MmmConfigMigration.mergeMissingValues(new JsonObject(), legacy);

        assertEquals(250_000, merged.getAsJsonObject("Generic").get("dailyGoal").getAsInt());
        assertEquals(1.25D, merged.getAsJsonObject("Generic").get("hudScale").getAsDouble());
    }

    @Test
    void currentMmmSettingsWinOverLegacyValues()
    {
        JsonObject current = new JsonObject();
        JsonObject currentGeneric = new JsonObject();
        currentGeneric.addProperty("dailyGoal", 300_000);
        current.add("Generic", currentGeneric);

        JsonObject legacy = new JsonObject();
        JsonObject legacyGeneric = new JsonObject();
        legacyGeneric.addProperty("dailyGoal", 100_000);
        legacyGeneric.addProperty("hudScale", 1.5D);
        legacy.add("Generic", legacyGeneric);

        JsonObject merged = MmmConfigMigration.mergeMissingValues(current, legacy);

        assertEquals(300_000, merged.getAsJsonObject("Generic").get("dailyGoal").getAsInt());
        assertEquals(1.5D, merged.getAsJsonObject("Generic").get("hudScale").getAsDouble());
    }

    @Test
    void importsMissingStateWithoutOverwritingCurrentCounters()
    {
        JsonObject current = new JsonObject();
        JsonObject currentState = new JsonObject();
        currentState.addProperty("weeklyBlocksMined", 55_000L);
        current.add("State", currentState);

        JsonObject legacy = new JsonObject();
        JsonObject legacyState = new JsonObject();
        legacyState.addProperty("weeklyBlocksMined", 99_000L);
        legacyState.addProperty("websiteSyncToken", "legacy-token");
        legacy.add("State", legacyState);

        JsonObject merged = MmmConfigMigration.mergeMissingValues(current, legacy);

        assertEquals(55_000L, merged.getAsJsonObject("State").get("weeklyBlocksMined").getAsLong());
        assertEquals("legacy-token", merged.getAsJsonObject("State").get("websiteSyncToken").getAsString());
    }

    @Test
    void preservesUnknownCurrentKeys()
    {
        JsonObject current = new JsonObject();
        current.addProperty("FutureSection", "keep-me");

        JsonObject merged = MmmConfigMigration.mergeMissingValues(current, new JsonObject());

        assertEquals("keep-me", merged.get("FutureSection").getAsString());
    }

    @Test
    void mergeIsIdempotent()
    {
        JsonObject legacy = new JsonObject();
        JsonObject generic = new JsonObject();
        generic.addProperty("dailyGoal", 125_000);
        legacy.add("Generic", generic);

        JsonObject once = MmmConfigMigration.mergeMissingValues(new JsonObject(), legacy);
        JsonObject twice = MmmConfigMigration.mergeMissingValues(once, legacy);

        assertEquals(once, twice);
        assertTrue(MmmConfigMigration.containsSettings(twice));
        assertFalse(twice.has("TweakToggles"));
    }
}
