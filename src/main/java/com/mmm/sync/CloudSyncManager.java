package com.mmm.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.Reference;
import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.config.Configs.ProjectEntry;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.storage.MiningCalendarStore;
import com.mmm.storage.WorldIdentity;
import com.mmm.storage.WorldSessionContext;
import com.mmm.tracker.MiningStats;
import com.mmm.tracker.SourceTotalPolicy;
import com.mmm.util.MmmDebugLogger;
import com.mmm.util.PeriodKeys;
import com.mmm.util.UiFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;

public final class CloudSyncManager
{
    private static final String LOG_PREFIX = "[MMM_SYNC]";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final long SOURCE_SCOREBOARD_SCAN_INTERVAL_MS = 3_000L;
    private static final long HUD_FAILURE_GRACE_MS = 12_000L;
    private static final long HUD_HEALTH_STALE_MS = 90_000L;
    private static final long SYNC_UNAVAILABLE_LOG_INTERVAL_MS = 30_000L;
    private static final long MIN_LIVE_SYNC_ATTEMPT_INTERVAL_MS = 15_000L;
    private static final int MAX_SAVED_SESSIONS_PER_PAYLOAD = 25;

    private static long lastHeartbeatMs;
    private static long nextTickCheckMs;
    private static long lastLiveBlockSyncMs;
    private static long lastSourceScoreboardScanMs;
    private static volatile SyncStatus syncStatus = SyncStatus.CONNECTED;
    private static volatile String syncStatusDetail = "";
    private static volatile long lastHealthySignalMs;
    private static volatile long lastFailureSignalMs;
    private static volatile long scheduledRetryAtMs;
    private static volatile String lastRetryDetail = "";
    private static SourceLeaderboardSnapshot latestLeaderboardSnapshot;
    private static String lastQueuedLiveFingerprint;
    private static String lastSuccessfulLiveFingerprint;
    private static String lastSuccessfulLeaderboardFingerprint;
    private static volatile String lastPayloadSourceKey = "";
    private static volatile String lastPayloadSourceName = "";
    private static volatile boolean currentContextPayloadPrepared;
    private static volatile long lastSyncUnavailableLogMs;
    private static volatile String lastSyncUnavailableReason = "";

    private CloudSyncManager()
    {
    }

