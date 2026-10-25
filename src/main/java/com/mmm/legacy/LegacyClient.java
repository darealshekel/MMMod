package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.config.Callbacks;
import com.mmm.hotkey.MmmHotkeyManager;
import com.mmm.storage.*;
import com.mmm.sync.*;
import com.mmm.timer.MmmTimerState;
import com.mmm.tracker.*;
import com.mmm.util.MmmLog;
import java.time.Duration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;

public final class LegacyClient {
    private static ClientWorld observedWorld;
    private static String activeWorld;
    private static SessionData pendingSummary;
    private static String summaryWorld;
    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        Configs.loadFromFile();
        MmmTimerState.load();
        Callbacks.init();
        LegacyNavigation.register();
        com.mmm.leaderboard.LeaderboardSidebar.register();
        com.mmm.legacy.collection.CollectionNotifications.setSender(com.mmm.social.PublicChatClient::sendCollectionMessage);
        com.mmm.scoreboard.ScoreboardService.setNameDecorator(com.mmm.tags.TierTagManager::decorateNameString);
        com.mmm.legacy.achievement.LegacyAchievementClient.init(Minecraft.getInstance());
        initialized = true;
    }

    public static void tick(Minecraft client) {
        if (!initialized) return;
        if (client.world != null && client.player != null) {
            if (client.world != observedWorld || activeWorld == null) {
                WorldSessionContext.update(client);
                String world = WorldSessionContext.getCurrentWorldId();
                if (!world.equals(activeWorld)) {
                    if (activeWorld != null) MiningStats.finaliseSession();
                    DigsSyncManager.resetForWorldChange(world);
                    SessionHistory.loadForWorld(world);
                    activeWorld = world;
                    MiningStats.startWorldSession(world);
                    BlockBreakdownTracker.requestStatsOnWorldJoin();
                    LegacyBlockItems.initialize();
                    com.mmm.feature.PerimeterWallDigHelper.refreshFromConfig();
                    SyncQueueManager.requestFlush("world join");
                    com.mmm.legacy.achievement.LegacyAchievementClient.onJoin(client);
                }
                observedWorld = client.world;
            }
        } else if (observedWorld != null) {
            beforeWorldExit();
        }
        MmmHotkeyManager.tick(client);
        com.mmm.legacy.command.LegacyCommands.tick(client);
        com.mmm.leaderboard.LeaderboardSidebar.tick(client);
        MiningStats.onClientTick();
        LiveCollections.tick(client);
        EfficiencyTracker.tick(client);
        PinnedCollection.tick(client);
        MmmTimerState.onClientTick(client);
        MiningSpeedTracker.tick(client);
        WebsiteProfileTotals.refresh(false);
        RankOvertakeClient.onClientTick(client);
        com.mmm.social.PublicChatClient.onClientTick(client);
        com.mmm.tags.TierTagManager.onClientTick(client);
        com.mmm.feature.TranslucentLavaRenderer.tick(client);
        com.mmm.legacy.achievement.LegacyAchievementClient.tick(client);
        if (client.screen == null && MmmTimerState.consumeCreditsPending()) {
            client.openScreen(new com.mmm.legacy.settings.TimerResultsScreen(null));
        }
        if (pendingSummary != null && client.world == null) {
            SessionData summary = pendingSummary;
            pendingSummary = null;
            client.openScreen(new LegacySessionScreen(client.screen, summary, summaryWorld));
        }
    }

    public static void toggleSession() {
        Minecraft client = Minecraft.getInstance();
        if (client.world == null || client.player == null) return;
        if (MiningStats.isSessionActive()) {
            SessionData finished = MiningStats.finaliseSession();
            client.openScreen(new LegacySessionScreen(client.screen, finished));
        } else MiningStats.startNewSession();
    }

    public static void beforeWorldExit() {
        if (!initialized || activeWorld == null) return;
        String name = WorldSessionContext.getCurrentWorldName();
        SessionData finished = MiningStats.finaliseSession();
        CloudSyncManager.requestScheduledSync("world exit");
        DigsSyncManager.requestScheduledSync("world exit");
        SyncQueueManager.requestFlush("world exit");
        MmmTimerState.save();
        LiveCollections.flush();
        if (FeatureToggle.MMM_SUMMARY_ON_EXIT.getBooleanValue() && finished.totalBlocks > 0) {
            pendingSummary = finished;
            summaryWorld = name;
        }
        activeWorld = null;
        observedWorld = null;
        CloudSyncManager.resetForDisconnect();
        DigsSyncManager.resetForDisconnect();
        com.mmm.social.PublicChatClient.disconnect();
        com.mmm.tags.TierTagManager.clear();
        com.mmm.legacy.achievement.LegacyAchievementClient.onDisconnect();
        com.mmm.feature.BreakingIndicatorRenderer.reset();
        com.mmm.scoreboard.ScoreboardState.resetPage();
        com.mmm.scoreboard.ScoreboardHudRenderer.invalidateLayout();
    }

    public static void shutdown() {
        if (!initialized) return;
        beforeWorldExit();
        Configs.saveToFile();
        MmmTimerState.save();
        AsyncPersistence.flush(Duration.ofSeconds(3));
    }
}
