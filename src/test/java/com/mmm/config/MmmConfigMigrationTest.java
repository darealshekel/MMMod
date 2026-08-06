package com.mmm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class MmmConfigMigrationTest
{
    @Test
    void migratesLegacyToggleNames()
    {
        JsonObject root = new JsonObject();
        JsonObject legacy = new JsonObject();
        legacy.addProperty("tweakMiningTracker", false);
        legacy.addProperty("tweakDailyGoal", true);
        root.add("TweakToggles", legacy);

        MmmConfigMigration.migrateLegacyFeatureToggleSections(root);

        JsonObject migrated = root.getAsJsonObject(MmmConfigMigration.TOGGLES_SECTION);
        assertEquals(false, migrated.get("mmmMiningTracker").getAsBoolean());
        assertEquals(true, migrated.get("mmmDailyGoal").getAsBoolean());
    }

    @Test
    void keepsExistingMmmValueWhenLegacyValueAlsoExists()
    {
        JsonObject root = new JsonObject();
        JsonObject current = new JsonObject();
        current.addProperty("mmmMiningTracker", true);
        root.add(MmmConfigMigration.TOGGLES_SECTION, current);
        JsonObject legacy = new JsonObject();
        legacy.addProperty("tweakMiningTracker", false);
        root.add("TweakToggles", legacy);

        MmmConfigMigration.migrateLegacyFeatureToggleSections(root);

        assertEquals(true, root.getAsJsonObject(MmmConfigMigration.TOGGLES_SECTION)
                .get("mmmMiningTracker").getAsBoolean());
    }

    @Test
    void migratesLegacyFeatureHotkeys()
    {
        JsonObject root = new JsonObject();
        JsonObject legacy = new JsonObject();
        legacy.addProperty("tweakToggleTab", "KEY_T");
        root.add("TweakHotkeys", legacy);

        MmmConfigMigration.migrateLegacyFeatureToggleSections(root);

        assertEquals("KEY_T", root.getAsJsonObject(MmmConfigMigration.HOTKEYS_SECTION)
                .get("mmmToggleTab").getAsString());
    }
}