    public static void onClientTick(long now)
    {
        if (now < nextTickCheckMs)
        {
            return;
        }
        nextTickCheckMs = now + 1_000L;

        if (canSync() == false || hasLiveContext() == false)
        {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        refreshLeaderboardSnapshot(client, now, false);

        if (isSyncCadenceDue(now))
        {
            syncHeartbeat();
        }
        if (latestLeaderboardSnapshot != null && syncStatus != SyncStatus.SYNCING && syncStatus != SyncStatus.SYNCED)
        {
            syncStatus = SyncStatus.CONNECTED;
            syncStatusDetail = "Leaderboard detected";
            touchHealthy();
        }

    }

    public static void syncHeartbeat()
    {
        requestScheduledSync("24-hour heartbeat");
    }

    public static void requestScheduledSync(String reason)
    {
        if (canSync() == false || hasLiveContext() == false)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (isSyncCadenceDue(now) == false)
        {
            syncStatusDetail = "Next sync in " + getNextSyncLabel();
            return;
        }

        refreshLeaderboardSnapshot(MinecraftClient.getInstance(), now, true);
        lastHeartbeatMs = now;
        lastLiveBlockSyncMs = now;
        SessionData liveSession = MiningStats.isSessionActive() ? MiningStats.getCurrentSession() : null;
        queueLivePayload(buildPayload(liveSession, liveSession == null ? null : getCurrentSessionStatus()), true);
    }
    public static void syncNow(String reason)
    {
        if (canSync() == false || hasLiveContext() == false)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (isSyncCadenceDue(now) == false)
        {
            syncStatusDetail = "Next sync in " + getNextSyncLabel();
            return;
        }
        refreshLeaderboardSnapshot(MinecraftClient.getInstance(), now, true);
        lastHeartbeatMs = now;
        lastLiveBlockSyncMs = now;
        SessionData liveSession = MiningStats.isSessionActive() ? MiningStats.getCurrentSession() : null;
        queueLivePayload(buildPayload(liveSession, liveSession == null ? null : getCurrentSessionStatus()), true);
        SyncQueueManager.forceFlush(reason == null || reason.isBlank() ? "manual sync" : reason);
    }

    public static void syncFinishedSession(SessionData session)
    {
        if (canSync() == false || SessionHistory.isQualifyingSession(session) == false)
        {
            return;
        }

        if (isSyncCadenceDue(System.currentTimeMillis()) == false)
        {
            return;
        }

        String sessionKey = sessionKey(session);
        JsonObject payload = buildPayload(session, "ended");
        SyncQueueManager.enqueueCloudFinishedSession(sessionKey, payload);
    }

    public static void onBlockMined(long now)
    {
        if (canSync() == false || isSyncCadenceDue(now) == false)
        {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        String sourceName = ScoreboardSourceResolver.displayName(worldInfo.displayName(), worldInfo);
        boolean autoSyncEnabled = Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue();
        boolean hasEndpoint = Configs.cloudSyncEndpoint != null && Configs.cloudSyncEndpoint.isBlank() == false;
        boolean hasContext = client != null && client.player != null && client.world != null;
        boolean loggedIn = hasContext && client.getSession() != null && client.getSession().getUsername().isBlank() == false;
        long totalMined = MiningStats.getTotalMined();
        long sessionMined = MiningStats.getSessionBlocksMined();
        boolean hasSyncSecret = Configs.cloudSyncSecret != null && Configs.cloudSyncSecret.isBlank() == false;
        boolean hasLinkedIdentity = Configs.websiteLinkedMinecraftUuid != null && Configs.websiteLinkedMinecraftUuid.isBlank() == false;
        boolean hasSessionToken = hasSessionToken(client);

        if (MmmDebugLogger.shouldLog("cloud-sync-check", 5_000L))
        {
            MMM.LOGGER.info(
                    "{} sync-check autoSyncEnabled={} loggedIn={} hasWorldContext={} hasEndpoint={} hasSyncSecret={} hasLinkedIdentity={} hasSessionToken={} sourceName={} totalMined={} sessionMined={}",
                    LOG_PREFIX,
                    autoSyncEnabled,
                    loggedIn,
                    hasContext,
                    hasEndpoint,
                    hasSyncSecret,
                    hasLinkedIdentity,
                    hasSessionToken,
                    sourceName,
                    totalMined,
                    sessionMined
            );
        }

        if (hasLiveContext() == false)
        {
            return;
        }

        queueCurrentLivePayloadIfDue(now);
    }

    static void onQueued(SyncItemType type, JsonObject payload)
    {
        if (type != SyncItemType.CLOUD_LIVE_STATE && type != SyncItemType.CLOUD_FINISHED_SESSION)
        {
            return;
        }

        syncStatus = SyncStatus.QUEUED;
        syncStatusDetail = type == SyncItemType.CLOUD_FINISHED_SESSION ? "Session queued for sync." : "Live sync queued.";
        scheduledRetryAtMs = 0L;
        lastRetryDetail = "";

        if (type == SyncItemType.CLOUD_LIVE_STATE)
        {
            lastQueuedLiveFingerprint = livePayloadFingerprint(payload);
        }
    }

    static void onQueuePreparing(SyncItemType type)
    {
        if (type == SyncItemType.CLOUD_LIVE_STATE || type == SyncItemType.CLOUD_FINISHED_SESSION)
        {
            syncStatus = SyncStatus.SYNCING;
            syncStatusDetail = "Preparing the saved sync payload.";
            scheduledRetryAtMs = 0L;
        }
    }

    static void onQueueUploading(SyncItemType type)
    {
        if (type == SyncItemType.CLOUD_LIVE_STATE || type == SyncItemType.CLOUD_FINISHED_SESSION)
        {
            syncStatus = SyncStatus.SYNCING;
            syncStatusDetail = "Uploading to MMM.";
        }
    }

    static void onQueueWaitingForResponse(SyncItemType type)
    {
        if (type == SyncItemType.CLOUD_LIVE_STATE || type == SyncItemType.CLOUD_FINISHED_SESSION)
        {
            syncStatus = SyncStatus.SYNCING;
            syncStatusDetail = "Waiting for the MMM website response.";
        }
    }
    static void onQueueSuccess(SyncItemType type, JsonObject payload, String responseBody)
    {
        if (type != SyncItemType.CLOUD_LIVE_STATE && type != SyncItemType.CLOUD_FINISHED_SESSION)
        {
            return;
        }

        boolean skippedByCadence = responseBoolean(responseBody, "sync_skipped");
        boolean sourceSyncAccepted = responseBoolean(responseBody, "source_sync_accepted");
        syncStatus = skippedByCadence || sourceSyncAccepted == false
                ? SyncStatus.CONNECTED
                : SyncStatus.SYNCED;
        scheduledRetryAtMs = 0L;
        lastRetryDetail = "";
        syncStatusDetail = sourceSyncResponseDetail(responseBody, skippedByCadence, sourceSyncAccepted,
                type == SyncItemType.CLOUD_FINISHED_SESSION);
        touchHealthy();
        if (skippedByCadence == false)
        {
            markSyncedSessions(payload, responseBody);
            if (responseBoolean(responseBody, "daily_mining_synced"))
            {
                MiningCalendarStore.markPayloadSynced(payload);
            }
        }
        applySuccessfulSyncResponse(payload, responseBody);

        if (type == SyncItemType.CLOUD_LIVE_STATE && skippedByCadence == false)
        {
            lastSuccessfulLiveFingerprint = livePayloadFingerprint(payload);
            lastQueuedLiveFingerprint = lastSuccessfulLiveFingerprint;
        }

        if (sourceSyncAccepted && latestLeaderboardSnapshot != null)
        {
            String sentLeaderboardFingerprint = leaderboardFingerprint(payload);
            if (sentLeaderboardFingerprint.isBlank() == false)
            {
                lastSuccessfulLeaderboardFingerprint = sentLeaderboardFingerprint;
            }
        }
    }

    private static boolean responseBoolean(String responseBody, String key)
    {
        if (responseBody == null || responseBody.isBlank() || key == null || key.isBlank())
        {
            return false;
        }

        try
        {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            return root.has(key) && root.get(key).isJsonPrimitive() && root.get(key).getAsBoolean();
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    static String sourceSyncResponseDetail(String responseBody, boolean skippedByCadence,
                                           boolean sourceSyncAccepted, boolean finishedSession)
    {
        JsonObject root = responseObject(responseBody);
        String reason = responseString(root, "reason");
        String message = responseString(root, "message");
        if (skippedByCadence && reason.equals("24_hour_cooldown"))
        {
            long nextSyncAtMs = responseTimestamp(root, "next_sync_at");
            String wait = nextSyncAtMs > System.currentTimeMillis()
                    ? UiFormat.formatDuration(Math.max(1L, (nextSyncAtMs - System.currentTimeMillis() + 999L) / 1000L))
                    : "a moment";
            return "This source already synced. Its next full sync is available in " + wait + ".";
        }
        if (sourceSyncAccepted)
        {
            JsonObject sourceSync = root == null || root.has("source_sync") == false || root.get("source_sync").isJsonObject() == false
                    ? null : root.getAsJsonObject("source_sync");
            String objective = responseString(sourceSync, "objective_title");
            long players = responseLong(sourceSync, "player_count");
            long total = responseLong(sourceSync, "total_blocks");
            if (objective.isBlank() == false)
            {
                String counts = players > 0L
                        ? " with " + players + " players" + (total > 0L ? " / " + UiFormat.formatCompact(total) + " blocks" : "")
                        : total > 0L ? " with " + UiFormat.formatCompact(total) + " blocks" : "";
                return "Accepted scoreboard " + objective + counts + ". Next sync in 24 hours.";
            }
            return finishedSession ? "Finished session delivered." : "Latest source scoreboard accepted. Next sync in 24 hours.";
        }
        if (reason.equals("no_mining_scoreboard_evidence"))
        {
            return "No valid mining scoreboard was sent. Choose one with Sync Scoreboard.";
        }
        if (reason.equals("validation_review_required"))
        {
            return "Personal data was saved. The source update is waiting for owner review.";
        }
        if (reason.equals("source_write_not_allowed"))
        {
            return "Personal data was saved, but this source scoreboard was not accepted.";
        }
        if (message.isBlank() == false)
        {
            return message.endsWith(".") ? message : message + ".";
        }
        return "Personal data was saved, but no source scoreboard was accepted.";
    }

    private static JsonObject responseObject(String responseBody)
    {
        if (responseBody == null || responseBody.isBlank())
        {
            return null;
        }
        try
        {
            return JsonParser.parseString(responseBody).getAsJsonObject();
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static String responseString(JsonObject root, String key)
    {
        if (root == null || key == null || root.has(key) == false || root.get(key).isJsonPrimitive() == false)
        {
            return "";
        }
        try
        {
            return root.get(key).getAsString().trim();
        }
        catch (Exception ignored)
        {
            return "";
        }
    }

    private static long responseLong(JsonObject root, String key)
    {
        if (root == null || key == null || root.has(key) == false || root.get(key).isJsonPrimitive() == false)
        {
            return 0L;
        }
        try
        {
            return Math.max(0L, root.get(key).getAsLong());
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }

    private static long responseTimestamp(JsonObject root, String key)
    {
        String value = responseString(root, key);
        if (value.isBlank())
        {
            return 0L;
        }
        try
        {
            return Instant.parse(value).toEpochMilli();
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }
    static void onQueueRetry(SyncItemType type, String detail, long nextRetryAtMs)
    {
        if (type != SyncItemType.CLOUD_LIVE_STATE && type != SyncItemType.CLOUD_FINISHED_SESSION)
        {
            return;
        }

        syncStatus = SyncStatus.FAILED;
        lastFailureSignalMs = System.currentTimeMillis();
        scheduledRetryAtMs = Math.max(0L, nextRetryAtMs);
        lastRetryDetail = detail == null ? "" : detail.trim();
        syncStatusDetail = buildRetryDetail(System.currentTimeMillis());
    }

    static void onQueueDropped(SyncItemType type, String detail)
    {
        if (type != SyncItemType.CLOUD_LIVE_STATE && type != SyncItemType.CLOUD_FINISHED_SESSION)
        {
            return;
        }

        syncStatus = SyncStatus.FAILED;
        lastFailureSignalMs = System.currentTimeMillis();
        scheduledRetryAtMs = 0L;
        lastRetryDetail = detail == null ? "" : detail.trim();
        syncStatusDetail = friendlyFailureReason(lastRetryDetail);
    }

    public static boolean isHudHealthy(long now)
    {
        if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() == false
                || Configs.cloudSyncEndpoint == null
                || Configs.cloudSyncEndpoint.isBlank()
                || WebsiteLinkManager.hasPersistedLink() == false
                || isCurrentPlayerMismatch())
        {
            return false;
        }

        PendingSyncQueue.Snapshot snapshot = SyncQueueManager.getSnapshot();
        int pending = snapshot.countFor(SyncItemType.CLOUD_LIVE_STATE) + snapshot.countFor(SyncItemType.CLOUD_FINISHED_SESSION);
        long recentHealthyMs = Math.max(lastHealthySignalMs, snapshot.lastSuccessfulSyncAtMs());

        if (snapshot.flushActive())
        {
            return true;
        }

        if (syncStatus == SyncStatus.FAILED)
        {
            return recentHealthyMs > 0L && now - recentHealthyMs <= HUD_FAILURE_GRACE_MS;
        }

        if (pending > 0 && recentHealthyMs > 0L && now - recentHealthyMs > HUD_FAILURE_GRACE_MS)
        {
            return false;
        }

        if (latestLeaderboardSnapshot != null
                && latestLeaderboardSnapshot.isValid()
                && recentHealthyMs > 0L
                && now - recentHealthyMs <= HUD_HEALTH_STALE_MS)
        {
            return true;
        }

        return recentHealthyMs > 0L && now - recentHealthyMs <= HUD_HEALTH_STALE_MS;
    }
    public static String getStatusLabel()
    {
        PendingSyncQueue.Snapshot snapshot = SyncQueueManager.getSnapshot();
        long now = System.currentTimeMillis();
        int pendingLinkClaims = snapshot.countFor(SyncItemType.WEBSITE_LINK_CLAIM);
        int pending = pendingSyncCount(snapshot);

        if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() == false)
        {
            return "Disabled";
        }
        if (Configs.cloudSyncEndpoint == null || Configs.cloudSyncEndpoint.isBlank())
        {
            return "Unavailable";
        }
        if (pendingLinkClaims > 0)
        {
            if (snapshot.flushActive())
            {
                return "Linking";
            }
            return snapshot.nextAttemptAtMs() > now ? "Link retrying" : "Link queued";
        }
        if (WebsiteLinkManager.hasPersistedLink() == false)
        {
            return "Not authenticated";
        }
        if (isCurrentPlayerMismatch())
        {
            return "Wrong account";
        }
        if (snapshot.flushActive() && pending > 0)
        {
            return "Syncing";
        }
        if (pending > 0)
        {
            if (syncStatus == SyncStatus.FAILED || snapshot.nextAttemptAtMs() > now)
            {
                return "Retrying";
            }
            return "Queued";
        }
        if (hasLiveContext() == false)
        {
            return "Waiting";
        }
        if (syncStatus == SyncStatus.FAILED)
        {
            return "Error";
        }

        long remainingMs = getNextSyncRemainingMs(now);
        if (remainingMs > 0L)
        {
            return "Cooldown";
        }
        return "Ready";
    }

    public static String getStatusDetail()
    {
        PendingSyncQueue.Snapshot snapshot = SyncQueueManager.getSnapshot();
        long now = System.currentTimeMillis();
        String readable = getReadableStatusDetail(snapshot, now);

        if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue() == false)
        {
            return readable;
        }

        List<String> parts = new ArrayList<>();
        parts.add(readable);
        parts.add("Q:" + snapshot.queueSize());
        parts.add(snapshot.flushActive() ? "flush=active" : "flush=idle");

        if (snapshot.lastSuccessfulSyncAtMs() > 0L)
        {
            long ageSeconds = Math.max(0L, (now - snapshot.lastSuccessfulSyncAtMs()) / 1000L);
            parts.add("lastOk=" + UiFormat.formatDuration(ageSeconds));
        }
        if (snapshot.nextAttemptAtMs() > 0L)
        {
            long waitSeconds = Math.max(0L, (snapshot.nextAttemptAtMs() - now + 999L) / 1000L);
            parts.add(waitSeconds <= 0L ? "next=now" : "next=" + UiFormat.formatDuration(waitSeconds));
        }

        return String.join(" | ", parts);
    }

    public static String getStatusSummary()
    {
        String label = getStatusLabel();
        return switch (label)
        {
            case "Disabled" -> "Sync off";
            case "Unavailable" -> "Sync unavailable - endpoint missing";
            case "Not authenticated" -> "Website link required";
            case "Wrong account" -> "Wrong linked Minecraft account";
            case "Waiting" -> "Join a world or server to sync";
            case "Linking" -> "Linking website account";
            case "Link queued" -> "Website link queued";
            case "Link retrying" -> "Website link retry in " + retryCountdownLabel();
            case "Syncing" -> "Syncing - " + compactStageDetail();
            case "Queued" -> "Sync queued - waiting to send";
            case "Retrying" -> "Retry in " + retryCountdownLabel() + " - " + trimTerminalPeriod(friendlyFailureReason(lastRetryDetail));
            case "Cooldown" -> "Cooldown - " + getNextSyncLabel() + " left";
            case "Error" -> "Sync error - " + trimTerminalPeriod(friendlyFailureReason(lastRetryDetail));
            default -> "Sync ready";
        };
    }

    private static String getReadableStatusDetail(PendingSyncQueue.Snapshot snapshot, long now)
    {
        if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() == false)
        {
            return "Website sync is turned off.";
        }
        if (Configs.cloudSyncEndpoint == null || Configs.cloudSyncEndpoint.isBlank())
        {
            return "The website sync endpoint is not configured.";
        }
        if (snapshot.countFor(SyncItemType.WEBSITE_LINK_CLAIM) > 0)
        {
            String detail = WebsiteLinkManager.getState().detail();
            return detail == null || detail.isBlank() ? "Claiming the website link code..." : detail;
        }
        if (WebsiteLinkManager.hasPersistedLink() == false)
        {
            return "Website link required. Generate a mod link code, then enter it in Website Link.";
        }
        if (isCurrentPlayerMismatch())
        {
            return "This Minecraft account does not match the account linked to MMM.";
        }

        int pending = pendingSyncCount(snapshot);
        if (snapshot.flushActive() && pending > 0)
        {
            return syncStatusDetail == null || syncStatusDetail.isBlank()
                    ? "Sending the current source to the MMM website."
                    : syncStatusDetail;
        }
        if (pending > 0)
        {
            long retryAtMs = Math.max(snapshot.nextAttemptAtMs(), scheduledRetryAtMs);
            if (retryAtMs > now || syncStatus == SyncStatus.FAILED)
            {
                return buildRetryDetail(now);
            }
            return "Sync queued. Waiting for the sender to start.";
        }
        if (hasLiveContext() == false)
        {
            return "Join a world or server before syncing.";
        }
        if (syncStatus == SyncStatus.FAILED)
        {
            return friendlyFailureReason(lastRetryDetail);
        }

        long remainingMs = getNextSyncRemainingMs(now);
        if (remainingMs > 0L)
        {
            String source = currentSourceDisplayName();
            String suffix = source.isBlank() ? "" : " for " + source;
            String selected = SyncScoreboardSelector.selectedObjectiveName();
            String scoreboard = selected.isBlank() ? "" : " Selected scoreboard: " + selected + ".";
            return "Cooldown: " + UiFormat.formatDuration(Math.max(1L, (remainingMs + 999L) / 1000L)) + " remaining" + suffix + "." + scoreboard;
        }
        if (lastSuccessfulSyncMs() <= 0L)
        {
            return "Ready to send this source for the first time.";
        }
        return "Ready to send this source again.";
    }

    private static int pendingSyncCount(PendingSyncQueue.Snapshot snapshot)
    {
        return snapshot.countFor(SyncItemType.CLOUD_LIVE_STATE)
                + snapshot.countFor(SyncItemType.CLOUD_FINISHED_SESSION)
                + snapshot.countFor(SyncItemType.PLAYER_TOTAL_DIGS)
                + snapshot.countFor(SyncItemType.WEBSITE_LINK_CLAIM);
    }

    private static String buildRetryDetail(long now)
    {
        long retryAtMs = Math.max(scheduledRetryAtMs, SyncQueueManager.getSnapshot().nextAttemptAtMs());
        String reason = friendlyFailureReason(lastRetryDetail);
        if (retryAtMs <= now)
        {
            return "Retry is ready. " + reason;
        }
        long waitSeconds = Math.max(1L, (retryAtMs - now + 999L) / 1000L);
        return "Retrying in " + UiFormat.formatDuration(waitSeconds) + ". " + reason;
    }

    private static String retryCountdownLabel()
    {
        long now = System.currentTimeMillis();
        long retryAtMs = Math.max(scheduledRetryAtMs, SyncQueueManager.getSnapshot().nextAttemptAtMs());
        if (retryAtMs <= now)
        {
            return "now";
        }
        return UiFormat.formatDuration(Math.max(1L, (retryAtMs - now + 999L) / 1000L));
    }

    private static String compactStageDetail()
    {
        String detail = syncStatusDetail == null ? "" : syncStatusDetail.trim();
        if (detail.equals("Preparing the saved sync payload."))
        {
            return "preparing data";
        }
        if (detail.equals("Uploading to MMM."))
        {
            return "uploading";
        }
        if (detail.equals("Waiting for the MMM website response."))
        {
            return "waiting for website";
        }
        return detail.isBlank() ? "sending" : trimTerminalPeriod(detail);
    }

    static String friendlyFailureReason(String detail)
    {
        String safe = detail == null ? "" : detail.replace('\n', ' ').replace('\r', ' ').trim();
        String lower = safe.toLowerCase(Locale.ROOT);
        if (safe.isBlank())
        {
            return "The website did not accept the sync.";
        }
        if (lower.contains("disabled by config"))
        {
            return "Website sync is turned off.";
        }
        if (lower.contains("no sync endpoint") || lower.contains("endpoint") && lower.contains("not configured"))
        {
            return "The website sync endpoint is missing.";
        }
        if (lower.contains("link mmmod") || lower.contains("website link") && (lower.contains("expired") || lower.contains("rejected") || lower.contains("required"))
                || lower.contains("unauthorized") || lower.contains("invalid token"))
        {
            return "Website link required. Generate a new mod link code.";
        }
        if (lower.contains("temporarily disabled") || lower.contains("maintenance") || lower.contains("service unavailable") || lower.equals("http 503"))
        {
            return "The MMM sync service is temporarily unavailable.";
        }
        if (lower.contains("too many requests") || lower.contains("rate limit") || lower.equals("http 429"))
        {
            return "The website asked MMM to wait before trying again.";
        }
        if (lower.contains("timed out") || lower.contains("timeout"))
        {
            return "The website did not respond in time.";
        }
        if (lower.contains("connection refused") || lower.contains("connectexception"))
        {
            return "The website sync service refused the connection.";
        }
        if (lower.contains("unknownhost") || lower.contains("name or service not known") || lower.contains("could not resolve"))
        {
            return "MMM could not find the website sync server.";
        }
        return safe.endsWith(".") ? safe : safe + ".";
    }

    private static String currentSourceDisplayName()
    {
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        if (worldInfo != null && worldInfo.displayName() != null && worldInfo.displayName().isBlank() == false)
        {
            return worldInfo.displayName().trim();
        }
        return lastPayloadSourceName == null ? "" : lastPayloadSourceName.trim();
    }

    private static String trimTerminalPeriod(String value)
    {
        String safe = value == null ? "" : value.trim();
        return safe.endsWith(".") ? safe.substring(0, safe.length() - 1) : safe;
    }
    public static void resetForDisconnect()
    {
        latestLeaderboardSnapshot = null;
        syncStatus = SyncStatus.CONNECTED;
        syncStatusDetail = "";
        lastHeartbeatMs = 0L;
        lastLiveBlockSyncMs = 0L;
        lastSourceScoreboardScanMs = 0L;
        lastQueuedLiveFingerprint = null;
        lastSuccessfulLiveFingerprint = null;
        lastSuccessfulLeaderboardFingerprint = null;
        lastFailureSignalMs = 0L;
        scheduledRetryAtMs = 0L;
        lastRetryDetail = "";
        lastPayloadSourceKey = "";
        lastPayloadSourceName = "";
        currentContextPayloadPrepared = false;
    }

    public static String getLastPayloadSourceKey()
    {
        return lastPayloadSourceKey;
    }

    public static String getLastPayloadSourceName()
    {
        return lastPayloadSourceName;
    }

    public static long getSyncIntervalMs()
    {
        return Configs.normalizeWebsiteSyncIntervalMs(Configs.websiteSyncIntervalMs);
    }

    public static long getNextSyncRemainingMs()
    {
        return getNextSyncRemainingMs(System.currentTimeMillis());
    }

    public static long getNextSyncRemainingMs(long now)
    {
        if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() == false
                || WebsiteLinkManager.hasPersistedLink() == false
                || isCurrentPlayerMismatch())
        {
            return -1L;
        }

        long lastSyncMs = lastSuccessfulSyncMs();
        if (lastSyncMs <= 0L)
        {
            return 0L;
        }

        return Math.max(0L, lastSyncMs + getSyncIntervalMs() - now);
    }
    public static String getNextSyncLabel()
    {
        PendingSyncQueue.Snapshot snapshot = SyncQueueManager.getSnapshot();
        if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() == false)
        {
            return "off";
        }
        if (Configs.cloudSyncEndpoint == null || Configs.cloudSyncEndpoint.isBlank())
        {
            return "unavailable";
        }
        int pendingLinkClaims = snapshot.countFor(SyncItemType.WEBSITE_LINK_CLAIM);
        if (pendingLinkClaims > 0)
        {
            if (snapshot.flushActive())
            {
                return "linking";
            }
            long nextAttemptAtMs = snapshot.nextAttemptAtMs();
            long now = System.currentTimeMillis();
            if (nextAttemptAtMs <= 0L || nextAttemptAtMs <= now)
            {
                return "link now";
            }
            return "link " + UiFormat.formatDuration(Math.max(1L, (nextAttemptAtMs - now + 999L) / 1000L));
        }
        if (WebsiteLinkManager.hasPersistedLink() == false)
        {
            return "link required";
        }
        if (isCurrentPlayerMismatch())
        {
            return "wrong account";
        }
        if (snapshot.flushActive())
        {
            return "syncing";
        }
        int pending = snapshot.countFor(SyncItemType.CLOUD_LIVE_STATE)
                + snapshot.countFor(SyncItemType.CLOUD_FINISHED_SESSION)
                + snapshot.countFor(SyncItemType.PLAYER_TOTAL_DIGS);
        if (pending > 0)
        {
            long nextAttemptAtMs = snapshot.nextAttemptAtMs();
            long now = System.currentTimeMillis();
            if (nextAttemptAtMs <= 0L || nextAttemptAtMs <= now)
            {
                return "now";
            }
            return UiFormat.formatDuration(Math.max(1L, (nextAttemptAtMs - now + 999L) / 1000L));
        }

        long remainingMs = getNextSyncRemainingMs();
        if (remainingMs < 0L)
        {
            return "off";
        }
        if (remainingMs <= 0L)
        {
            return "now";
        }
        return UiFormat.formatDuration(Math.max(1L, (remainingMs + 999L) / 1000L));
    }
    public static String getSyncTier()
    {
        return Configs.normalizeWebsiteSyncTier(Configs.websiteSyncTier);
    }

    private static boolean isSyncCadenceDue(long now)
    {
        if (lastLiveBlockSyncMs > 0L && now - lastLiveBlockSyncMs < MIN_LIVE_SYNC_ATTEMPT_INTERVAL_MS)
        {
            return false;
        }

        long lastSyncMs = lastSuccessfulSyncMs();
        return lastSyncMs <= 0L || now - lastSyncMs >= getSyncIntervalMs();
    }

    static boolean isCurrentContextPayloadPreparedForSync()
    {
        return currentContextPayloadPrepared;
    }

    private static void refreshLeaderboardSnapshot(MinecraftClient client, long now, boolean force)
    {
        if (client == null)
        {
            return;
        }
        if (force == false && now - lastSourceScoreboardScanMs < SOURCE_SCOREBOARD_SCAN_INTERVAL_MS)
        {
            return;
        }

        lastSourceScoreboardScanMs = now;
        latestLeaderboardSnapshot = SourceLeaderboardReader.read(client);
    }

    private static long lastSuccessfulSyncMs()
    {
        String sourceKey = currentSourceKey();
        return sourceKey.isBlank()
                ? Math.max(0L, Configs.websiteLastSuccessfulSyncMs)
                : Configs.getSourceLastSuccessfulSyncMs(sourceKey);
    }

    public static long getLastSuccessfulSyncMs()
    {
        return lastSuccessfulSyncMs();
    }

    private static String currentSourceKey()
    {
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        if (worldInfo == null || worldInfo.id() == null || worldInfo.id().isBlank())
        {
            return "";
        }
        return ScoreboardSourceResolver.sourceKey(worldInfo.displayName(), worldInfo);
    }
    private static void queueCurrentLivePayloadIfDue(long now)
    {
        if (isSyncCadenceDue(now) == false)
        {
            return;
        }

        lastLiveBlockSyncMs = now;
        lastHeartbeatMs = now;
        SessionData liveSession = MiningStats.isSessionActive() ? MiningStats.getCurrentSession() : null;
        queueLivePayload(buildPayload(liveSession, liveSession == null ? null : getCurrentSessionStatus()), true);
    }

    private static void queueLivePayload(JsonObject payload)
    {
        queueLivePayload(payload, false);
    }

    private static void queueLivePayload(JsonObject payload, boolean force)
    {
        String fingerprint = livePayloadFingerprint(payload);

        if (!force
                && fingerprint != null
                && fingerprint.equals(lastSuccessfulLiveFingerprint)
                && syncStatus == SyncStatus.SYNCED)
        {
            return;
        }

        if (!force
                && fingerprint != null
                && fingerprint.equals(lastQueuedLiveFingerprint)
                && syncStatus == SyncStatus.QUEUED)
        {
            return;
        }

        lastQueuedLiveFingerprint = fingerprint;
        currentContextPayloadPrepared = true;
        SyncQueueManager.enqueueCloudLiveState(payload);
    }

    static void applySuccessfulSyncResponse(JsonObject payload, String responseBody)
    {
        applySyncResponse(responseBody);
        WebsiteProfileTotals.refresh(true);
        long cadenceAnchorMs = sourceSyncCadenceAnchor(responseBody);
        String sourceKey = payloadSourceKey(payload);
        if (cadenceAnchorMs > 0L && sourceKey.isBlank() == false)
        {
            Configs.recordSourceSuccessfulSync(sourceKey, cadenceAnchorMs);
            Configs.saveToFile();
        }
    }

    private static String payloadSourceKey(JsonObject payload)
    {
        JsonObject world = getObject(payload, "world");
        String sourceKey = getString(world, "source_key", "");
        if (sourceKey.isBlank())
        {
            sourceKey = getString(world, "key", "");
        }
        if (sourceKey.isBlank())
        {
            sourceKey = getString(getObject(payload, "current_world_totals"), "world_key", "");
        }
        return sourceKey.trim().toLowerCase(Locale.ROOT);
    }
    static long sourceSyncCadenceAnchor(String responseBody)
    {
        if (responseBody == null || responseBody.isBlank())
        {
            return 0L;
        }

        try
        {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (root.has("source_sync_accepted")
                    && root.get("source_sync_accepted").isJsonPrimitive()
                    && root.get("source_sync_accepted").getAsBoolean())
            {
                return System.currentTimeMillis();
            }
            if (responseBoolean(responseBody, "sync_skipped")
                    && responseString(root, "reason").equals("24_hour_cooldown"))
            {
                long nextSyncAtMs = responseTimestamp(root, "next_sync_at");
                JsonObject syncPolicy = root.has("sync_policy") && root.get("sync_policy").isJsonObject()
                        ? root.getAsJsonObject("sync_policy") : null;
                long intervalMs = responseLong(syncPolicy, "interval_ms");
                if (nextSyncAtMs > 0L && intervalMs > 0L)
                {
                    return Math.max(1L, Math.min(System.currentTimeMillis(), nextSyncAtMs - intervalMs));
                }
            }
        }
        catch (Exception ignored)
        {
        }

        return 0L;
    }

    private static void applySyncResponse(String responseBody)
    {
        if (responseBody == null || responseBody.isBlank())
        {
            return;
        }

        try
        {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            boolean changed = false;

            JsonObject syncPolicy = getObject(root, "sync_policy");
            if (syncPolicy != null)
            {
                String tier = Configs.normalizeWebsiteSyncTier(getString(syncPolicy, "tier", Configs.websiteSyncTier));
                long intervalMs = Configs.normalizeWebsiteSyncIntervalMs(getLong(syncPolicy, "interval_ms", Configs.websiteSyncIntervalMs));
                if (tier.equals(Configs.websiteSyncTier) == false)
                {
                    Configs.websiteSyncTier = tier;
                    changed = true;
                }
                if (intervalMs != Configs.websiteSyncIntervalMs)
                {
                    Configs.websiteSyncIntervalMs = intervalMs;
                    changed = true;
                }
            }

            JsonObject playerProfile = getObject(root, "player_profile");
            if (playerProfile != null)
            {
                long currentGlobalTotal = Math.max(0L, Configs.websiteGlobalTotalBlocks);
                long globalTotal = Math.max(0L, getLong(playerProfile, "global_total_blocks", currentGlobalTotal));
                if (playerProfile.has("global_total_blocks") && globalTotal > 0L && (currentGlobalTotal <= 0L || globalTotal >= currentGlobalTotal))
                {
                    Configs.websiteGlobalTotalBlocks = globalTotal;
                    Configs.websiteGlobalTotalUpdatedAtMs = System.currentTimeMillis();
                    changed = true;
                }
                else if (playerProfile.has("global_total_blocks") && globalTotal > 0L && currentGlobalTotal > 0L && globalTotal < currentGlobalTotal)
                {
                    MmmDebugLogger.info(
                            "website-global-total-stale",
                            30_000L,
                            "[MMM_SYNC] ignored lower website global total candidate={} current={}",
                            globalTotal,
                            currentGlobalTotal);
                }
            }

            if (changed)
            {
                Configs.saveToFile();
            }
        }
        catch (Exception ignored)
        {
        }
    }

    private static JsonObject getObject(JsonObject root, String key)
    {
        if (root == null || root.has(key) == false)
        {
            return null;
        }

        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String key, String fallback)
    {
        if (object == null || object.has(key) == false)
        {
            return fallback;
        }

        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static long getLong(JsonObject object, String key, long fallback)
    {
        if (object == null || object.has(key) == false)
        {
            return fallback;
        }

        try
        {
            JsonElement element = object.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsLong() : fallback;
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }

    private static void touchHealthy()
    {
        lastHealthySignalMs = System.currentTimeMillis();
        lastFailureSignalMs = 0L;
    }

    private static boolean canSync()
    {
        if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() == false)
        {
            logSyncUnavailable("websiteSyncEnabled_false");
            return false;
        }

        if (Configs.cloudSyncEndpoint == null || Configs.cloudSyncEndpoint.isBlank())
        {
            logSyncUnavailable("endpoint_blank");
            return false;
        }

        if (WebsiteLinkManager.hasPersistedLink() == false)
        {
            logSyncUnavailable("website_link_required");
            syncStatusDetail = "Generate a new mod link code on the website, then enter it in Website Link.";
            return false;
        }

        if (isCurrentPlayerMismatch())
        {
            logSyncUnavailable("linked_account_mismatch");
            syncStatusDetail = "The current Minecraft account does not match the linked account.";
            return false;
        }

        return true;
    }
    private static void logSyncUnavailable(String reason)
    {
        long now = System.currentTimeMillis();
        syncStatusDetail = "Sync disabled: " + reason;

        if (reason.equals(lastSyncUnavailableReason) && now - lastSyncUnavailableLogMs < SYNC_UNAVAILABLE_LOG_INTERVAL_MS)
        {
            return;
        }

        lastSyncUnavailableReason = reason;
        lastSyncUnavailableLogMs = now;
        MMM.LOGGER.warn("{} cloud-sync-disabled reason={} endpointConfigured={}",
                LOG_PREFIX,
                reason,
                Configs.cloudSyncEndpoint != null && Configs.cloudSyncEndpoint.isBlank() == false);
    }

    private static boolean hasLiveContext()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && client.world != null;
    }

    private static boolean isCurrentPlayerMismatch()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && client.player != null
                && WebsiteLinkManager.isCurrentPlayerLinked() == false;
    }
    private static String getCurrentSessionStatus()
    {
        if (MiningStats.isSessionActive() == false)
        {
            return null;
        }

        return MiningStats.isSessionPaused() ? "paused" : "active";
    }

    private record SourceEvidence(
            SourceScanResult scan,
            List<SourceLeaderboardSnapshot> leaderboards,
            long playerTotalDigs
    ) {}

    private static JsonObject buildPayload(SessionData session, String sessionStatus)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        MiningStats.GoalProgress dailyGoal = MiningStats.getDailyGoalProgress();
        MiningStats.ProjectProgress projectProgress = MiningStats.getActiveProjectProgress();

        SourceEvidence sourceEvidence = readSourceEvidence(client, worldInfo);

        JsonObject payload = new JsonObject();
        payload.addProperty("client_id", Configs.cloudClientId);
        payload.addProperty("minecraft_uuid", client != null && client.player != null ? client.player.getUuidAsString() : null);
        payload.addProperty("username", resolveUsername(client));
        payload.addProperty("mod_version", Reference.MOD_VERSION);
        payload.addProperty("minecraft_version", client != null ? client.getGameVersion() : null);
        payload.addProperty("sync_origin", "client_evidence");
        payload.add("world", buildWorld(worldInfo));
        payload.add("lifetime_totals", buildLifetimeTotals());
        payload.add("mining_records", buildMiningRecords());
        JsonArray dailyMining = MiningCalendarStore.pendingEntries();
        if (dailyMining.size() > 0)
        {
            payload.add("daily_mining", dailyMining);
        }
        payload.add("current_world_totals", buildCurrentWorldTotals(worldInfo, sourceEvidence.playerTotalDigs()));

        JsonObject currentWorldBlockBreakdown = BlockBreakdownPayloads.buildCurrentWorldBlockBreakdown(worldInfo);
        if (currentWorldBlockBreakdown != null)
        {
            payload.add("current_world_block_breakdown", currentWorldBlockBreakdown);
        }

        JsonObject serverPlayerBlockBreakdowns = ServerPlayerBlockBreakdownScanner.scan(client, worldInfo);
        if (serverPlayerBlockBreakdowns != null)
        {
            payload.add("server_player_block_breakdowns", serverPlayerBlockBreakdowns);
        }

        JsonObject sourceScan = buildSourceScan(sourceEvidence.scan(), worldInfo);
        if (sourceScan != null)
        {
            payload.add("source_scan", sourceScan);
        }

        JsonArray sourceLeaderboards = buildSourceLeaderboards(sourceEvidence.leaderboards());
        if (sourceLeaderboards.size() > 0)
        {
            payload.add("source_leaderboards", sourceLeaderboards);
            payload.add("source_leaderboard", sourceLeaderboards.get(0).deepCopy());
        }

        JsonObject playerTotalDigs = buildPlayerTotalDigs(client, worldInfo, sourceEvidence);
        if (playerTotalDigs != null)
        {
            payload.add("player_total_digs", playerTotalDigs);
        }

        payload.add("projects", buildProjects());
        payload.add("daily_goal", buildDailyGoal(dailyGoal));
        payload.add("synced_stats", buildSyncedStats(projectProgress, dailyGoal));
        payload.add("session_state", buildSessionState());
        if (sessionStatus != null && SessionHistory.isQualifyingSession(session))
        {
            payload.add("session", buildSession(session, sessionStatus));
        }

        JsonArray savedSessions = buildPendingSavedSessions(worldInfo, session);
        if (savedSessions.size() > 0)
        {
            payload.add("sessions", savedSessions);
        }

        debugPayloadSource(worldInfo, payload);

        return payload;
    }

