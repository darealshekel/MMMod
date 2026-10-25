package com.mmm.legacy;

import com.mmm.legacy.settings.LegacyUiRegistry;

/** Register feature controls only after their actual lifecycle and mixins are installed. */
final class LegacyNavigation {
    private LegacyNavigation() {}

    static void register() {
        for (String capability : new String[]{"toggle-tab", "block-esp", "goal-xp", "timer-credits", "items",
                "breaking-indicators", "lava", "goal-animation", "social", "collections", "scoreboard"}) {
            LegacyUiRegistry.enable(capability);
        }
        LegacyUiRegistry.register("leaderboards", "Leaderboards", "GENERAL", com.mmm.legacy.collection.LeaderboardScreen::new);
        LegacyUiRegistry.register("scoreboard", "Scoreboard", "GENERAL", com.mmm.scoreboard.ScoreboardScreen::new);
        LegacyUiRegistry.register("move-scoreboard", "Move Scoreboard", "GENERAL", com.mmm.scoreboard.ScoreboardMoveScreen::new);
        LegacyUiRegistry.register("sidebar-leaderboard", "Sidebar Leaderboard", "GENERAL", parent -> new com.mmm.legacy.collection.LeaderboardScreen(parent, true));
        LegacyUiRegistry.register("scoreboard-records", "Recorded Scoreboards", "GENERAL", com.mmm.scoreboard.ScoreboardRecordsScreen::new);
        LegacyUiRegistry.register("ignored-players", "Ignored Players", "SOCIAL", com.mmm.legacy.social.MmmChatIgnoreListScreen::new);
        LegacyUiRegistry.register("goal-sounds", "Milestone Sounds", "NOTIFICATIONS", com.mmm.legacy.achievement.GoalSoundSettingsScreen::new);
    }
}
