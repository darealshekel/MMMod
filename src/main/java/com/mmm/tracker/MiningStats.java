package com.mmm.tracker;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import com.mmm.config.Configs;
import com.mmm.config.Configs.ProjectEntry;
import com.mmm.config.FeatureToggle;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.storage.ActiveSessionCheckpoint;
import com.mmm.storage.MiningCalendarStore;
import com.mmm.storage.WorldSessionContext;
import com.mmm.MMM;
import com.mmm.sync.CloudSyncManager;
import com.mmm.sync.DigsSyncManager;
import com.mmm.sync.ScoreboardSourceResolver;
import com.mmm.sync.ScoreboardParser;
import com.mmm.sync.SyncQueueManager;
import com.mmm.timer.MmmTimerState;
import com.mmm.util.BlockBreakdownCatalog;
import com.mmm.util.MmmDebugLogger;
import com.mmm.util.DailyProgressPolicy;
import com.mmm.util.PeriodKeys;
import com.mmm.util.WeeklyProgressPolicy;
import com.mmm.util.UiFormat;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

public final class MiningStats
{
    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long STREAK_GAP_MS = 5_000L;
    private static final long AUTO_MINING_REQUIRED_MS = 10_000L;
    private static final long AUTO_MINING_GAP_MS = 2_000L;
    private static final long AUTO_PAUSE_IDLE_MS = 90_000L;
    private static final long FASTEST_100K_TARGET = 100_000L;
    private static final long TOTAL_MINED_PERSIST_INTERVAL_MS = 5_000L;
    private static final long SESSION_CHECKPOINT_INTERVAL_MS = 5_000L;
    private static final long PERIOD_STATS_CHECK_INTERVAL_MS = 1_000L;
    private static final long BLOCK_MINED_DEBUG_LOG_INTERVAL_MS = 5_000L;
    private static final long SESSION_DEBUG_LOG_INTERVAL_MS = 30_000L;
    private static final long SCOREBOARD_BOOTSTRAP_SKIPPED_LOG_INTERVAL_MS = 10_000L;
    private static final long SOURCE_UPDATE_DEBUG_LOG_INTERVAL_MS = 5_000L;
    private static final int TICKS_PER_SECOND = 20;
    private static final int BPH_WINDOW_TICKS = 72_000;
    private static final int BPS_UPDATE_INTERVAL_TICKS = 10;
    private static final int BPS_WINDOW_TICKS = 100;
    private static final ZoneId DAILY_RESET_ZONE = ZoneId.of("UTC");

    private static final LongArrayFIFOQueue MINE_EVENTS = new LongArrayFIFOQueue();
    private static final LongArrayFIFOQueue FASTEST_100K_EVENT_TIMES = new LongArrayFIFOQueue();
    private static final RollingMiningMetrics METRIC_TICK_COUNTS = new RollingMiningMetrics(BPH_WINDOW_TICKS);
    private static SessionData currentSession = new SessionData(System.currentTimeMillis());
    private static String currentWorldId = "default";
    private static boolean currentSourceScoreboardAuthoritative;
    private static boolean sessionActive = true;
    private static boolean sessionPaused;
    private static long pausedAtMs;
    private static long pausedAccumulatedMs;
    private static long sessionStartTotalMined;
    private static long pausedSessionMinedOffset;
    private static long lastPersistedTotalMinedMs;
    private static long lastSessionCheckpointMs;
    private static boolean session100kRecorded;
    private static long autoMiningStreakStartMs;
    private static long lastValidBlockMineMs;
    private static boolean sessionAutoPaused;
    private static boolean sessionMenuPaused;
    private static long metricTickIndex;
    private static long lastBpsUpdateTick;
    private static long lastBphUpdateTick;
    private static long lastScoreboardSessionUpdateActiveElapsedMs;
    private static long sessionActiveTicks;
    private static int currentTickBpsBlocks;
    private static int currentTickBphBlocks;
    private static double rollingBlocksPerSecond;
    private static double rollingBlocksPerHour;
    private static double displayedBlocksPerSecond;
    private static double displayedBlocksPerHour;
    private static Configs.BpsSmoothing lastBpsSmoothing = Configs.BpsSmoothing.FAST;

    private static long streakStartMs;
    private static long lastMineMs;
    private static long lastDailyResetCheckMs;
    private static long lastWorldContextRefreshMs;
    private static long lastPeriodStatsCheckMs;

    private MiningStats()
    {
    }

    public static void startWorldSession(String worldId)
    {
        currentWorldId = worldId == null || worldId.isBlank() ? "default" : worldId;
        currentSourceScoreboardAuthoritative = false;
        long now = System.currentTimeMillis();
        MiningSanityGuard.resetWorld(currentWorldId);
        sessionActive = false;
        resetSession();
        resetRollingMetrics();
        MiningSpeedTracker.resetSession();
        touchCurrentWorldStats(now);
        restoreActiveSession(now);

        resetDailyProgressIfNeeded();
        resetPeriodStatsIfNeeded(System.currentTimeMillis());

        GoalNotificationManager.clear();
        CloudSyncManager.requestScheduledSync("world join");
    }

    public static synchronized SessionData finaliseSession()
    {
        boolean wasActive = sessionActive;
        if (sessionPaused)
        {
            pausedAccumulatedMs += Math.max(0L, System.currentTimeMillis() - pausedAtMs);
            pausedAtMs = 0L;
            sessionPaused = false;
            sessionAutoPaused = false;
        }
        sessionMenuPaused = false;

        resetDailyProgressIfNeeded();
        resetPeriodStatsIfNeeded(System.currentTimeMillis());
        MiningCalendarStore.flush();
        currentSession.endTimeMs = System.currentTimeMillis() - pausedAccumulatedMs;
        if (wasActive && shouldPersistSession(currentSession))
        {
            SessionHistory.save(currentSession);
        }

        SessionData finished = currentSession;
        if (wasActive && shouldPersistSession(finished))
        {
            CloudSyncManager.syncFinishedSession(finished);
        }
        if (wasActive)
        {
            ActiveSessionCheckpoint.clear(currentWorldId);
        }
        sessionActive = false;
        resetSession();
        resetRollingMetrics();
        MiningSpeedTracker.resetSession();
        Configs.saveToFile();
        GoalNotificationManager.clear();
        return finished;
    }

    public static void recordBlockMined(Block block)
    {
        recordBlockMined(block, null, null);
    }