    private static JsonArray buildPendingSavedSessions(WorldSessionContext.WorldInfo worldInfo,
                                                       SessionData primarySession)
    {
        JsonArray result = new JsonArray();
        if (worldInfo == null || worldInfo.id() == null || worldInfo.id().isBlank())
        {
            return result;
        }

        String primarySessionKey = primarySession == null ? "" : sessionKey(primarySession);
        List<SessionData> pendingSessions = new ArrayList<>();
        for (SessionHistory.WorldHistory history : SessionHistory.getWorldHistories())
        {
            if (WorldIdentity.matchesCurrentWorld(
                    history.worldId(),
                    worldInfo.id(),
                    worldInfo.kind(),
                    worldInfo.host()) == false)
            {
                continue;
            }

            for (SessionData savedSession : history.sessions())
            {
                String savedSessionKey = sessionKey(savedSession);
                if (SessionHistory.isQualifyingSession(savedSession) == false
                        || SessionSyncState.isSynced(savedSessionKey)
                        || savedSessionKey.equals(primarySessionKey))
                {
                    continue;
                }
                pendingSessions.add(savedSession);
            }
        }

        pendingSessions.sort(Comparator
                .comparingLong((SessionData savedSession) -> savedSession.endTimeMs)
                .thenComparingLong(savedSession -> savedSession.startTimeMs)
                .reversed());

        for (SessionData savedSession : pendingSessions)
        {
            result.add(buildSession(savedSession, "ended"));
            if (result.size() >= MAX_SAVED_SESSIONS_PER_PAYLOAD)
            {
                break;
            }
        }
        return result;
    }

