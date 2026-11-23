package com.mmm.config;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudPresetsTest
{
    @Test void compactDefaultsDoNotOverrideSavedVisibility()
    {
        var option = Configs.Generic.BLOCK_STATS_VISIBLE;
        boolean before = option.getBooleanValue();
        try
        {
            assertFalse(option.getDefaultBooleanValue());
            JsonObject root = new JsonObject(), generic = new JsonObject();
            generic.addProperty(option.getName(), true);
            root.add("Generic", generic);
            MmmConfigIO.readConfigBase(root, "Generic", List.of(option));
            assertTrue(option.getBooleanValue());
        }
        finally { option.setBooleanValue(before); }
    }

    @Test void presetOnlyChangesHudVisibility()
    {
        JsonObject root = new JsonObject();
        MmmConfigIO.writeConfigBase(root, "Generic", Configs.Generic.PERSISTED_OPTIONS);
        boolean[] toggles = new boolean[FeatureToggle.VALUES.size()];
        for (int i = 0; i < toggles.length; i++) toggles[i] = FeatureToggle.VALUES.get(i).getBooleanValue();
        try
        {
            int goal = Configs.Generic.DAILY_GOAL.getIntegerValue();
            boolean sync = Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue();
            boolean tracker = FeatureToggle.MMM_MINING_TRACKER.getBooleanValue();
            HudPresets.compact();
            assertTrue(Configs.Generic.HUD_SESSION_TOTAL_VISIBLE.getBooleanValue());
            assertFalse(Configs.Generic.BLOCK_STATS_VISIBLE.getBooleanValue());
            assertFalse(FeatureToggle.MMM_HUD_SPEED_GRAPH.getBooleanValue());
            assertEquals(goal, Configs.Generic.DAILY_GOAL.getIntegerValue());
            assertEquals(sync, Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue());
            assertEquals(tracker, FeatureToggle.MMM_MINING_TRACKER.getBooleanValue());
        }
        finally
        {
            MmmConfigIO.readConfigBase(root, "Generic", Configs.Generic.PERSISTED_OPTIONS);
            for (int i = 0; i < toggles.length; i++) FeatureToggle.VALUES.get(i).setBooleanValue(toggles[i]);
        }
    }
}