    public static void recordBlockMined(Block block, BlockPos pos, BlockState previousState)
    {
        long now = System.currentTimeMillis();
        if (BlockBreakdownCatalog.isValid(block) == false)
        {
            return;
        }

        String dimensionId = getCurrentDimensionId();
        if (MiningSanityGuard.shouldAcceptBlock(pos, WorldSessionContext.getCurrentWorldId(), dimensionId, now) == false)
        {
            MmmDebugLogger.info(
                    "miningstats.sanity-block-skipped",
                    BLOCK_MINED_DEBUG_LOG_INTERVAL_MS,
                    "[MMM_DEBUG] sanity-block-skipped dimension={} duplicateRejects={} minuteCapRejects={}",
                    dimensionId,
                    MiningSanityGuard.getWorldDuplicateCoordinateRejects(),
                    MiningSanityGuard.getMinuteCapRejects());
            return;
        }

        handleAutoSessionOnValidMine(now);
        recordSuccessfulHarvestForRollingMetrics();

        boolean authoritativeMode = DigsSyncManager.hasAuthoritativeTotalDigs();
        long beforeLifetime = Configs.totalBlocksMined;
        long beforeSourceTotal = getCurrentSourceTotalMined();
        long beforeSession = Math.max(0L, currentSession.totalBlocks);

        // Local events advance fallback/session counters immediately. Once a validated
        // mining scoreboard is seen in this world, World Total and sync stay pinned
        // to that scoreboard instead of exposing this local prediction.
        Configs.totalBlocksMined++;
        if (Configs.websiteGlobalTotalBlocks > 0L && Configs.websiteGlobalTotalBlocks < Long.MAX_VALUE)
        {
            Configs.websiteGlobalTotalBlocks++;
            Configs.websiteGlobalTotalUpdatedAtMs = now;
        }
        Configs.WorldStatsEntry worldStats = touchCurrentWorldStats(now);
        worldStats.totalBlocks++;
        if (worldStats.scoreboardTotalUpdatedAtMs > 0L)
        {
            worldStats.pendingLocalBlocks++;
        }
        recordCurrentWorldBlockBreakdown(worldStats, block, now);
        MmmTimerState.onBlockMined(block);

        resetDailyProgressIfNeeded();
        long previousDaily = Configs.dailyProgress;
        Configs.dailyProgress++;
        recordPeriodBlocksMined(1L, now);
        MiningCalendarStore.recordBlock(now);
        recordFastest100kWindow(now);
        GoalNotificationManager.onGoalProgressChanged(previousDaily, getDailyGoalProgress());

        ProjectEntry active = Configs.getActiveProject();
        if (active != null)
        {
            active.progress++;
        }

        if (sessionPaused == false)
        {
            MINE_EVENTS.enqueue(now);
            pruneOldEvents(now);
        }

        if (sessionActive && sessionPaused == false)
        {
            currentSession.totalBlocks++;
            currentSession.endTimeMs = now;
            currentSession.recordMineEvent(getActiveElapsedMs(now));
            recordFastest100kIfReached(now);

            if (lastMineMs == 0L || now - lastMineMs > STREAK_GAP_MS)
            {
                streakStartMs = now;
            }
            lastMineMs = now;
            currentSession.bestStreakSeconds = Math.max(currentSession.bestStreakSeconds, (now - streakStartMs) / 1000L);

            if (block != null)
            {
                String key = BlockBreakdownCatalog.blockId(block);
                currentSession.blockBreakdown.merge(key, 1L, Long::sum);
            }
        }

        CloudSyncManager.onBlockMined(now);

        if (now - lastPersistedTotalMinedMs >= TOTAL_MINED_PERSIST_INTERVAL_MS)
        {
            Configs.requestSave();
            lastPersistedTotalMinedMs = now;
        }

        long afterSourceTotal = getCurrentSourceTotalMined();
        debugAttribution("manual-block",
                beforeSourceTotal,
                afterSourceTotal,
                Math.max(0L, afterSourceTotal - beforeSourceTotal));
        if (MmmDebugLogger.shouldLog("miningstats.block-mined", BLOCK_MINED_DEBUG_LOG_INTERVAL_MS))
        {
            WorldSessionContext.WorldInfo world = WorldSessionContext.getCurrentWorldInfo();
            MMM.LOGGER.info(
                    "[MMM_DEBUG] block-mined worldName={} authoritative={} sessionActive={} sessionBefore={} sessionAfter={} lifetimeBefore={} lifetimeAfter={}",
                    world.displayName(),
                    authoritativeMode,
                    sessionActive,
                    beforeSession,
                    currentSession.totalBlocks,
                    beforeLifetime,
                    Configs.totalBlocksMined
            );
        }
    }

    public static void resetSession()
    {
        MINE_EVENTS.clear();
        currentSession = new SessionData(System.currentTimeMillis());
        streakStartMs = 0L;
        lastMineMs = 0L;
        pausedAtMs = 0L;
        pausedAccumulatedMs = 0L;
        sessionPaused = false;
        sessionStartTotalMined = Math.max(0L, getCurrentSourceTotalMined());
        pausedSessionMinedOffset = 0L;
        session100kRecorded = false;
        autoMiningStreakStartMs = 0L;
        lastValidBlockMineMs = 0L;
        sessionAutoPaused = false;
        sessionMenuPaused = false;
        lastScoreboardSessionUpdateActiveElapsedMs = 0L;
        sessionActiveTicks = 0L;
    }

    public static void startNewSession()
    {
        resetSession();
        resetRollingMetrics();
        MiningSpeedTracker.resetSession();
        sessionActive = true;
        MmmTimerState.onSessionStarted();
        sessionStartTotalMined = getCurrentSourceTotalMined();
        WorldSessionContext.WorldInfo world = WorldSessionContext.getCurrentWorldInfo();
        MmmDebugLogger.info(
                "miningstats-session-start",
                SESSION_DEBUG_LOG_INTERVAL_MS,
                "[MMM_DEBUG] session-start worldName={} sessionStartSourceTotal={} lifetime={}",
                world.displayName(),
                sessionStartTotalMined,
                Configs.totalBlocksMined);
        CloudSyncManager.syncHeartbeat();
        checkpointActiveSession(System.currentTimeMillis(), true);
    }

    public static boolean toggleSession()
    {
        if (sessionActive)
        {
            finaliseSession();
            return false;
        }

        startNewSession();
        return true;
    }

    public static boolean togglePauseSession()
    {
        if (sessionActive == false)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        sessionMenuPaused = false;
        if (sessionPaused)
        {
            pausedAccumulatedMs += Math.max(0L, now - pausedAtMs);
            pausedAtMs = 0L;
            sessionPaused = false;
            sessionAutoPaused = false;
            rollingBlocksPerHour = calculateSessionBph();
            rollingBlocksPerSecond = calculateRollingBps(lastBpsSmoothing);
            updateDisplayedRollingMetrics();
        }
        else
        {
            pausedAtMs = now;
            sessionPaused = true;
            sessionAutoPaused = false;
            freezeRollingMetrics();
        }

        CloudSyncManager.syncHeartbeat();
        checkpointActiveSession(now, true);

        return sessionPaused;
    }

    public static boolean isSessionActive()
    {
        return sessionActive;
    }

    public static boolean isSessionPaused()
    {
        return sessionActive && sessionPaused;
    }