    private static JsonObject buildSessionState()
    {
        JsonObject state = new JsonObject();
        state.addProperty("session_active", MiningStats.isSessionActive());
        state.addProperty("session_paused", MiningStats.isSessionPaused());
        state.addProperty("session_total_blocks", MiningStats.getSessionBlocksMined());
        state.addProperty("session_duration_seconds", Math.max(0L, MiningStats.getSessionDurationMs() / 1000L));
        return state;
    }

    private static JsonObject buildLifetimeTotals()
    {
        JsonObject totals = new JsonObject();
        totals.addProperty("total_blocks", Configs.totalBlocksMined);
        totals.addProperty("daily_blocks_mined", MiningStats.getDailyBlocksMined());
        totals.addProperty("weekly_blocks_mined", MiningStats.getWeeklyBlocksMined());
        totals.addProperty("personal_record_daily_blocks", MiningStats.getPersonalRecordDailyBlocks());
        totals.addProperty("personal_record_weekly_blocks", MiningStats.getPersonalRecordWeeklyBlocks());
        totals.addProperty("fastest_100k_seconds", MiningStats.getFastest100kSeconds());
        return totals;
    }

    private static JsonObject buildMiningRecords()
    {
        JsonObject records = new JsonObject();
        records.addProperty("captured_at", toIso(System.currentTimeMillis()));
        records.addProperty("daily_blocks_date", MiningStats.getDailyBlocksDate());
        records.addProperty("weekly_blocks_week", MiningStats.getWeeklyBlocksWeek());
        records.addProperty("daily_blocks_mined", MiningStats.getDailyBlocksMined());
        records.addProperty("weekly_blocks_mined", MiningStats.getWeeklyBlocksMined());
        records.addProperty("personal_record_daily_blocks", MiningStats.getPersonalRecordDailyBlocks());
        records.addProperty("personal_record_weekly_blocks", MiningStats.getPersonalRecordWeeklyBlocks());
        records.addProperty("fastest_100k_seconds", MiningStats.getFastest100kSeconds());
        records.addProperty("fastest_100k_started_at", Configs.fastest100kStartedAtMs > 0L ? toIso(Configs.fastest100kStartedAtMs) : null);
        records.addProperty("fastest_100k_finished_at", Configs.fastest100kFinishedAtMs > 0L ? toIso(Configs.fastest100kFinishedAtMs) : null);
        return records;
    }

