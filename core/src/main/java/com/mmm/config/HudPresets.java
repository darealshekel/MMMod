package com.mmm.config;

import java.util.List;

public final class HudPresets
{
    private HudPresets() {}

    public static void compact()
    {
        for (var option : List.of(Configs.Generic.HUD_TITLE_VISIBLE, Configs.Generic.HUD_WORLD_TOTAL_VISIBLE,
                Configs.Generic.HUD_SESSION_TOTAL_VISIBLE, Configs.Generic.HUD_SESSION_TIME_VISIBLE))
            option.setBooleanValue(true);
        for (var option : List.of(Configs.Generic.HUD_GLOBAL_TOTAL_VISIBLE, Configs.Generic.HUD_RANK_OVERTAKE_VISIBLE,
                Configs.Generic.HUD_DAILY_WEEK_VISIBLE, Configs.Generic.HUD_RECORDS_VISIBLE,
                Configs.Generic.HUD_FASTEST_100K_VISIBLE, Configs.Generic.HUD_WORLD_PLAYTIME_VISIBLE,
                Configs.Generic.HUD_MOD_PLAYTIME_VISIBLE, Configs.Generic.HUD_ALL_WORLDS_PLAYTIME_VISIBLE,
                Configs.Generic.HUD_DAILY_RESET_VISIBLE, Configs.Generic.HUD_TIMER_STATUS_VISIBLE,
                Configs.Generic.TIMER_HUD_VISIBLE, Configs.Generic.HOURLY_STATS_VISIBLE,
                Configs.Generic.BLOCKS_PER_MINUTE_VISIBLE, Configs.Generic.BLOCK_STATS_VISIBLE,
                Configs.Generic.HUD_DAILY_GOAL_BAR_VISIBLE))
            option.setBooleanValue(false);
        for (var option : List.of(FeatureToggle.MMM_HUD, FeatureToggle.MMM_HUD_TOTAL_MINED,
                FeatureToggle.MMM_HUD_BLOCKS_PER_HOUR)) option.setBooleanValue(true);
        for (var option : List.of(FeatureToggle.MMM_HUD_PROJECT, FeatureToggle.MMM_HUD_GOAL_PROGRESS,
                FeatureToggle.MMM_HUD_ETA, FeatureToggle.MMM_HUD_SPEED_GRAPH)) option.setBooleanValue(false);
    }
}