    private static void updateMenuPauseState(MinecraftClient client, long now)
    {
        if (sessionActive == false)
        {
            sessionMenuPaused = false;
            return;
        }
        if (client == null || client.world == null || client.player == null)
        {
            return;
        }

        boolean pauseRequested = client.isInSingleplayer()
                && client.currentScreen != null
                && (client.currentScreen instanceof GameMenuScreen
                || client.currentScreen.shouldPause());
        if (pauseRequested)
        {
            if (sessionPaused == false)
            {
                pausedAtMs = now;
                sessionPaused = true;
                sessionAutoPaused = false;
                sessionMenuPaused = true;
                freezeRollingMetrics();
                CloudSyncManager.syncHeartbeat();
                checkpointActiveSession(now, true);
                MmmDebugLogger.info(
                        "miningstats-menu-pause",
                        SESSION_DEBUG_LOG_INTERVAL_MS,
                        "[MMM_DEBUG] session-menu-paused");
            }
            return;
        }

        if (sessionMenuPaused == false)
        {
            return;
        }

        sessionMenuPaused = false;
        if (sessionPaused == false)
        {
            return;
        }

        long pausedDurationMs = Math.max(0L, now - pausedAtMs);
        pausedAccumulatedMs += pausedDurationMs;
        pausedAtMs = 0L;
        sessionPaused = false;
        sessionAutoPaused = false;
        if (lastValidBlockMineMs > 0L)
        {
            lastValidBlockMineMs += pausedDurationMs;
        }
        if (autoMiningStreakStartMs > 0L)
        {
            autoMiningStreakStartMs += pausedDurationMs;
        }
        rollingBlocksPerHour = calculateSessionBph();
        rollingBlocksPerSecond = calculateRollingBps(lastBpsSmoothing);
        updateDisplayedRollingMetrics();
        CloudSyncManager.syncHeartbeat();
        checkpointActiveSession(now, true);
        MmmDebugLogger.info(
                "miningstats-menu-resume",
                SESSION_DEBUG_LOG_INTERVAL_MS,
                "[MMM_DEBUG] session-menu-resumed");
    }

    public static void onClientTick()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hasMiningContext = client != null && client.world != null && client.player != null;
        long now = System.currentTimeMillis();
        updateMenuPauseState(client, now);
        if (hasMiningContext && now - lastWorldContextRefreshMs >= 1_000L)
        {
            lastWorldContextRefreshMs = now;
            WorldSessionContext.update(client);
        }

        if (now - lastDailyResetCheckMs >= 1_000L)
        {
            lastDailyResetCheckMs = now;
            resetDailyProgressIfNeeded();
            resetPeriodStatsIfNeeded(now);
        }