    private static JsonObject buildCurrentWorldTotals(WorldSessionContext.WorldInfo worldInfo)
    {
        return buildCurrentWorldTotals(worldInfo, 0L);
    }

    private static JsonObject buildCurrentWorldTotals(WorldSessionContext.WorldInfo worldInfo, long authoritativePlayerTotal)
    {
        Configs.WorldStatsEntry worldStats = Configs.getOrCreateWorldStats(
                worldInfo.id(),
                worldInfo.displayName(),
                worldInfo.kind(),
                worldInfo.host());
        boolean freshScoreboardArgument = authoritativePlayerTotal > 0L;
        long totalBlocks = SourceTotalPolicy.preferAuthoritative(
                MiningStats.getCurrentSourceTotalMined(),
                authoritativePlayerTotal,
                freshScoreboardArgument);
        boolean scoreboardBacked = freshScoreboardArgument || MiningStats.hasAuthoritativeCurrentSourceScoreboardTotal();

        JsonObject totals = new JsonObject();
        totals.addProperty("world_key", worldStats.worldId);
        totals.addProperty("display_name", worldStats.displayName);
        totals.addProperty("kind", normaliseWorldKind(worldStats.kind));
        totals.addProperty("source_type", worldInfo.sourceType());
        totals.addProperty("host", (String) null);
        totals.addProperty("total_blocks", totalBlocks);
        totals.addProperty("total_origin", scoreboardBacked ? "scoreboard" : "client_valid_blocks");
        totals.addProperty("last_seen_at", toIso(Math.max(worldStats.lastSeenAt, System.currentTimeMillis())));
        return totals;
    }

