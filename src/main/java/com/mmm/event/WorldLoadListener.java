package com.mmm.event;

import com.mmm.config.FeatureToggle;
import com.mmm.feature.PerimeterWallDigHelper;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.storage.WorldSessionContext;
import com.mmm.sync.CloudSyncManager;
import com.mmm.sync.DigsSyncManager;
import com.mmm.sync.SyncQueueManager;
import com.mmm.timer.MmmBlockBreakDetector;
import com.mmm.tracker.BlockBreakdownTracker;
import com.mmm.tracker.GoalNotificationManager;
import com.mmm.tracker.MiningStats;
import com.mmm.util.MmmDebugLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class WorldLoadListener
{
    private static final long WORLD_SWITCH_LOG_INTERVAL_MS = 30_000L;
    private static SessionData pendingSummary;
    private static String pendingSummaryName = "Unknown";
    private ClientLevel observedWorld;

    public void onJoin(Minecraft client)
    {
        this.observedWorld = client.level;
        handleWorldAvailable(client);
    }

    public void onDisconnect(Minecraft client)
    {
        handleWorldExit();
        this.observedWorld = null;
        GoalNotificationManager.clear();
        CloudSyncManager.resetForDisconnect();
        DigsSyncManager.resetForDisconnect();
        MiningStats.resetRollingMetrics();
        MmmBlockBreakDetector.clear();
    }

    public void pollWorldChange(Minecraft client)
    {
        if (client.level == this.observedWorld)
        {
            return;
        }

        ClientLevel previous = this.observedWorld;
        this.observedWorld = client.level;
        if (client.level == null)
        {
            if (previous != null)
            {
                onDisconnect(client);
            }
            return;
        }
        handleWorldAvailable(client);
    }

    private void handleWorldExit()
    {
        SessionData finished = MiningStats.finaliseSession();
        CloudSyncManager.requestScheduledSync("world exit");
        DigsSyncManager.requestScheduledSync("world exit");
        SyncQueueManager.requestFlush("world exit");
        if (FeatureToggle.MMM_SUMMARY_ON_EXIT.getBooleanValue() && finished.totalBlocks > 0)
        {
            pendingSummary = finished;
            pendingSummaryName = WorldSessionContext.getCurrentWorldName();
        }
        GoalNotificationManager.clear();
        MmmBlockBreakDetector.clear();
    }

    private void handleWorldAvailable(Minecraft client)
    {
        if (client.level == null)
        {
            return;
        }

        PerimeterWallDigHelper.refreshFromConfig();
        String previousWorldId = WorldSessionContext.getCurrentWorldId();
        WorldSessionContext.update(client);
        String nextWorldId = WorldSessionContext.getCurrentWorldId();
        if (!previousWorldId.equals(nextWorldId))
        {
            debugWorldSwitch(previousWorldId, nextWorldId);
            DigsSyncManager.resetForWorldChange(nextWorldId);
            SessionHistory.loadForWorld(nextWorldId);
            MiningStats.startWorldSession(nextWorldId);
            BlockBreakdownTracker.requestStatsOnWorldJoin();
        }
        SyncQueueManager.requestFlush("world join");
    }

    private void debugWorldSwitch(String previousWorldId, String nextWorldId)
    {
        WorldSessionContext.WorldInfo info = WorldSessionContext.getCurrentWorldInfo();
        MmmDebugLogger.info(
                "world-switch",
                WORLD_SWITCH_LOG_INTERVAL_MS,
                "[MMM_DEBUG] world-switch changed={} displayName={} sourceType={}",
                !previousWorldId.equals(nextWorldId),
                info.displayName(),
                info.sourceType());
    }

    public static SessionData consumePendingSummary()
    {
        SessionData value = pendingSummary;
        pendingSummary = null;
        return value;
    }

    public static String consumePendingSummaryName()
    {
        String value = pendingSummaryName;
        pendingSummaryName = "Unknown";
        return value;
    }
}