        updateRollingMetrics(hasMiningContext);
        DigsSyncManager.onClientTick(now);
        CloudSyncManager.onClientTick(now);
        BlockBreakdownTracker.onClientTick(client, now);
        SyncQueueManager.onClientTick(now);
        maybeAutoPauseSession(now);
        checkpointActiveSession(now, false);
    }

    public static int getBlocksPerHour()
    {
        return getEstimatedBlocksPerHour();
    }

    public static int getEstimatedBlocksPerHour()
    {
        if (sessionActive == false)
        {
            return 0;
        }

        return SessionData.clampBlocksPerHour(Math.round(rollingBlocksPerHour));
    }

    public static double getEstimatedBlocksPerSecond()
    {
        if (sessionActive == false)
        {
            return 0D;
        }

        return Math.max(0D, Math.min(20D, rollingBlocksPerSecond));
    }

    public static int getDisplayedBlocksPerHour()
    {
        if (sessionActive == false)
        {
            return 0;
        }

        return SessionData.clampBlocksPerHour(Math.round(displayedBlocksPerHour));
    }

    public static double getDisplayedBlocksPerSecond()
    {
        if (sessionActive == false)
        {
            return 0D;
        }

        return Math.max(0D, Math.min(20D, displayedBlocksPerSecond));
    }

    public static boolean hasActualBlocksPerHour()
    {
        return getSessionDurationMs() >= ONE_HOUR_MS;
    }

    public static int getActualBlocksPerHour()
    {
        return currentSession.getAverageBlocksPerHour();
    }

    public static long getTotalMined()
    {
        return Configs.totalBlocksMined;
    }

    public static long getGlobalTotalMinedForDisplay()
    {
        long websiteTotal = Math.max(0L, Configs.websiteGlobalTotalBlocks);
        return websiteTotal > 0L ? websiteTotal : Math.max(0L, Configs.totalBlocksMined);
    }

    public static long getCurrentSourceTotalMined()
    {
        Configs.WorldStatsEntry worldStats = getCurrentWorldStats();
        if (worldStats == null)
        {
            return 0L;
        }
        return SourceTotalPolicy.resolve(
                worldStats.totalBlocks,
                worldStats.scoreboardTotalBlocks,
                currentSourceScoreboardAuthoritative);
    }

    public static boolean hasAuthoritativeCurrentSourceScoreboardTotal()
    {
        Configs.WorldStatsEntry worldStats = getCurrentWorldStats();
        return worldStats != null && currentSourceScoreboardAuthoritative;
    }

    public static long getCurrentSourcePendingLocalBlocks()
    {
        Configs.WorldStatsEntry worldStats = getCurrentWorldStats();
        return worldStats == null ? 0L : Math.max(0L, worldStats.pendingLocalBlocks);
    }

    public static long getSessionTotal()
    {
        return getSessionBlocksMined();
    }

    public static long getSessionBlocksMined()
    {
        return Math.max(0L, currentSession.totalBlocks);
    }

    public static void applyScoreboardTotalMined(long totalDigs, String objectiveTitle, boolean parserValidated, long now)
    {
        if (totalDigs < 0L
                || (parserValidated == false && ScoreboardParser.isMiningEvidence(objectiveTitle) == false))
        {
            return;
        }

        Configs.WorldStatsEntry worldStats = touchCurrentWorldStats(now);
        long previousSourceTotal = Math.max(0L, worldStats.totalBlocks);
        long previousScoreboardTotal = Math.max(0L, worldStats.scoreboardTotalBlocks);
        boolean firstScoreboardSnapshot = worldStats.scoreboardTotalUpdatedAtMs <= 0L;


        long effectiveScoreboardTotal = totalDigs;
        long scoreboardIncrease = firstScoreboardSnapshot
                ? 0L
                : Math.max(0L, effectiveScoreboardTotal - previousScoreboardTotal);
        long consumedPendingBlocks = firstScoreboardSnapshot
                ? 0L
                : Math.min(Math.max(0L, worldStats.pendingLocalBlocks), scoreboardIncrease);

        if (firstScoreboardSnapshot)
        {
            worldStats.pendingLocalBlocks = 0L;
        }
        else
        {
            worldStats.pendingLocalBlocks = Math.max(0L, worldStats.pendingLocalBlocks - consumedPendingBlocks);
        }

        worldStats.scoreboardTotalBlocks = effectiveScoreboardTotal;
        worldStats.scoreboardTotalUpdatedAtMs = now;
        currentSourceScoreboardAuthoritative = true;
        // Keep a local fallback estimate for future visits where the server does not expose the
        // scoreboard. Authoritative readers use scoreboardTotalBlocks directly.
        worldStats.totalBlocks = effectiveScoreboardTotal + worldStats.pendingLocalBlocks;
        worldStats.lastSeenAt = now;

        long effectiveDelta = worldStats.totalBlocks - previousSourceTotal;
        if (effectiveDelta != 0L)
        {
            Configs.totalBlocksMined = Math.max(0L, Configs.totalBlocksMined + effectiveDelta);
            // Scoreboard reconciliation updates lifetime/source totals only.
            // Daily, weekly, and PR counters advance from accepted local block breaks.
            // A validated scoreboard can legitimately move down after an
            // objective correction. Persist and sync that correction too.
            CloudSyncManager.onBlockMined(now);
        }
        // Scoreboard snapshots reconcile source and lifetime totals only. They may
        // arrive in batches or briefly switch objectives, so treating their delta
        // as live mining can add the same blocks to a session more than once.
        // Session progress is sourced exclusively from accepted local harvests.

        debugAttribution("authoritative-update", previousSourceTotal, worldStats.totalBlocks, Math.max(0L, effectiveDelta));

        if (now - lastPersistedTotalMinedMs >= TOTAL_MINED_PERSIST_INTERVAL_MS)
        {
            Configs.requestSave();
            lastPersistedTotalMinedMs = now;
        }
    }

    public static void bootstrapSourceTotalFromScoreboard(long scoreboardPlayerTotal,
                                                            String scoreboardSourceName,
                                                            String objectiveTitle,
                                                            boolean parserValidated,
                                                            long now)
    {
        if (scoreboardPlayerTotal <= 0L
                || (parserValidated == false && ScoreboardParser.isMiningEvidence(objectiveTitle) == false))
        {
            return;
        }

        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        if (sourceMatchesCurrentWorld(worldInfo, scoreboardSourceName) == false)
        {
            if (MmmDebugLogger.shouldLog("miningstats.scoreboard-bootstrap-skipped", SCOREBOARD_BOOTSTRAP_SKIPPED_LOG_INTERVAL_MS))
            {
                MMM.LOGGER.info(
                        "[MMM_DEBUG] scoreboard-bootstrap-skipped worldName={} scoreboardSourceName={}",
                        worldInfo.displayName(),
                        scoreboardSourceName
                );
            }
            return;
        }

        applyScoreboardTotalMined(scoreboardPlayerTotal, objectiveTitle, parserValidated, now);
    }

    public static void applyMinecraftStatsBlockBreakdown(Map<String, Long> breakdown, long now)
    {
        Map<String, Long> sanitized = Configs.sanitizeBlockBreakdown(breakdown);
        if (sanitized.isEmpty())
        {
            return;
        }

        Configs.WorldStatsEntry worldStats = touchCurrentWorldStats(now);
        Map<String, Long> previous = Configs.sanitizeBlockBreakdown(worldStats.blockBreakdown);
        boolean breakdownChanged = sanitized.equals(previous) == false
                || Configs.BLOCK_BREAKDOWN_SOURCE_MINECRAFT_STATS.equals(worldStats.blockBreakdownSource) == false;

        if (breakdownChanged)
        {
            worldStats.blockBreakdown = new LinkedHashMap<>(sanitized);
            worldStats.blockBreakdownSource = Configs.BLOCK_BREAKDOWN_SOURCE_MINECRAFT_STATS;
            worldStats.blockBreakdownUpdatedAtMs = now;
            worldStats.lastSeenAt = now;
        }

        if (breakdownChanged)
        {
            Configs.saveToFile();
            CloudSyncManager.syncHeartbeat();
        }
    }

    public static String getSessionDurationClock()
    {
        long totalSeconds = Math.max(0L, getSessionDurationMs() / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String getDailyResetCountdownClock()
    {
        resetDailyProgressIfNeeded();

        ZonedDateTime now = ZonedDateTime.now(DAILY_RESET_ZONE);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(DAILY_RESET_ZONE);
        long remainingMs = Math.max(0L, nextMidnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli());

        long totalSeconds = remainingMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static long getSessionDurationMs()
    {
        if (sessionActive == false)
        {
            return 0L;
        }

        long now = sessionPaused ? pausedAtMs : System.currentTimeMillis();
        return Math.max(0L, now - currentSession.startTimeMs - pausedAccumulatedMs);
    }

    private static long getActiveElapsedMs(long now)
    {
        return Math.max(0L, now - currentSession.startTimeMs - pausedAccumulatedMs);
    }

    public static String getEstimatedTimeToDailyGoal()
    {
        GoalProgress progress = getDailyGoalProgress();
        if (progress.enabled() == false || progress.target() <= 0 || progress.current() >= progress.target())
        {
            return "Complete";
        }

        int blocksPerHour = (int) Math.round(rollingBlocksPerHour);
        if (blocksPerHour <= 0)
        {
            return isSessionPaused() ? "Paused" : "Calculating...";
        }

        long remainingBlocks = progress.target() - progress.current();
        long seconds = Math.max(1L, Math.round((remainingBlocks * 3600.0D) / blocksPerHour));
        return UiFormat.formatDuration(seconds);
    }

    public static GoalProgress getDailyGoalProgress()
    {
        long now = System.currentTimeMillis();
        resetDailyProgressIfNeeded();
        resetPeriodStatsIfNeeded(now);
        return new GoalProgress("Daily Goal", FeatureToggle.MMM_DAILY_GOAL.getBooleanValue(), Math.max(0L, Configs.dailyBlocksMined), Configs.Generic.DAILY_GOAL.getIntegerValue());
    }

    public static long getDailyBlocksMined()
    {
        resetPeriodStatsIfNeeded(System.currentTimeMillis());
        return Math.max(0L, Configs.dailyBlocksMined);
    }

    public static String getDailyBlocksDate()
    {
        resetPeriodStatsIfNeeded(System.currentTimeMillis());
        return Configs.dailyBlocksDate == null ? "" : Configs.dailyBlocksDate;
    }

    public static long getWeeklyBlocksMined()
    {
        resetPeriodStatsIfNeeded(System.currentTimeMillis());
        return Math.max(0L, Configs.weeklyBlocksMined);
    }

    public static String getWeeklyBlocksWeek()
    {
        resetPeriodStatsIfNeeded(System.currentTimeMillis());
        return Configs.weeklyBlocksWeek == null ? "" : Configs.weeklyBlocksWeek;
    }

    public static long getPersonalRecordDailyBlocks()
    {
        resetPeriodStatsIfNeeded(System.currentTimeMillis());
        return Math.max(Configs.personalRecordDailyBlocks, Configs.dailyBlocksMined);
    }

    public static long getPersonalRecordWeeklyBlocks()
    {
        resetPeriodStatsIfNeeded(System.currentTimeMillis());
        return Math.max(Configs.personalRecordWeeklyBlocks, Configs.weeklyBlocksMined);
    }

    public static long getFastest100kMs()
    {
        return Math.max(0L, Configs.fastest100kMs);
    }
    public static synchronized boolean applyAuthoritativeFastest100k(long seconds, long startedAtMs, long finishedAtMs)
    {
        if (seconds < 5_000L || seconds > 315_360_000L)
        {
            return false;
        }

        long durationMs = seconds * 1_000L;
        long currentMs = Math.max(0L, Configs.fastest100kMs);
        boolean currentIsImpossible = currentMs > 0L && currentMs < 5_000_000L;
        if (!currentIsImpossible && currentMs > 0L && currentMs <= durationMs)
        {
            return false;
        }

        long timestampDurationMs = finishedAtMs - startedAtMs;
        boolean timestampsMatch = startedAtMs > 0L
                && finishedAtMs > startedAtMs
                && Math.abs(timestampDurationMs - durationMs) <= 2_000L;
        Configs.fastest100kMs = durationMs;
        Configs.fastest100kStartedAtMs = timestampsMatch ? startedAtMs : 0L;
        Configs.fastest100kFinishedAtMs = timestampsMatch ? finishedAtMs : 0L;
        Configs.saveToFile();
        return true;
    }

    public static long getFastest100kSeconds()
    {
        return Math.max(0L, Math.round(getFastest100kMs() / 1000.0D));
    }

    public static String getFastest100kClock()
    {
        long fastestMs = getFastest100kMs();
        return fastestMs <= 0L ? "--" : UiFormat.formatDuration(Math.max(1L, Math.round(fastestMs / 1000.0D)));
    }

    public static ProjectProgress getActiveProjectProgress()
    {
        ProjectEntry activeProject = Configs.getActiveProject();
        if (activeProject == null)
        {
            return new ProjectProgress("No Project", 0L);
        }
        return new ProjectProgress(activeProject.name, activeProject.progress);
    }

    public static SessionData getCurrentSession()
    {
        if (sessionActive == false)
        {
            currentSession.endTimeMs = currentSession.startTimeMs;
            return currentSession;
        }

        long now = sessionPaused ? pausedAtMs : System.currentTimeMillis();
        currentSession.endTimeMs = now - pausedAccumulatedMs;
        return currentSession;
    }

    public static SessionData simulateDevFinishedSession()
    {
        long now = System.currentTimeMillis();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int durationMinutes = random.nextInt(180, 721);
        long targetBlocks = random.nextLong(100_000L, 500_001L);
        long startTimeMs = now - durationMinutes * ONE_MINUTE_MS - 30_000L;
        SessionData session = new SessionData(startTimeMs);

        long totalBlocks = 0L;
        double[] weights = new double[durationMinutes];
        double totalWeight = 0.0D;
        for (int minute = 0; minute < durationMinutes; minute++)
        {
            double warmup = Math.min(1.0D, (minute + 1) / 30.0D);
            double cooldown = 1.0D - Math.max(0.0D, (minute - durationMinutes + 45) / 120.0D) * 0.35D;
            double wave = 1.0D
                    + Math.sin((minute + random.nextInt(0, 16)) / 9.0D) * 0.12D
                    + Math.sin((minute + random.nextInt(0, 40)) / 31.0D) * 0.08D;
            double jitter = random.nextDouble(0.82D, 1.22D);
            double weight = Math.max(0.25D, warmup * cooldown * wave * jitter);
            weights[minute] = weight;
            totalWeight += weight;
        }

        for (int minute = 0; minute < durationMinutes; minute++)
        {
            long bucketBlocks = Math.max(0L, Math.round(targetBlocks * (weights[minute] / totalWeight)));
            if (minute == durationMinutes - 1)
            {
                bucketBlocks = Math.max(0L, targetBlocks - totalBlocks);
            }
            else if (totalBlocks + bucketBlocks > targetBlocks)
            {
                bucketBlocks = Math.max(0L, targetBlocks - totalBlocks);
            }
            session.recordMinedAmount(minute * ONE_MINUTE_MS, bucketBlocks);
            totalBlocks += bucketBlocks;
        }

        session.totalBlocks = totalBlocks;
        session.endTimeMs = startTimeMs + durationMinutes * ONE_MINUTE_MS;
        session.bestStreakSeconds = random.nextLong(12L * 60L, Math.min(90L * 60L, Math.max(13L * 60L, durationMinutes * 45L)));
        session.blockBreakdown = buildDevSessionBreakdown(totalBlocks);
        return session;
    }

    public static void setDailyProgress(long value)
    {
        long now = System.currentTimeMillis();
        long progress = Math.max(0L, value);
        Configs.dailyBlocksDate = PeriodKeys.currentDailyKey(now);
        Configs.dailyGoalLastResetMs = now;
        Configs.dailyProgress = progress;
        Configs.dailyBlocksMined = progress;
        Configs.personalRecordDailyBlocks = Math.max(Configs.personalRecordDailyBlocks, Configs.dailyBlocksMined);
        GoalNotificationManager.clear();
        Configs.saveToFile();
    }

    public static void setActiveProjectProgress(long value)
    {
        ProjectEntry activeProject = Configs.getActiveProject();
        if (activeProject != null)
        {
            activeProject.progress = Math.max(0L, value);
            GoalNotificationManager.clear();
            Configs.saveToFile();
        }
    }

    public static String getCurrentWorldId()
    {
        return currentWorldId != null ? currentWorldId : WorldSessionContext.getCurrentWorldId();
    }

    public static void onBpsSmoothingChanged()
    {
        lastBpsSmoothing = Configs.getBpsSmoothingMode();
        trimMetricWindow();
        rollingBlocksPerSecond = calculateRollingBps(lastBpsSmoothing);
        lastBpsUpdateTick = metricTickIndex;
    }

    public static Map<String, Long> getSortedBreakdown(SessionData session)
    {
        return session.blockBreakdown.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, java.util.LinkedHashMap::new));
    }

    private static Map<String, Long> buildDevSessionBreakdown(long totalBlocks)
    {
        String[] ids = {
                "minecraft:deepslate",
                "minecraft:tuff",
                "minecraft:stone",
                "minecraft:gravel",
                "minecraft:diorite",
                "minecraft:andesite",
                "minecraft:deepslate_redstone_ore",
                "minecraft:deepslate_diamond_ore"
        };
        int[] weights = { 355, 210, 170, 95, 70, 62, 28, 10 };
        Map<String, Long> breakdown = new LinkedHashMap<>();
        long assigned = 0L;
        for (int i = 0; i < ids.length; i++)
        {
            long amount = i == ids.length - 1 ? totalBlocks - assigned : (totalBlocks * weights[i]) / 1000L;
            assigned += amount;
            if (amount > 0L)
            {
                breakdown.put(ids[i], amount);
            }
        }
        return breakdown;
    }

    private static void resetDailyProgressIfNeeded()
    {
        resetPeriodStatsIfNeeded(System.currentTimeMillis());
    }
    private static void resetPeriodStatsIfNeeded(long now)
    {
        if (now >= lastPeriodStatsCheckMs && now - lastPeriodStatsCheckMs < PERIOD_STATS_CHECK_INTERVAL_MS)
        {
            return;
        }
        lastPeriodStatsCheckMs = now;

        boolean changed = false;
        boolean resetPeriod = false;
        DailyProgressPolicy.Result dailyResult = DailyProgressPolicy.evaluate(
                Configs.dailyBlocksMined,
                Configs.dailyProgress,
                Configs.dailyBlocksDate,
                Configs.dailyGoalLastResetMs,
                now);
        if (dailyResult.changed())
        {
            long previousBlocks = Configs.dailyBlocksMined;
            String previousKey = Configs.dailyBlocksDate == null ? "" : Configs.dailyBlocksDate;
            long previousResetMs = Configs.dailyGoalLastResetMs;
            if (dailyResult.reset())
            {
                Configs.personalRecordDailyBlocks = Math.max(Configs.personalRecordDailyBlocks, previousBlocks);
                GoalNotificationManager.clear();
            }
            Configs.dailyBlocksMined = dailyResult.blocks();
            Configs.dailyProgress = dailyResult.progress();
            Configs.dailyBlocksDate = dailyResult.periodKey();
            Configs.dailyGoalLastResetMs = dailyResult.lastResetAtMs();
            changed = true;
            resetPeriod = dailyResult.reset();
            MMM.LOGGER.info(
                    "[MMM_PERIOD] daily-state-change context=mining_tick previousBlocks={} newBlocks={} previousKey={} newKey={} previousResetAt={} nextResetAt={} reason={}",
                    previousBlocks,
                    Configs.dailyBlocksMined,
                    previousKey,
                    Configs.dailyBlocksDate,
                    formatPeriodTimestamp(previousResetMs),
                    formatPeriodTimestamp(dailyResult.nextResetAtMs()),
                    dailyResult.reason());
        }
        WeeklyProgressPolicy.Result weeklyResult = WeeklyProgressPolicy.evaluate(
                Configs.weeklyBlocksMined,
                Configs.weeklyBlocksWeek,
                Configs.weeklyLastResetMs,
                now);
        if (weeklyResult.changed())
        {
            long previousBlocks = Configs.weeklyBlocksMined;
            String previousKey = Configs.weeklyBlocksWeek == null ? "" : Configs.weeklyBlocksWeek;
            long previousResetMs = Configs.weeklyLastResetMs;
            if (weeklyResult.reset())
            {
                Configs.personalRecordWeeklyBlocks = Math.max(Configs.personalRecordWeeklyBlocks, previousBlocks);
            }
            Configs.weeklyBlocksMined = weeklyResult.blocks();
            Configs.weeklyBlocksWeek = weeklyResult.periodKey();
            Configs.weeklyLastResetMs = weeklyResult.lastResetAtMs();
            changed = true;
            resetPeriod = resetPeriod || weeklyResult.reset();
            MMM.LOGGER.info(
                    "[MMM_PERIOD] weekly-state-change context=mining_tick previousBlocks={} newBlocks={} previousKey={} newKey={} previousResetAt={} nextResetAt={} reason={}",
                    previousBlocks,
                    Configs.weeklyBlocksMined,
                    previousKey,
                    Configs.weeklyBlocksWeek,
                    formatPeriodTimestamp(previousResetMs),
                    formatPeriodTimestamp(weeklyResult.nextResetAtMs()),
                    weeklyResult.reason());
        }

        long calendarDailyBlocks = MiningCalendarStore.currentDailyBlocks(now);
        if (PeriodKeys.isCurrentDailyKey(Configs.dailyBlocksDate, now)
                && calendarDailyBlocks > Math.max(Configs.dailyBlocksMined, Configs.dailyProgress))
        {
            long previousBlocks = Math.max(Configs.dailyBlocksMined, Configs.dailyProgress);
            Configs.dailyBlocksMined = calendarDailyBlocks;
            Configs.dailyProgress = calendarDailyBlocks;
            Configs.personalRecordDailyBlocks = Math.max(Configs.personalRecordDailyBlocks, calendarDailyBlocks);
            changed = true;
            MMM.LOGGER.info(
                    "[MMM_PERIOD] recovered daily progress from mining calendar previousBlocks={} recoveredBlocks={} periodKey={}",
                    previousBlocks,
                    calendarDailyBlocks,
                    Configs.dailyBlocksDate);
        }

        long calendarWeeklyBlocks = MiningCalendarStore.currentWeeklyBlocks(now);
        if (PeriodKeys.isCurrentWeeklyKey(Configs.weeklyBlocksWeek, now)
                && calendarWeeklyBlocks > Configs.weeklyBlocksMined)
        {
            long previousBlocks = Configs.weeklyBlocksMined;
            Configs.weeklyBlocksMined = calendarWeeklyBlocks;
            Configs.personalRecordWeeklyBlocks = Math.max(Configs.personalRecordWeeklyBlocks, calendarWeeklyBlocks);
            changed = true;
            MMM.LOGGER.info(
                    "[MMM_PERIOD] recovered weekly progress from mining calendar previousBlocks={} recoveredBlocks={} periodKey={}",
                    previousBlocks,
                    calendarWeeklyBlocks,
                    Configs.weeklyBlocksWeek);
        }

        if (changed)
        {
            Configs.saveToFile();
            if (resetPeriod)
            {
                CloudSyncManager.requestScheduledSync("mining records period reset");
                DigsSyncManager.requestScheduledSync("mining records period reset");
            }
        }
    }

    private static String formatPeriodTimestamp(long timestampMs)
    {
        return timestampMs <= 0L ? "never" : Instant.ofEpochMilli(timestampMs).toString();
    }

    private static void recordPeriodBlocksMined(long amount, long now)
    {
        if (amount <= 0L)
        {
            return;
        }

        resetPeriodStatsIfNeeded(now);
        Configs.dailyBlocksMined = Math.max(0L, Configs.dailyBlocksMined) + amount;
        Configs.dailyProgress = Configs.dailyBlocksMined;
        Configs.weeklyBlocksMined = Math.max(0L, Configs.weeklyBlocksMined) + amount;
        Configs.personalRecordDailyBlocks = Math.max(Configs.personalRecordDailyBlocks, Configs.dailyBlocksMined);
        Configs.personalRecordWeeklyBlocks = Math.max(Configs.personalRecordWeeklyBlocks, Configs.weeklyBlocksMined);
    }

    private static void recordFastest100kIfReached(long now)
    {
        if (sessionActive == false || sessionPaused || session100kRecorded || currentSession.totalBlocks < FASTEST_100K_TARGET)
        {
            return;
        }

        long durationMs = Math.max(1L, getActiveElapsedMs(now));
        session100kRecorded = true;
        updateFastest100kRecord(durationMs, currentSession.startTimeMs, now);
    }

    private static void recordFastest100kWindow(long now)
    {
        FASTEST_100K_EVENT_TIMES.enqueue(now);
        while (FASTEST_100K_EVENT_TIMES.size() > FASTEST_100K_TARGET)
        {
            FASTEST_100K_EVENT_TIMES.dequeueLong();
        }

        if (FASTEST_100K_EVENT_TIMES.size() < FASTEST_100K_TARGET)
        {
            return;
        }

        long startedAt = FASTEST_100K_EVENT_TIMES.firstLong();
        updateFastest100kRecord(Math.max(1L, now - startedAt), startedAt, now);
    }

    private static void updateFastest100kRecord(long durationMs, long startedAtMs, long finishedAtMs)
    {
        if (durationMs <= 0L)
        {
            return;
        }

        if (Configs.fastest100kMs <= 0L || durationMs < Configs.fastest100kMs)
        {
            Configs.fastest100kMs = durationMs;
            Configs.fastest100kStartedAtMs = Math.max(0L, startedAtMs);
            Configs.fastest100kFinishedAtMs = Math.max(0L, finishedAtMs);
            Configs.saveToFile();
            CloudSyncManager.syncHeartbeat();
        }
    }

    private static void recordSuccessfulHarvestForRollingMetrics()
    {
        if (sessionActive && !sessionPaused)
        {
            currentTickBpsBlocks++;
            currentTickBphBlocks++;
        }
    }

    private static void updateRollingMetrics(boolean hasMiningContext)
    {
        if (hasMiningContext == false)
        {
            resetRollingMetrics();
            return;
        }

        if (sessionActive == false)
        {
            resetRollingMetrics();
            return;
        }

        if (sessionPaused)
        {
            return;
        }

        Configs.BpsSmoothing mode = Configs.getBpsSmoothingMode();
        if (lastBpsSmoothing != mode)
        {
            lastBpsSmoothing = mode;
            trimMetricWindow();
            rollingBlocksPerSecond = calculateRollingBps(mode);
            lastBpsUpdateTick = metricTickIndex;
        }

        metricTickIndex++;
        METRIC_TICK_COUNTS.addTick(Math.max(currentTickBpsBlocks, currentTickBphBlocks));
        currentTickBpsBlocks = 0;
        currentTickBphBlocks = 0;
        trimMetricWindow();

        if (metricTickIndex - lastBpsUpdateTick >= BPS_UPDATE_INTERVAL_TICKS)
        {
            rollingBlocksPerSecond = calculateRollingBps(mode);
            lastBpsUpdateTick = metricTickIndex;
        }

        if (metricTickIndex - lastBphUpdateTick >= BPS_UPDATE_INTERVAL_TICKS)
        {
            rollingBlocksPerHour = calculateSessionBph();
            lastBphUpdateTick = metricTickIndex;
        }

        sessionActiveTicks++;

        updateDisplayedRollingMetrics();
    }

    public static void resetRollingMetrics()
    {
        METRIC_TICK_COUNTS.clear();
        metricTickIndex = 0L;
        lastBpsUpdateTick = 0L;
        lastBphUpdateTick = 0L;
        sessionActiveTicks = 0L;
        currentTickBpsBlocks = 0;
        currentTickBphBlocks = 0;
        rollingBlocksPerSecond = 0D;
        rollingBlocksPerHour = 0D;
        displayedBlocksPerSecond = 0D;
        displayedBlocksPerHour = 0D;
        lastBpsSmoothing = Configs.getBpsSmoothingMode();
    }

    private static void updateDisplayedRollingMetrics()
    {
        displayedBlocksPerSecond = Math.max(0D, Math.min(20D, rollingBlocksPerSecond));
        displayedBlocksPerHour = Math.max(0D, Math.min(SessionData.MAX_BLOCKS_PER_HOUR, MmmTimerState.getEstimatedBlocksPerHour()));
    }


    private static void trimMetricWindow()
    {
        // RollingMiningMetrics is permanently capped to the largest metric window.
    }

    private static void freezeRollingMetrics()
    {
        currentTickBpsBlocks = 0;
        currentTickBphBlocks = 0;
        rollingBlocksPerSecond = 0D;
        rollingBlocksPerHour = 0D;
        displayedBlocksPerSecond = 0D;
        displayedBlocksPerHour = 0D;
    }

    private static double calculateRollingBps(Configs.BpsSmoothing mode)
    {
        int maxTicks = Math.max(1, Math.min(mode.getWindowTicks(), BPS_WINDOW_TICKS));
        int preferredTicks = Math.max(1, Math.min(maxTicks, mode.getPreferredMinimumTicks()));
        int availableTicks = METRIC_TICK_COUNTS.size();
        int targetTicks = availableTicks < preferredTicks ? availableTicks : Math.min(maxTicks, availableTicks);
        int ticksUsed = targetTicks;
        long validBlocks = METRIC_TICK_COUNTS.sumLatest(targetTicks);

        if (ticksUsed <= 0 || validBlocks <= 0)
        {
            return 0D;
        }

        double seconds = ticksUsed / (double) TICKS_PER_SECOND;
        if (seconds <= 0D)
        {
            return 0D;
        }

        return Math.max(0D, Math.min(20D, validBlocks / seconds));
    }

    private static double calculateRollingBph()
    {
        int ticksUsed = METRIC_TICK_COUNTS.size();
        long validBlocks = METRIC_TICK_COUNTS.total();

        if (ticksUsed <= 0 || validBlocks <= 0)
        {
            return 0D;
        }

        return Math.min(SessionData.MAX_BLOCKS_PER_HOUR, Math.max(0D, validBlocks * (double) BPH_WINDOW_TICKS / ticksUsed));
    }

    private static double calculateSessionBph()
    {
        return calculateRollingBph();
    }

    private static void pruneOldEvents(long now)
    {
        long cutoff = now - ONE_HOUR_MS;
        while (MINE_EVENTS.isEmpty() == false && MINE_EVENTS.firstLong() < cutoff)
        {
            MINE_EVENTS.dequeueLong();
        }
    }

    private static Configs.WorldStatsEntry touchCurrentWorldStats(long now)
    {
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        Configs.WorldStatsEntry entry = Configs.getOrCreateWorldStats(
                worldInfo.id(),
                worldInfo.displayName(),
                worldInfo.kind(),
                worldInfo.host());
        entry.lastSeenAt = now;
        return entry;
    }

    private static void recordCurrentWorldBlockBreakdown(Configs.WorldStatsEntry worldStats, Block block, long now)
    {
        if (worldStats == null || block == null)
        {
            return;
        }

        String key = BlockBreakdownCatalog.blockId(block);
        if (BlockBreakdownCatalog.isValid(key) == false)
        {
            return;
        }

        if (worldStats.blockBreakdown == null)
        {
            worldStats.blockBreakdown = new LinkedHashMap<>();
        }
        worldStats.blockBreakdown.merge(key, 1L, Long::sum);
        if (Configs.BLOCK_BREAKDOWN_SOURCE_MINECRAFT_STATS.equals(worldStats.blockBreakdownSource) == false)
        {
            worldStats.blockBreakdownSource = Configs.BLOCK_BREAKDOWN_SOURCE_LOCAL_OBSERVED;
        }
        worldStats.blockBreakdownUpdatedAtMs = now;
    }

    private static void handleAutoSessionOnValidMine(long now)
    {
        if (lastValidBlockMineMs == 0L || now - lastValidBlockMineMs > AUTO_MINING_GAP_MS)
        {
            autoMiningStreakStartMs = now;
        }
        lastValidBlockMineMs = now;

        if (autoMiningStreakStartMs <= 0L || now - autoMiningStreakStartMs < AUTO_MINING_REQUIRED_MS)
        {
            return;
        }

        if (sessionActive == false)
        {
            startNewSession();
            lastValidBlockMineMs = now;
            autoMiningStreakStartMs = 0L;
            return;
        }

        if (sessionPaused && sessionAutoPaused)
        {
            resumeAutoPausedSession(now);
            lastValidBlockMineMs = now;
            autoMiningStreakStartMs = 0L;
        }
    }

    private static void maybeAutoPauseSession(long now)
    {
        if (sessionActive == false || sessionPaused || lastValidBlockMineMs <= 0L)
        {
            return;
        }

        if (now - lastValidBlockMineMs < AUTO_PAUSE_IDLE_MS)
        {
            return;
        }

        pausedAtMs = now;
        sessionPaused = true;
        sessionAutoPaused = true;
        sessionMenuPaused = false;
        autoMiningStreakStartMs = 0L;
        freezeRollingMetrics();
        CloudSyncManager.syncHeartbeat();
        checkpointActiveSession(now, true);
        MmmDebugLogger.info(
                "miningstats-auto-pause",
                SESSION_DEBUG_LOG_INTERVAL_MS,
                "[MMM_DEBUG] session-auto-paused idleMs={}",
                now - lastValidBlockMineMs);
    }

    private static void resumeAutoPausedSession(long now)
    {
        pausedAccumulatedMs += Math.max(0L, now - pausedAtMs);
        pausedAtMs = 0L;
        sessionPaused = false;
        sessionAutoPaused = false;
        sessionMenuPaused = false;
        rollingBlocksPerHour = calculateSessionBph();
        rollingBlocksPerSecond = calculateRollingBps(lastBpsSmoothing);
        updateDisplayedRollingMetrics();
        CloudSyncManager.syncHeartbeat();
        checkpointActiveSession(now, true);
        MmmDebugLogger.info(
                "miningstats-auto-resume",
                SESSION_DEBUG_LOG_INTERVAL_MS,
                "[MMM_DEBUG] session-auto-resumed");
    }

    private static Configs.WorldStatsEntry getCurrentWorldStats()
    {
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        for (Configs.WorldStatsEntry entry : Configs.WORLD_STATS)
        {
            if (worldInfo.id().equals(entry.worldId))
            {
                return entry;
            }
        }

        return null;
    }

    private static boolean sourceMatchesCurrentWorld(WorldSessionContext.WorldInfo worldInfo, String scoreboardSourceName)
    {
        if (worldInfo == null)
        {
            return false;
        }

        String currentKey = ScoreboardSourceResolver.sourceKey(worldInfo.displayName(), worldInfo);
        WorldSessionContext.WorldInfo scoreboardInfo = new WorldSessionContext.WorldInfo(
                worldInfo.id(),
                scoreboardSourceName == null || scoreboardSourceName.isBlank() ? worldInfo.displayName() : scoreboardSourceName,
                worldInfo.kind(),
                worldInfo.host()
        );
        String scoreboardKey = ScoreboardSourceResolver.sourceKey(scoreboardInfo.displayName(), scoreboardInfo);
        return currentKey.equalsIgnoreCase(scoreboardKey);
    }

    private static String getCurrentDimensionId()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null)
        {
            return "unknown";
        }

        return client.world.getRegistryKey().getValue().toString();
    }

    private static void debugAttribution(String reason, long beforeSourceTotal, long afterSourceTotal, long delta)
    {
        if (MmmDebugLogger.shouldLog("miningstats.source-update." + reason, SOURCE_UPDATE_DEBUG_LOG_INTERVAL_MS) == false)
        {
            return;
        }

        WorldSessionContext.WorldInfo world = WorldSessionContext.getCurrentWorldInfo();
        MMM.LOGGER.info(
                "[MMM_DEBUG] source-update reason={} worldName={} sessionActive={} sessionBlocks={} sourceBefore={} sourceAfter={} delta={} lifetime={}",
                reason,
                world.displayName(),
                sessionActive,
                currentSession.totalBlocks,
                beforeSourceTotal,
                afterSourceTotal,
                delta,
                Configs.totalBlocksMined
        );
    }

    private static boolean shouldPersistSession(SessionData session)
    {
        return SessionHistory.isQualifyingSession(session);
    }

    private static void checkpointActiveSession(long now, boolean force)
    {
        if (sessionActive == false
                || (!force && now - lastSessionCheckpointMs < SESSION_CHECKPOINT_INTERVAL_MS))
        {
            return;
        }

        long effectiveNow = sessionPaused && pausedAtMs > 0L ? pausedAtMs : now;
        currentSession.endTimeMs = Math.max(
                currentSession.startTimeMs,
                effectiveNow - Math.max(0L, pausedAccumulatedMs));
        ActiveSessionCheckpoint.save(
                currentWorldId,
                new ActiveSessionCheckpoint.State(
                        currentSession,
                        sessionPaused,
                        sessionAutoPaused,
                        sessionMenuPaused,
                        pausedAtMs,
                        pausedAccumulatedMs,
                        sessionStartTotalMined,
                        pausedSessionMinedOffset,
                        lastScoreboardSessionUpdateActiveElapsedMs,
                        session100kRecorded,
                        now));
        lastSessionCheckpointMs = now;
    }

    private static boolean restoreActiveSession(long now)
    {
        ActiveSessionCheckpoint.State checkpoint = ActiveSessionCheckpoint.load(currentWorldId);
        if (checkpoint == null || checkpoint.session() == null)
        {
            return false;
        }

        currentSession = checkpoint.session();
        if (currentSession.repairInflatedTotalFromBreakdown())
        {
            MMM.LOGGER.warn(
                    "[MMM] Repaired an inflated active session total from its block breakdown: {} blocks",
                    currentSession.totalBlocks);
        }
        long savedAtMs = checkpoint.savedAtMs() > 0L && checkpoint.savedAtMs() <= now
                ? checkpoint.savedAtMs()
                : now;
        pausedAccumulatedMs = Math.max(0L, checkpoint.pausedAccumulatedMs());
        sessionPaused = checkpoint.paused();
        sessionAutoPaused = checkpoint.autoPaused();
        sessionMenuPaused = sessionPaused && checkpoint.menuPaused();
        if (sessionPaused)
        {
            long pauseStartedAtMs = checkpoint.pausedAtMs() > 0L && checkpoint.pausedAtMs() <= now
                    ? checkpoint.pausedAtMs()
                    : savedAtMs;
            pausedAccumulatedMs += Math.max(0L, now - pauseStartedAtMs);
            pausedAtMs = now;
        }
        else
        {
            pausedAccumulatedMs += Math.max(0L, now - savedAtMs);
            pausedAtMs = 0L;
        }

        sessionStartTotalMined = Math.max(0L, checkpoint.sessionStartTotalMined());
        pausedSessionMinedOffset = Math.max(0L, checkpoint.pausedSessionMinedOffset());
        lastScoreboardSessionUpdateActiveElapsedMs = Math.max(
                0L,
                checkpoint.lastScoreboardSessionUpdateActiveElapsedMs());
        session100kRecorded = checkpoint.session100kRecorded();
        sessionActive = true;
        currentSession.endTimeMs = Math.max(
                currentSession.startTimeMs,
                (sessionPaused ? pausedAtMs : now) - pausedAccumulatedMs);
        lastSessionCheckpointMs = now;
        autoMiningStreakStartMs = 0L;
        lastValidBlockMineMs = 0L;
        MINE_EVENTS.clear();
        resetRollingMetrics();
        MiningSpeedTracker.resetSession();
        MMM.LOGGER.info(
                "[MMM] Restored active session after restart world={} blocks={} paused={} savedAt={}",
                WorldSessionContext.getCurrentWorldName(),
                currentSession.totalBlocks,
                sessionPaused,
                Instant.ofEpochMilli(savedAtMs));
        return true;
    }
    public record GoalProgress(String label, boolean enabled, long current, long target)
    {
        public double getPercentValue()
        {
            return target <= 0 ? 0.0D : Math.max(0.0D, (current * 100.0D) / target);
        }

        public int getPercent()
        {
            return (int) getPercentValue();
        }
    }

    public record ProjectProgress(String name, long blocksMined) {}

}