    private static SourceEvidence readSourceEvidence(MinecraftClient client, WorldSessionContext.WorldInfo worldInfo)
    {
        SourceScanResult scan = SourceScanManager.scan(client);
        if (scan != null && scan.hasMeaningfulEvidence() == false)
        {
            scan = null;
        }

        List<SourceLeaderboardSnapshot> leaderboards = SourceLeaderboardReader.readAll(client);
        latestLeaderboardSnapshot = leaderboards.isEmpty() ? null : leaderboards.get(0);
        long playerTotalDigs = resolvePlayerTotalDigs(client, scan, latestLeaderboardSnapshot);

        return new SourceEvidence(scan, leaderboards, playerTotalDigs);
    }

    private static long resolvePlayerTotalDigs(MinecraftClient client,
                                               SourceScanResult scan,
                                               SourceLeaderboardSnapshot snapshot)
    {
        if (scan != null && scan.playerTotalDigs() > 0L)
        {
            return scan.playerTotalDigs();
        }

        if (client == null || client.player == null || snapshot == null || snapshot.isValid() == false)
        {
            return 0L;
        }

        String username = client.player.getGameProfile().getName();
        return snapshot.entries().stream()
                .filter(SourceLeaderboardEntry::isValid)
                .filter(entry -> entry.username().equalsIgnoreCase(username))
                .mapToLong(SourceLeaderboardEntry::digs)
                .max()
                .orElse(0L);
    }

    private static JsonArray buildSourceLeaderboards(List<SourceLeaderboardSnapshot> snapshots)
    {
        JsonArray leaderboards = new JsonArray();
        if (snapshots == null || snapshots.isEmpty())
        {
            return leaderboards;
        }

        for (SourceLeaderboardSnapshot snapshot : snapshots)
        {
            JsonObject leaderboard = buildSourceLeaderboard(snapshot);
            if (leaderboard != null)
            {
                leaderboards.add(leaderboard);
            }
        }

        return leaderboards;
    }

    private static JsonObject buildSourceLeaderboard()
    {
        return buildSourceLeaderboard(latestLeaderboardSnapshot);
    }

    private static JsonObject buildSourceLeaderboard(SourceLeaderboardSnapshot snapshot)
    {
        if (snapshot == null || snapshot.isValid() == false)
        {
            return null;
        }

        SourceLeaderboardPayloadSupport.FilterResult filtered = SourceLeaderboardPayloadSupport.filterEntries(
                MinecraftClient.getInstance(),
                snapshot.entries());
        List<SourceLeaderboardEntry> realEntries = filtered.entries();

        if (realEntries.isEmpty())
        {
            return null;
        }

        JsonObject leaderboard = new JsonObject();
        leaderboard.addProperty("server_name", snapshot.serverName());
        leaderboard.addProperty("objective_title", snapshot.objectiveTitle());
        leaderboard.addProperty("captured_at", toIso(snapshot.capturedAtMs()));
        leaderboard.addProperty("source_type", "scoreboard");
        leaderboard.addProperty("mode", "full");
        leaderboard.addProperty("complete_snapshot", true);

        long payloadTotalDigs = SourceLeaderboardPayloadSupport.resolveTotal(snapshot, realEntries);
        if (payloadTotalDigs > 0L)
        {
            leaderboard.addProperty("total_digs", payloadTotalDigs);
        }

        JsonArray entries = new JsonArray();
        for (SourceLeaderboardEntry entry : realEntries)
        {
            JsonObject row = new JsonObject();
            row.addProperty("username", entry.username());
            row.addProperty("digs", entry.digs());
            row.addProperty("rank", entry.rank());
            row.addProperty("source_server", snapshot.serverName());
            entries.add(row);
        }

        if (filtered.fakeUsernames().isEmpty() == false && filtered.filterCollapsedScoreboard() == false)
        {
            JsonArray filteredUsernames = new JsonArray();
            filtered.fakeUsernames().stream().sorted().forEach(filteredUsernames::add);
            leaderboard.add("filtered_fake_usernames", filteredUsernames);
        }

        leaderboard.add("entries", entries);
        return leaderboard;
    }

    private static JsonObject buildSourceScan(MinecraftClient client, WorldSessionContext.WorldInfo worldInfo)
    {
        SourceScanResult scan = SourceScanManager.scan(client);
        return buildSourceScan(scan, worldInfo);
    }

    private static JsonObject buildSourceScan(SourceScanResult scan, WorldSessionContext.WorldInfo worldInfo)
    {
        if (scan == null || scan.hasMeaningfulEvidence() == false)
        {
            return null;
        }

        JsonObject object = new JsonObject();
        object.addProperty("compatible", scan.compatible());
        object.addProperty("confidence", scan.confidence());
        object.addProperty("scoreboard_title", scan.scoreboardTitle());

        if (scan.totalDigs() > 0L)
        {
            object.addProperty("total_digs", scan.totalDigs());
        }

        if (scan.playerTotalDigs() > 0L)
        {
            object.addProperty("player_total_digs", scan.playerTotalDigs());
        }

        object.addProperty("server_name", scan.sourceName());
        object.addProperty("source_key", ScoreboardSourceResolver.sourceKey(
                worldInfo != null ? worldInfo.displayName() : "",
                worldInfo));
        object.addProperty("icon_url", scan.iconUrl());
        object.addProperty("scan_fingerprint", scan.scanFingerprint());

        JsonArray sampleLines = new JsonArray();
        for (String line : scan.sampleSidebarLines())
        {
            sampleLines.add(line);
        }
        object.add("sample_sidebar_lines", sampleLines);

        JsonArray detectedFields = new JsonArray();
        for (String field : scan.detectedStatFields())
        {
            detectedFields.add(field);
        }
        object.add("detected_stat_fields", detectedFields);

        JsonObject evidence = new JsonObject();
        evidence.addProperty("source_name", scan.sourceName());
        evidence.addProperty("source_key", ScoreboardSourceResolver.sourceKey(
                worldInfo != null ? worldInfo.displayName() : "",
                worldInfo));
        evidence.addProperty("source_kind", worldInfo.kind());
        evidence.addProperty("source_host", (String) null);
        evidence.addProperty("icon_url", scan.iconUrl());
        evidence.addProperty("scoreboard_title", scan.scoreboardTitle());
        evidence.addProperty("total_digs", scan.totalDigs());
        evidence.addProperty("player_total_digs", scan.playerTotalDigs());
        evidence.addProperty("compatible", scan.compatible());
        evidence.addProperty("confidence", scan.confidence());
        evidence.add("sample_sidebar_lines", sampleLines.deepCopy());
        evidence.add("detected_stat_fields", detectedFields.deepCopy());
        object.add("raw_scan_evidence", evidence);

        return object;
    }

