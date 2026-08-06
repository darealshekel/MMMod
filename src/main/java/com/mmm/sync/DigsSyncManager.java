package com.mmm.sync;

import com.google.gson.JsonObject;
import com.mmm.config.Configs;
import com.mmm.storage.MiningCalendarStore;
import com.mmm.storage.WorldSessionContext;
import com.mmm.tracker.MiningStats;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public final class DigsSyncManager
{
    private static final long HUD_FAILURE_GRACE_MS = 12_000L;
    private static final long HUD_HEALTH_STALE_MS = 90_000L;
    private static final long AUTHORITATIVE_MODEL_STALE_MS = 15_000L;
    private static final long SCOREBOARD_DETECTION_INTERVAL_MS = 500L;

    private static PlayerDigsModel latestModel;
    private static boolean latestModelAuthoritative;
    private static String latestModelSourceType = "none";
    private static PersonalTotalDetector.FastTotalPlan latestFastTotalPlan = PersonalTotalDetector.FastTotalPlan.empty();
    private static volatile SyncStatus status = SyncStatus.CONNECTED;
    private static volatile long lastHealthySignalMs;

    private static volatile long debugSidebarTotal;
    private static volatile long debugTabTotal;
    private static volatile long debugChosenTotal;
    private static volatile String debugChosenSource = "none";
    private static volatile String debugSidebarSample = "";
    private static volatile String debugTabSample = "";
    private static volatile String debugSidebarObjective = "";
    private static volatile String debugTabObjective = "";
    private static volatile String debugSidebarMatchedUser = "";
    private static volatile String debugTabMatchedUser = "";
    private static volatile long debugSidebarRawScore;
    private static volatile long debugTabRawScore;
    private static volatile String debugSkipReason = "";
    private static long nextScoreboardDetectionAtMs;

    private DigsSyncManager()
    {
    }

    public static void onClientTick(long now)
    {
        clearStaleModel(now);
        MinecraftClient client = MinecraftClient.getInstance();
        refreshAuthoritativeTotalFast(client, now);
        if (now < nextScoreboardDetectionAtMs)
        {
            return;
        }
        long detectionIntervalMs = latestFastTotalPlan.isUsable()
                ? 2_000L
                : SCOREBOARD_DETECTION_INTERVAL_MS;
        nextScoreboardDetectionAtMs = now + detectionIntervalMs;

        List<ScoreboardReader.ObjectiveSnapshot> objectiveSnapshots = ScoreboardReader.readObjectives(client);
        PersonalTotalDetector.Detection detection = PersonalTotalDetector.detect(client, objectiveSnapshots);
        PlayerDigsModel parserModel = PlayerDigsParser.parse(client, objectiveSnapshots);
        TotalSelection selection = selectAuthoritativeTotal(client, detection, parserModel, now);
        applyDetectionDebug(detection, selection);

        if (selection.model() != null)
        {
            latestModel = selection.model();
            latestModelAuthoritative = selection.authoritative();
            latestModelSourceType = selection.sourceType();
            if (latestModelAuthoritative)
            {
                latestFastTotalPlan = PersonalTotalDetector.buildFastTotalPlan(
                        client,
                        selection.sourceType(),
                        selection.objectiveTitle());
                MiningStats.bootstrapSourceTotalFromScoreboard(
                        latestModel.totalDigs(),
                        latestModel.server(),
                        selection.objectiveTitle(),
                        true,
                        now);
            }
            else
            {
                latestFastTotalPlan = PersonalTotalDetector.FastTotalPlan.empty();
            }
            if (status != SyncStatus.SYNCED)
            {
                status = SyncStatus.CONNECTED;
            }
            touchHealthy();
        }

        // CloudSyncManager owns transport so one complete payload claims the
        // server-enforced daily source-sync slot. This class only detects totals.
    }

    public static void onSyncScoreboardSelectionChanged()
    {
        latestModel = null;
        latestModelAuthoritative = false;
        latestModelSourceType = "none";
        latestFastTotalPlan = PersonalTotalDetector.FastTotalPlan.empty();
        nextScoreboardDetectionAtMs = 0L;
    }

    static void onQueued(JsonObject payload)
    {
        status = SyncStatus.QUEUED;
    }

    static void onQueuePreparing()
    {
        status = SyncStatus.QUEUED;
    }

    static void onQueueUploading()
    {
        status = SyncStatus.QUEUED;
    }

    static void onQueueWaitingForResponse()
    {
        status = SyncStatus.QUEUED;
    }
    static void onQueueSuccess(JsonObject payload, String responseBody)
    {
        status = SyncStatus.SYNCED;
        touchHealthy();
        CloudSyncManager.applySuccessfulSyncResponse(payload, responseBody);
        SyncDeltaStore.markPayloadSynced(payload);
        if (responseAcknowledgesDailyMining(responseBody))
        {
            MiningCalendarStore.markPayloadSynced(payload);
        }
    }

    static void onQueueRetry(String detail, long nextRetryAtMs)
    {
        status = SyncStatus.FAILED;
    }

    static void onQueueDropped(String detail)
    {
        status = SyncStatus.FAILED;
    }

    public static void requestScheduledSync(String reason)
    {
        // The 1.21 branch sends one complete source snapshot through CloudSyncManager.
        // Keeping totals and sessions in that payload prevents partial-source overwrites.
        CloudSyncManager.requestScheduledSync(reason);
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
        int pending = snapshot.countFor(SyncItemType.PLAYER_TOTAL_DIGS);
        long recentHealthyMs = Math.max(lastHealthySignalMs, snapshot.lastSuccessfulSyncAtMs());

        if (snapshot.flushActive() && pending > 0)
        {
            return true;
        }

        if (status == SyncStatus.FAILED)
        {
            return recentHealthyMs > 0L && now - recentHealthyMs <= HUD_FAILURE_GRACE_MS;
        }

        if (pending > 0 && recentHealthyMs > 0L && now - recentHealthyMs > HUD_FAILURE_GRACE_MS)
        {
            return false;
        }

        return recentHealthyMs > 0L && now - recentHealthyMs <= HUD_HEALTH_STALE_MS;
    }
    private static TotalSelection selectAuthoritativeTotal(MinecraftClient client,
                                                           PersonalTotalDetector.Detection detection,
                                                           PlayerDigsModel parserModel,
                                                           long now)
    {
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        String sourceName = ScoreboardSourceResolver.displayName(worldInfo != null ? worldInfo.displayName() : "", worldInfo);

        if (SyncScoreboardSelector.hasManualSelection())
        {
            net.minecraft.scoreboard.ScoreboardObjective selected = SyncScoreboardSelector.resolveSelectedObjective(client);
            long selectedTotal = SyncScoreboardSelector.readSelectedPlayerTotal(client);
            if (selected == null || selectedTotal <= 0L)
            {
                return new TotalSelection(sourceName, null, "none", "", "selected-scoreboard-unavailable", false);
            }

            String objectiveTitle = selected.getDisplayName().getString();
            PlayerDigsModel model = new PlayerDigsModel(
                    resolveUsername(client, parserModel),
                    selectedTotal,
                    now,
                    sourceName,
                    objectiveTitle);
            return new TotalSelection(sourceName, model, "parser", objectiveTitle, "selected-scoreboard", true);
        }

        long tabTotal = Math.max(0L, detection.tabTotal());
        long sidebarTotal = Math.max(0L, detection.sidebarTotal());
        long parserTotal = parserModel != null && parserModel.isValid() ? Math.max(0L, parserModel.totalDigs()) : 0L;
        long toolUsageTotal = Math.max(0L, detection.toolUsageTotal());
        long cachedTotal = Math.max(0L, MiningStats.getCurrentSourceTotalMined());

        // A dedicated total-digs objective is authoritative. Tool counters are
        // the fallback only when the server does not expose such an objective.
        if (ScoreboardParser.isToolUsesObjective(detection.tabObjectiveTitle()))
        {
            tabTotal = 0L;
        }
        if (ScoreboardParser.isToolUsesObjective(detection.sidebarObjectiveTitle()))
        {
            sidebarTotal = 0L;
        }
        if (parserModel != null && ScoreboardParser.isToolUsesObjective(parserModel.objectiveTitle()))
        {
            parserTotal = 0L;
        }

        Candidate chosen = chooseBestCandidate(tabTotal, sidebarTotal, parserTotal, toolUsageTotal, cachedTotal);
        if (!chosen.valid())
        {
            return new TotalSelection(sourceName, null, "none", "", chosen.reason(), false);
        }

        String username = resolveUsername(client, parserModel);
        String objectiveTitle = resolveObjectiveTitle(chosen, parserModel, detection);
        PlayerDigsModel model = new PlayerDigsModel(username, chosen.total(), now, sourceName, objectiveTitle);
        return new TotalSelection(sourceName, model, chosen.sourceType(), objectiveTitle, chosen.reason(), isAuthoritativeCandidate(chosen));
    }

    private static boolean isAuthoritativeCandidate(Candidate candidate)
    {
        return candidate != null
                && candidate.valid()
                && "cached".equals(candidate.sourceType()) == false;
    }

    private static Candidate chooseBestCandidate(long tabTotal,
                                                 long sidebarTotal,
                                                 long parserTotal,
                                                 long toolUsageTotal,
                                                 long cachedTotal)
    {
        // Reject tiny ambiguous totals when stronger evidence exists.
        long strongest = Math.max(
                Math.max(tabTotal, sidebarTotal),
                Math.max(Math.max(parserTotal, toolUsageTotal), cachedTotal));

        Candidate tab = validate("tab", tabTotal, strongest);
        Candidate sidebar = validate("sidebar", sidebarTotal, strongest);
        Candidate parser = validate("parser", parserTotal, strongest);
        Candidate toolUsage = validate("tool-uses", toolUsageTotal, strongest);
        Candidate cached = validate("cached", cachedTotal, strongest);

        // Keep one deterministic authority order. Choosing the numerically largest
        // objective made World Total alternate when servers exposed several mining scores.
        Candidate dedicatedTotal = firstValid(tab, sidebar, parser);
        if (dedicatedTotal.valid()) return dedicatedTotal;
        if (toolUsage.valid()) return toolUsage;
        if (cached.valid()) return cached;

        return new Candidate("none", 0L, false, "no-valid-total");
    }

    private static Candidate firstValid(Candidate... candidates)
    {
        for (Candidate candidate : candidates)
        {
            if (candidate.valid())
            {
                return candidate;
            }
        }
        return new Candidate("none", 0L, false, "no-valid-live-total");
    }

    private static Candidate validate(String source, long total, long strongest)
    {
        if (total <= 0L)
        {
            return new Candidate(source, 0L, false, "missing");
        }

        if (total < 50L && strongest >= 1_000L)
        {
            return new Candidate(source, total, false, "tiny-rejected");
        }

        return new Candidate(source, total, true, "accepted");
    }

    private static String resolveUsername(MinecraftClient client, PlayerDigsModel parsed)
    {
        if (client != null && client.player != null)
        {
            return client.player.getGameProfile().getName();
        }
        if (parsed != null && parsed.username() != null && parsed.username().isBlank() == false)
        {
            return parsed.username();
        }
        return "Player";
    }

    private static String resolveObjectiveTitle(Candidate chosen, PlayerDigsModel parsed, PersonalTotalDetector.Detection detection)
    {
        if (chosen != null)
        {
            String selected = switch (chosen.sourceType())
            {
                case "tab" -> detection.tabObjectiveTitle();
                case "sidebar" -> detection.sidebarObjectiveTitle();
                case "parser" -> parsed == null ? "" : parsed.objectiveTitle();
                case "tool-uses" -> detection.toolUsageObjectiveTitle();
                default -> "";
            };
            if (selected != null && selected.isBlank() == false)
            {
                return selected;
            }
        }
        return "Scoreboard";
    }

    private static boolean responseAcknowledgesDailyMining(String responseBody)
    {
        if (responseBody == null || responseBody.isBlank())
        {
            return false;
        }
        try
        {
            JsonObject response = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            return response.has("daily_mining_synced") && response.get("daily_mining_synced").getAsBoolean();
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private static void refreshAuthoritativeTotalFast(MinecraftClient client, long now)
    {
        PlayerDigsModel current = latestModel;
        if (current == null || latestModelAuthoritative == false || "none".equals(latestModelSourceType))
        {
            return;
        }

        long total = PersonalTotalDetector.readValidatedTotal(client, latestFastTotalPlan);
        if (total <= 0L || total == current.totalDigs())
        {
            return;
        }

        latestModel = new PlayerDigsModel(current.username(), total, now, current.server(), current.objectiveTitle());
        debugChosenTotal = total;
        MiningStats.bootstrapSourceTotalFromScoreboard(
                total,
                current.server(),
                current.objectiveTitle(),
                true,
                now);
    }

    private static void clearStaleModel(long now)
    {
        if (latestModel == null)
        {
            return;
        }

        if (now - latestModel.capturedAtMs() > AUTHORITATIVE_MODEL_STALE_MS)
        {
            latestModel = null;
            latestModelAuthoritative = false;
            latestModelSourceType = "none";
            latestFastTotalPlan = PersonalTotalDetector.FastTotalPlan.empty();
        }
    }

    private static void applyDetectionDebug(PersonalTotalDetector.Detection detection, TotalSelection selection)
    {
        debugSidebarTotal = detection.sidebarTotal();
        debugTabTotal = detection.tabTotal();
        debugChosenTotal = selection.model() == null ? 0L : selection.model().totalDigs();
        debugChosenSource = selection.model() == null ? "none" : selection.sourceType();
        debugSidebarObjective = detection.sidebarObjectiveTitle();
        debugTabObjective = detection.tabObjectiveTitle();
        debugSidebarMatchedUser = detection.sidebarMatchedUsername();
        debugTabMatchedUser = detection.tabMatchedUsername();
        debugSidebarRawScore = detection.sidebarRawScore();
        debugTabRawScore = detection.tabRawScore();
        debugSidebarSample = detection.sidebarRenderedText();
        debugTabSample = detection.tabRenderedText();
        debugSkipReason = selection.model() == null ? selection.reason() : "";
    }

    public static String getStatusLabel()
    {
        PendingSyncQueue.Snapshot snapshot = SyncQueueManager.getSnapshot();
        if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() == false)
        {
            return "Disabled";
        }
        if (Configs.cloudSyncEndpoint == null || Configs.cloudSyncEndpoint.isBlank())
        {
            return "Unavailable";
        }
        if (snapshot.countFor(SyncItemType.WEBSITE_LINK_CLAIM) > 0)
        {
            return snapshot.flushActive() ? "Linking" : "Link queued";
        }
        if (WebsiteLinkManager.hasPersistedLink() == false)
        {
            return "Not authenticated";
        }
        if (isCurrentPlayerMismatch())
        {
            return "Wrong account";
        }

        int pending = snapshot.countFor(SyncItemType.PLAYER_TOTAL_DIGS);
        if (snapshot.flushActive() && pending > 0)
        {
            return "Syncing";
        }
        if (pending > 0)
        {
            return "Queued";
        }

        return switch (status)
        {
            case SYNCED -> "Synced";
            case FAILED -> "Retrying";
            default -> "Connected";
        };
    }

    private static boolean isCurrentPlayerMismatch()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && client.player != null
                && WebsiteLinkManager.isCurrentPlayerLinked() == false;
    }
    public static void resetForDisconnect()
    {
        latestModel = null;
        latestModelAuthoritative = false;
        latestModelSourceType = "none";
        latestFastTotalPlan = PersonalTotalDetector.FastTotalPlan.empty();
        status = SyncStatus.CONNECTED;
        nextScoreboardDetectionAtMs = 0L;
        clearDebug();
    }

    public static void resetForWorldChange(String worldId)
    {
        resetForDisconnect();
    }

    public static boolean hasAuthoritativeTotalDigs()
    {
        if (latestModel == null || latestModel.isValid() == false || latestModelAuthoritative == false)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - latestModel.capturedAtMs() > AUTHORITATIVE_MODEL_STALE_MS)
        {
            return false;
        }

        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        String currentSourceName = ScoreboardSourceResolver.displayName(worldInfo != null ? worldInfo.displayName() : "", worldInfo);
        return currentSourceName.equalsIgnoreCase(latestModel.server());
    }

    public static long getDebugSidebarTotal() { return debugSidebarTotal; }
    public static long getDebugTabTotal() { return debugTabTotal; }
    public static long getDebugChosenTotal() { return debugChosenTotal; }
    public static String getDebugChosenSource() { return debugChosenSource; }
    public static String getDebugSidebarSample() { return debugSidebarSample; }
    public static String getDebugTabSample() { return debugTabSample; }
    public static String getDebugSkipReason() { return debugSkipReason; }
    public static String getDebugSidebarObjective() { return debugSidebarObjective; }
    public static String getDebugTabObjective() { return debugTabObjective; }
    public static String getDebugSidebarMatchedUser() { return debugSidebarMatchedUser; }
    public static String getDebugTabMatchedUser() { return debugTabMatchedUser; }
    public static long getDebugSidebarRawScore() { return debugSidebarRawScore; }
    public static long getDebugTabRawScore() { return debugTabRawScore; }

    private static void clearDebug()
    {
        debugSidebarTotal = 0L;
        debugTabTotal = 0L;
        debugChosenTotal = 0L;
        debugChosenSource = "none";
        debugSidebarSample = "";
        debugTabSample = "";
        debugSidebarObjective = "";
        debugTabObjective = "";
        debugSidebarMatchedUser = "";
        debugTabMatchedUser = "";
        debugSidebarRawScore = 0L;
        debugTabRawScore = 0L;
        debugSkipReason = "";
    }

    private static void touchHealthy()
    {
        lastHealthySignalMs = System.currentTimeMillis();
    }

    private enum SyncStatus
    {
        CONNECTED,
        QUEUED,
        SYNCED,
        FAILED
    }

    private record Candidate(String sourceType, long total, boolean valid, String reason)
    {
    }

    private record TotalSelection(String sourceName, PlayerDigsModel model, String sourceType, String objectiveTitle, String reason, boolean authoritative)
    {
    }
}