    private static JsonObject buildPlayerTotalDigs(MinecraftClient client,
                                                   WorldSessionContext.WorldInfo worldInfo,
                                                   SourceEvidence sourceEvidence)
    {
        long scoreboardTotal = sourceEvidence == null ? 0L : Math.max(0L, sourceEvidence.playerTotalDigs());
        boolean freshScoreboardArgument = scoreboardTotal > 0L;
        long effectivePlayerTotal = SourceTotalPolicy.preferAuthoritative(
                MiningStats.getCurrentSourceTotalMined(),
                scoreboardTotal,
                freshScoreboardArgument);
        boolean scoreboardBacked = freshScoreboardArgument || MiningStats.hasAuthoritativeCurrentSourceScoreboardTotal();
        if (effectivePlayerTotal <= 0L)
        {
            return null;
        }

        String serverName = sourceEvidence != null && sourceEvidence.scan() != null && sourceEvidence.scan().sourceName() != null && sourceEvidence.scan().sourceName().isBlank() == false
                ? sourceEvidence.scan().sourceName()
                : latestLeaderboardSnapshot != null && latestLeaderboardSnapshot.serverName() != null && latestLeaderboardSnapshot.serverName().isBlank() == false
                ? latestLeaderboardSnapshot.serverName()
                : ScoreboardSourceResolver.displayName(worldInfo.displayName(), worldInfo);

        String objectiveTitle = sourceEvidence != null && sourceEvidence.scan() != null && sourceEvidence.scan().scoreboardTitle() != null && sourceEvidence.scan().scoreboardTitle().isBlank() == false
                ? sourceEvidence.scan().scoreboardTitle()
                : latestLeaderboardSnapshot != null && latestLeaderboardSnapshot.objectiveTitle() != null && latestLeaderboardSnapshot.objectiveTitle().isBlank() == false
                ? latestLeaderboardSnapshot.objectiveTitle()
                : "Scoreboard";

        JsonObject digs = new JsonObject();
        digs.addProperty("username", resolveUsername(client));
        digs.addProperty("total_digs", effectivePlayerTotal);
        digs.addProperty("server", serverName);
        digs.addProperty("timestamp", toIso(System.currentTimeMillis()));
        digs.addProperty("objective_title", objectiveTitle);
        digs.addProperty("total_origin", scoreboardBacked ? "scoreboard" : "client_valid_blocks");
        return digs;
    }

    private static JsonObject buildWorld(WorldSessionContext.WorldInfo worldInfo)
    {
        JsonObject world = new JsonObject();
        world.addProperty("key", worldInfo.id());
        world.addProperty("display_name", worldInfo.displayName());
        world.addProperty("kind", normaliseWorldKind(worldInfo.kind()));
        world.addProperty("source_type", worldInfo.sourceType());
        world.addProperty("host", (String) null);
        world.addProperty("source_key", ScoreboardSourceResolver.sourceKey(worldInfo.displayName(), worldInfo));
        world.addProperty("source_name", ScoreboardSourceResolver.displayName(worldInfo.displayName(), worldInfo));
        return world;
    }

    private static JsonObject buildWorld(String worldId, String displayName)
    {
        Configs.WorldStatsEntry worldStats = findWorldStats(worldId);
        String resolvedWorldId = worldId == null || worldId.isBlank() ? "default" : worldId;
        String resolvedDisplayName = displayName != null && displayName.isBlank() == false
                ? displayName
                : worldStats != null && worldStats.displayName != null && worldStats.displayName.isBlank() == false
                ? worldStats.displayName
                : resolvedWorldId;

        JsonObject world = new JsonObject();
        world.addProperty("key", resolvedWorldId);
        world.addProperty("display_name", resolvedDisplayName);
        String kind = normaliseWorldKind(worldStats == null ? "unknown" : worldStats.kind);
        world.addProperty("kind", kind);
        world.addProperty("source_type", "singleplayer".equals(kind) ? "ssp" : "server");
        world.addProperty("host", (String) null);
        world.addProperty("source_key", resolvedWorldId);
        world.addProperty("source_name", resolvedDisplayName);
        return world;
    }

    private static Configs.WorldStatsEntry findWorldStats(String worldId)
    {
        if (worldId == null || worldId.isBlank())
        {
            return null;
        }

        for (Configs.WorldStatsEntry entry : Configs.WORLD_STATS)
        {
            if (worldId.equals(entry.worldId))
            {
                return entry;
            }
        }

        return null;
    }

    private static JsonArray buildProjects()
    {
        JsonArray projects = new JsonArray();
        ProjectEntry activeProject = Configs.getActiveProject();

        for (ProjectEntry project : Configs.PROJECTS)
        {
            JsonObject entry = new JsonObject();
            entry.addProperty("project_key", project.id);
            entry.addProperty("name", project.name);
            entry.addProperty("progress", project.progress);
            entry.addProperty("goal", (String) null);
            entry.addProperty("is_active", activeProject != null && activeProject.id.equals(project.id));
            projects.add(entry);
        }

        return projects;
    }

    private static JsonObject buildDailyGoal(MiningStats.GoalProgress dailyGoal)
    {
        JsonObject goal = new JsonObject();
        goal.addProperty("goal_date", PeriodKeys.currentDailyKey(System.currentTimeMillis()));
        goal.addProperty("target", dailyGoal.target());
        goal.addProperty("progress", dailyGoal.current());
        goal.addProperty("completed", dailyGoal.target() > 0L && dailyGoal.current() >= dailyGoal.target());
        return goal;
    }

    private static JsonObject buildSyncedStats(MiningStats.ProjectProgress projectProgress,
                                               MiningStats.GoalProgress dailyGoal)
    {
        JsonObject syncedStats = new JsonObject();
        syncedStats.addProperty("blocks_per_hour", MiningStats.getEstimatedBlocksPerHour());
        syncedStats.addProperty("estimated_finish_seconds", (String) null);
        syncedStats.addProperty("current_project_name", projectProgress.name());
        syncedStats.addProperty("current_project_progress", projectProgress.blocksMined());
        syncedStats.addProperty("current_project_goal", (String) null);
        syncedStats.addProperty("daily_progress", dailyGoal.current());
        syncedStats.addProperty("daily_target", dailyGoal.target());
        syncedStats.addProperty("daily_blocks_mined", MiningStats.getDailyBlocksMined());
        syncedStats.addProperty("weekly_blocks_mined", MiningStats.getWeeklyBlocksMined());
        syncedStats.addProperty("personal_record_daily_blocks", MiningStats.getPersonalRecordDailyBlocks());
        syncedStats.addProperty("personal_record_weekly_blocks", MiningStats.getPersonalRecordWeeklyBlocks());
        syncedStats.addProperty("fastest_100k_seconds", MiningStats.getFastest100kSeconds());
        return syncedStats;
    }

    private static JsonObject buildSession(SessionData session, String status)
    {
        JsonObject sessionObject = new JsonObject();
        sessionObject.addProperty("session_key", sessionKey(session));
        sessionObject.addProperty("started_at", toIso(session.startTimeMs));
        sessionObject.addProperty("ended_at", "ended".equals(status) ? toIso(session.endTimeMs) : null);
        sessionObject.addProperty("active_seconds", session.getDurationMs() / 1000L);
        sessionObject.addProperty("total_blocks", session.totalBlocks);
        sessionObject.addProperty("average_bph", session.getAverageBlocksPerHour());
        sessionObject.addProperty("peak_bph", session.getPeakBlocksPerHour());
        sessionObject.addProperty("best_streak_seconds", session.bestStreakSeconds);
        Map<String, Long> sanitizedBreakdown = Configs.sanitizeBlockBreakdown(session.blockBreakdown);
        sessionObject.addProperty("top_block", getTopBlock(sanitizedBreakdown));
        sessionObject.addProperty("status", status);
        sessionObject.add("block_breakdown", buildBreakdown(sanitizedBreakdown));
        sessionObject.add("rate_points", buildRatePoints(session.miningRateBuckets));
        return sessionObject;
    }

    private static String sessionKey(SessionData session)
    {
        return "sess_" + session.startTimeMs;
    }

    private static void markSyncedSessions(JsonObject payload, String responseBody)
    {
        if (payload == null)
        {
            return;
        }

        if (payload.has("session") && payload.get("session").isJsonObject())
        {
            markSyncedSession(payload.getAsJsonObject("session"), responseBody);
        }

        if (payload.has("sessions") && payload.get("sessions").isJsonArray())
        {
            JsonArray sessions = payload.getAsJsonArray("sessions");
            for (JsonElement element : sessions)
            {
                if (element != null && element.isJsonObject())
                {
                    markSyncedSession(element.getAsJsonObject(), responseBody);
                }
            }
        }
    }

    private static void markSyncedSession(JsonObject session, String responseBody)
    {
        if (session.has("status") == false
                || session.get("status").isJsonPrimitive() == false
                || "ended".equals(session.get("status").getAsString()) == false)
        {
            return;
        }

        if (session.has("session_key") && session.get("session_key").isJsonPrimitive())
        {
            String sessionKey = session.get("session_key").getAsString();
            if (serverStoredSession(responseBody, sessionKey))
            {
                SessionSyncState.markSynced(sessionKey);
            }
        }
    }

    private static boolean serverStoredSession(String responseBody, String sessionKey)
    {
        if (responseBody == null || responseBody.isBlank())
        {
            return false;
        }

        try
        {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (root.has("sessions") && root.get("sessions").isJsonArray())
            {
                JsonArray sessions = root.getAsJsonArray("sessions");
                for (JsonElement element : sessions)
                {
                    if (element != null && element.isJsonObject()
                            && sessionAckStored(element.getAsJsonObject(), sessionKey))
                    {
                        return true;
                    }
                }
            }

            if (root.has("session") && root.get("session").isJsonObject())
            {
                return sessionAckStored(root.getAsJsonObject("session"), sessionKey);
            }

            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static boolean sessionAckStored(JsonObject syncSession, String sessionKey)
    {
        if (syncSession == null)
        {
            return false;
        }

        try
        {
            if (sessionKey == null
                    || syncSession.has("session_key") == false
                    || syncSession.get("session_key").isJsonPrimitive() == false)
            {
                return false;
            }

            if (sessionKey.equals(syncSession.get("session_key").getAsString()) == false)
            {
                return false;
            }

            return syncSession.has("stored")
                    && syncSession.get("stored").isJsonPrimitive()
                    && syncSession.get("stored").getAsBoolean();
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static JsonArray buildBreakdown(Map<String, Long> breakdown)
    {
        JsonArray array = new JsonArray();
        breakdown.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> {
                    JsonObject item = new JsonObject();
                    item.addProperty("block_id", entry.getKey());
                    item.addProperty("count", entry.getValue());
                    array.add(item);
                });
        return array;
    }

    private static JsonArray buildRatePoints(List<Integer> rateBuckets)
    {
        JsonArray array = new JsonArray();
        List<Integer> buckets = rateBuckets == null ? List.of() : new ArrayList<>(rateBuckets);

        for (int index = 0; index < buckets.size(); index++)
        {
            JsonObject point = new JsonObject();
            point.addProperty("point_index", index);
            point.addProperty("blocks_per_hour", Math.max(0, buckets.get(index)) * 60);
            point.addProperty("elapsed_seconds", (index + 1) * 60);
            array.add(point);
        }

        return array;
    }

    private static String getTopBlock(Map<String, Long> breakdown)
    {
        return breakdown.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static String normaliseWorldKind(String kind)
    {
        if ("singleplayer".equals(kind) || "multiplayer".equals(kind) || "realm".equals(kind))
        {
            return kind;
        }

        return "unknown";
    }

    private static String resolveUsername(MinecraftClient client)
    {
        if (client == null)
        {
            return "Player";
        }

        try
        {
            String username = client.getSession().getUsername();
            if (username != null && username.isBlank() == false)
            {
                return username;
            }
        }
        catch (Exception ignored)
        {
        }

        if (client.player != null)
        {
            return client.player.getName().getString();
        }

        return "Player";
    }

    private static boolean hasSessionToken(MinecraftClient client)
    {
        if (client == null || client.getSession() == null)
        {
            return false;
        }

        try
        {
            String token = client.getSession().getAccessToken();
            return token != null && token.isBlank() == false;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private static String toIso(long timeMs)
    {
        return Instant.ofEpochMilli(timeMs).toString();
    }

    private static String livePayloadFingerprint(JsonObject payload)
    {
        if (payload == null)
        {
            return null;
        }

        JsonObject minimal = new JsonObject();

        if (payload.has("world"))
        {
            minimal.add("world", payload.get("world"));
        }

        if (payload.has("lifetime_totals"))
        {
            minimal.add("lifetime_totals", payload.get("lifetime_totals"));
        }

        if (payload.has("current_world_totals"))
        {
            minimal.add("current_world_totals", payload.get("current_world_totals"));
        }

        if (payload.has("mining_records"))
        {
            minimal.add("mining_records", payload.get("mining_records"));
        }

        if (payload.has("daily_mining"))
        {
            minimal.add("daily_mining", payload.get("daily_mining"));
        }

        if (payload.has("current_world_block_breakdown"))
        {
            minimal.addProperty("current_world_block_breakdown", BlockBreakdownPayloads.fingerprint(payload.getAsJsonObject("current_world_block_breakdown")));
        }

        if (payload.has("server_player_block_breakdowns"))
        {
            minimal.addProperty("server_player_block_breakdowns", ServerPlayerBlockBreakdownScanner.fingerprint(payload.getAsJsonObject("server_player_block_breakdowns")));
        }

        if (payload.has("source_scan"))
        {
            minimal.add("source_scan", payload.get("source_scan"));
        }

        if (payload.has("source_leaderboard"))
        {
            minimal.add("source_leaderboard", payload.get("source_leaderboard"));
        }

        if (payload.has("source_leaderboards"))
        {
            minimal.add("source_leaderboards", payload.get("source_leaderboards"));
        }

        if (payload.has("player_total_digs"))
        {
            minimal.add("player_total_digs", payload.get("player_total_digs"));
        }

        if (payload.has("session"))
        {
            minimal.add("session", payload.get("session"));
        }

        return GSON.toJson(minimal);
    }

    private static String leaderboardFingerprint(SourceLeaderboardSnapshot snapshot)
    {
        if (snapshot == null || snapshot.isValid() == false)
        {
            return "";
        }

        List<SourceLeaderboardEntry> validEntries = snapshot.entries().stream()
                .filter(SourceLeaderboardEntry::isValid)
                .sorted(Comparator.comparingInt(SourceLeaderboardEntry::rank))
                .toList();
        JsonObject object = new JsonObject();
        object.addProperty("server_name", snapshot.serverName());
        object.addProperty("objective_title", snapshot.objectiveTitle());
        object.addProperty("total_digs", SourceLeaderboardPayloadSupport.resolveTotal(snapshot, validEntries));

        JsonArray entries = new JsonArray();
        validEntries.forEach(entry -> {
                    JsonObject row = new JsonObject();
                    row.addProperty("username", entry.username());
                    row.addProperty("rank", entry.rank());
                    row.addProperty("digs", entry.digs());
                    entries.add(row);
                });

        object.add("entries", entries);
        return GSON.toJson(object);
    }

    private static String leaderboardFingerprint(JsonObject payload)
    {
        if (payload == null)
        {
            return "";
        }

        JsonObject leaderboard = getObject(payload, "source_leaderboard");
        if (leaderboard == null && payload.has("source_leaderboards") && payload.get("source_leaderboards").isJsonArray())
        {
            JsonArray leaderboards = payload.getAsJsonArray("source_leaderboards");
            if (leaderboards.isEmpty() == false && leaderboards.get(0).isJsonObject())
            {
                leaderboard = leaderboards.get(0).getAsJsonObject();
            }
        }
        if (leaderboard == null)
        {
            return "";
        }

        JsonObject object = new JsonObject();
        object.addProperty("server_name", getString(leaderboard, "server_name", ""));
        object.addProperty("objective_title", getString(leaderboard, "objective_title", ""));
        object.addProperty("total_digs", Math.max(0L, getLong(leaderboard, "total_digs", 0L)));

        JsonArray entries = new JsonArray();
        if (leaderboard.has("entries") && leaderboard.get("entries").isJsonArray())
        {
            for (JsonElement element : leaderboard.getAsJsonArray("entries"))
            {
                if (element.isJsonObject() == false)
                {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                JsonObject row = new JsonObject();
                row.addProperty("username", getString(entry, "username", ""));
                row.addProperty("rank", Math.max(0L, getLong(entry, "rank", 0L)));
                row.addProperty("digs", Math.max(0L, getLong(entry, "digs", 0L)));
                entries.add(row);
            }
        }
        object.add("entries", entries);
        return GSON.toJson(object);
    }

    private static void debugPayloadSource(WorldSessionContext.WorldInfo worldInfo, JsonObject payload)
    {
        JsonObject world = payload.has("world") ? payload.getAsJsonObject("world") : null;
        String sourceKey = world != null && world.has("source_key") ? world.get("source_key").getAsString() : "";
        String sourceName = world != null && world.has("source_name") ? world.get("source_name").getAsString() : "";
        lastPayloadSourceKey = sourceKey;
        lastPayloadSourceName = sourceName;

        if (MmmDebugLogger.shouldLog("cloud-sync-payload-created", 5_000L) == false)
        {
            return;
        }

        MMM.LOGGER.info(
                "[MMM_DEBUG] sync-payload-created sourceName={} sourceType={} sessionActive={}",
                sourceName,
                worldInfo == null ? "unknown" : worldInfo.sourceType(),
                MiningStats.isSessionActive()
        );
    }

    private enum SyncStatus
    {
        CONNECTED,
        QUEUED,
        SYNCING,
        SYNCED,
        FAILED
    }
}
