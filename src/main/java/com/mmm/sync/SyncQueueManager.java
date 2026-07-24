package com.mmm.sync;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.MMM;
import com.mmm.Reference;
import com.mmm.config.Configs;
import com.mmm.util.MmmDebugLogger;

import net.fabricmc.loader.api.FabricLoader;

public final class SyncQueueManager
{
    private static final String LOG_PREFIX = "[MMM_SYNC]";
    private static final long PERIODIC_FLUSH_INTERVAL_MS = 5_000L;
    private static final long DISABLED_SYNC_LOG_INTERVAL_MS = 10_000L;
    private static final long SEND_SUCCESS_LOG_INTERVAL_MS = 10_000L;
    private static final long ERROR_PARSE_DEBUG_LOG_INTERVAL_MS = 30_000L;
    private static final String LINK_ENDPOINT = "https://www.mmmaniacs.com/api/auth/link-code/claim";

    private static PendingSyncQueue queue;
    private static long lastPeriodicFlushMs;

    private SyncQueueManager()
    {
    }

    public static synchronized void initialize()
    {
        if (queue != null)
        {
            return;
        }

        Path queuePath = FabricLoader.getInstance().getConfigDir().resolve(Reference.STORAGE_ID + "-sync-queue.json");
        queue = new PendingSyncQueue(
                queuePath,
                SyncQueueManager::send,
                new QueueListener(),
                (java.util.function.Predicate<QueuedSyncItem>) SyncQueueManager::isSyncEnabledFor);
        queue.initialize();
        MMM.LOGGER.info("{} queue-initialized endpoint={} linkEndpoint={}",
                LOG_PREFIX,
                Configs.cloudSyncEndpoint,
                LINK_ENDPOINT);
        forceFlush("mod startup");
    }

    public static void onClientTick(long now)
    {
        if (queue == null)
        {
            initialize();
        }

        if (now - lastPeriodicFlushMs >= PERIODIC_FLUSH_INTERVAL_MS)
        {
            lastPeriodicFlushMs = now;
            requestFlush("periodic timer");
        }
    }

    public static void requestFlush(String reason)
    {
        if (queue == null)
        {
            MMM.LOGGER.info("{} send-skipped-flush-guard reason={} detail=queue_not_initialized",
                    LOG_PREFIX,
                    reason == null || reason.isBlank() ? "manual" : reason);
            return;
        }

        queue.requestFlush(reason);
    }

    public static void forceFlush(String reason)
    {
        if (queue == null)
        {
            return;
        }

        queue.forceFlush(reason);
    }

    public static void enqueueCloudLiveState(JsonObject payload)
    {
        enqueueCloudLiveState(payload, "live state");
    }

    public static void enqueueCloudLiveState(JsonObject payload, String triggerReason)
    {
        if (isSyncEnabledFor(SyncItemType.CLOUD_LIVE_STATE) == false)
        {
            logEnqueueSkippedDisabled(SyncItemType.CLOUD_LIVE_STATE);
            return;
        }

        initialize();
        queue.enqueue(SyncItemType.CLOUD_LIVE_STATE, "cloud-live-state", payload, true, triggerReason);
        requestFlush("enqueue cloud live state");
    }

    public static void enqueueCloudFinishedSession(String sessionKey, JsonObject payload)
    {
        enqueueCloudFinishedSession(sessionKey, payload, "finished session");
    }

    public static void enqueueCloudFinishedSession(String sessionKey, JsonObject payload, String triggerReason)
    {
        if (isSyncEnabledFor(SyncItemType.CLOUD_FINISHED_SESSION) == false)
        {
            logEnqueueSkippedDisabled(SyncItemType.CLOUD_FINISHED_SESSION);
            return;
        }

        initialize();
        queue.enqueue(SyncItemType.CLOUD_FINISHED_SESSION, sessionKey == null ? "" : sessionKey, payload, true, triggerReason);
        requestFlush("enqueue finished session");
    }

    public static void enqueuePlayerTotalDigs(String dedupeKey, JsonObject payload)
    {
        enqueuePlayerTotalDigs(dedupeKey, payload, "total digs");
    }

    public static void enqueuePlayerTotalDigs(String dedupeKey, JsonObject payload, String triggerReason)
    {
        if (isSyncEnabledFor(SyncItemType.PLAYER_TOTAL_DIGS) == false)
        {
            logEnqueueSkippedDisabled(SyncItemType.PLAYER_TOTAL_DIGS);
            return;
        }

        initialize();
        queue.enqueue(SyncItemType.PLAYER_TOTAL_DIGS, dedupeKey == null ? "" : dedupeKey, payload, true, triggerReason);
        requestFlush("enqueue player total digs");
    }

    public static void enqueueWebsiteLinkClaim(String dedupeKey, JsonObject payload)
    {
        initialize();
        queue.enqueue(SyncItemType.WEBSITE_LINK_CLAIM, dedupeKey == null ? "" : dedupeKey, payload, true, "website link claim");
        requestFlush("enqueue website link");
    }

    public static PendingSyncQueue.Snapshot getSnapshot()
    {
        initialize();
        return queue.snapshot();
    }

    public static long getNextAttemptAtMs(SyncItemType... types)
    {
        initialize();
        return queue.nextAttemptAtMs(types);
    }

    private static SyncSendResult send(QueuedSyncItem item)
    {
        try
        {
            if (item == null || item.isValid() == false)
            {
                MMM.LOGGER.warn("{} send-skipped-item-invalid reason=invalid_queue_item", LOG_PREFIX);
                return SyncSendResult.drop(-1, "Invalid queue item.", "");
            }

            if (item.type == SyncItemType.PLAYER_TOTAL_DIGS)
            {
                return SyncSendResult.drop(410, "Legacy partial sync replaced by the complete daily sync.", "");
            }

            if (isSyncEnabledFor(item.type) == false)
            {
                MmmDebugLogger.info("syncqueue-send-disabled-" + item.type.name(), DISABLED_SYNC_LOG_INTERVAL_MS,
                        "{} send-skipped-disabled id={} type={}",
                        LOG_PREFIX,
                        item.id,
                        item.type);
                return SyncSendResult.retry(-1, "Sync disabled by config.", "");
            }

            String endpoint = resolveEndpoint(item.type);
            if (endpoint == null || endpoint.isBlank())
            {
                MMM.LOGGER.warn("{} send-skipped-no-endpoint id={} type={}", LOG_PREFIX, item.id, item.type);
                return SyncSendResult.retry(-1, "No sync endpoint configured.", "");
            }

            boolean hasClientId = item.payload != null && item.payload.has("client_id")
                    && item.payload.get("client_id").isJsonPrimitive()
                    && item.payload.get("client_id").getAsString().isBlank() == false;
            boolean hasUsername = item.payload != null && item.payload.has("username")
                    && item.payload.get("username").isJsonPrimitive()
                    && item.payload.get("username").getAsString().isBlank() == false;
            boolean hasSessionToken = item.payload != null && item.payload.has("minecraft_uuid")
                    && item.payload.get("minecraft_uuid").isJsonPrimitive()
                    && item.payload.get("minecraft_uuid").getAsString().isBlank() == false;
            boolean hasLinkedIdentity = Configs.websiteLinkedMinecraftUuid != null
                    && Configs.websiteLinkedMinecraftUuid.isBlank() == false;
            boolean hasClientSyncToken = Configs.websiteSyncToken != null
                    && Configs.websiteSyncToken.isBlank() == false;

            if (hasClientId == false || hasUsername == false)
            {
                MMM.LOGGER.warn(
                        "{} send-skipped-no-auth id={} type={} hasClientId={} hasUsername={}",
                        LOG_PREFIX,
                        item.id,
                        item.type,
                        hasClientId,
                        hasUsername
                );
                return SyncSendResult.drop(400, "Missing client_id/username.", "");
            }

            String secret = null;
            if (hasSessionToken == false)
            {
                MMM.LOGGER.warn("{} send-skipped-no-session-token id={} type={} note=payload_missing_minecraft_uuid",
                        LOG_PREFIX,
                        item.id,
                        item.type);
            }
            if (hasLinkedIdentity == false)
            {
                MMM.LOGGER.warn("{} send-skipped-no-linked-identity id={} type={} note=config_not_linked",
                        LOG_PREFIX,
                        item.id,
                        item.type);
            }

            if (item.type != SyncItemType.WEBSITE_LINK_CLAIM && (hasLinkedIdentity == false || hasClientSyncToken == false))
            {
                return SyncSendResult.drop(401, "Link MMMod to the website before syncing.", "");
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("x-mmm-sync-item-id", item.id);
            headers.put("x-mmm-sync-item-type", item.type.name());
            if (item.type != SyncItemType.WEBSITE_LINK_CLAIM)
            {
                headers.put("x-mmm-client-sync-token", Configs.websiteSyncToken);
            }

            String requestBody = item.payload.toString();
            long requestStartedAtMs = System.currentTimeMillis();
            notifyUploading(item);
            MmmDebugLogger.info(
                    "syncqueue-request-" + item.type.name(),
                    SEND_SUCCESS_LOG_INTERVAL_MS,
                    "{} request-sent id={} type={} trigger={} endpoint={} payloadBytes={} payloadRecords={} payloadSummary={}",
                    LOG_PREFIX,
                    item.id,
                    item.type,
                    item.triggerReason,
                    endpoint,
                    requestBody.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                    countPayloadRecords(item.payload),
                    summarizePayload(item.payload));
            notifyWaitingForResponse(item);
            HttpResponse<String> response = ApiClient.postJsonBlocking(endpoint, secret, requestBody, headers);
            long elapsedMs = Math.max(0L, System.currentTimeMillis() - requestStartedAtMs);
            int statusCode = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            if (statusCode >= 200 && statusCode < 300)
            {
                MmmDebugLogger.info(
                        "syncqueue-response-" + item.type.name(),
                        SEND_SUCCESS_LOG_INTERVAL_MS,
                        "{} response-received id={} type={} status={} elapsedMs={} response={}",
                        LOG_PREFIX,
                        item.id,
                        item.type,
                        statusCode,
                        elapsedMs,
                        summarizeResponse(body));
            }
            else
            {
                MMM.LOGGER.warn("{} response-received id={} type={} status={} elapsedMs={} response={}",
                        LOG_PREFIX,
                        item.id,
                        item.type,
                        statusCode,
                        elapsedMs,
                        summarizeResponse(body));
            }

            if (statusCode >= 200 && statusCode < 300)
            {
                return SyncSendResult.success(statusCode, body);
            }

            if (item.type == SyncItemType.WEBSITE_LINK_CLAIM
                    && (isPermanentLinkFailure(statusCode, body) || statusCode == 401 || statusCode == 403))
            {
                return SyncSendResult.drop(statusCode, extractError(body, "Could not claim link code."), body);
            }
            if (item.type != SyncItemType.WEBSITE_LINK_CLAIM && (statusCode == 401 || statusCode == 403))
            {
                String rejectionDetail = extractError(body, "Website link expired or was rejected. Generate a new mod link code.");
                if (statusCode == 401 || isWebsiteLinkRejection(rejectionDetail))
                {
                    WebsiteLinkManager.invalidatePersistedLink(rejectionDetail);
                }
                return SyncSendResult.drop(statusCode, rejectionDetail, body);
            }

            String detail = extractError(body, "HTTP " + statusCode);
            if (isPermanentSyncFailure(statusCode, detail))
            {
                return SyncSendResult.drop(statusCode, detail, body);
            }

            return SyncSendResult.retry(statusCode, detail, body);
        }
        catch (Exception exception)
        {
            String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            MMM.LOGGER.warn("{} request-failed id={} type={} error={}", LOG_PREFIX, item.id, item.type, detail);
            return SyncSendResult.retry(-1, detail, "");
        }
    }

    private static String resolveEndpoint(SyncItemType type)
    {
        return switch (type)
        {
            case CLOUD_LIVE_STATE, CLOUD_FINISHED_SESSION, PLAYER_TOTAL_DIGS -> Configs.cloudSyncEndpoint;
            case WEBSITE_LINK_CLAIM -> LINK_ENDPOINT;
        };
    }

    public static boolean isMiningSyncAuthenticated()
    {
        return WebsiteLinkManager.hasPersistedLink()
                && WebsiteLinkManager.isCurrentPlayerLinked();
    }

    public static void discardMiningItemsForOtherPlayers(String linkedUuid)
    {
        if (linkedUuid == null || linkedUuid.isBlank())
        {
            return;
        }

        initialize();
        int removed = queue.removeMatching(item -> isMiningItem(item.type)
                && payloadUuid(item.payload).equalsIgnoreCase(linkedUuid) == false);
        if (removed > 0)
        {
            MMM.LOGGER.info("{} stale-account-items-removed count={}", LOG_PREFIX, removed);
        }
    }

    private static boolean isSyncEnabledFor(QueuedSyncItem item)
    {
        if (item == null || isSyncEnabledFor(item.type) == false)
        {
            return false;
        }

        if (item.type == SyncItemType.CLOUD_LIVE_STATE || item.type == SyncItemType.CLOUD_FINISHED_SESSION)
        {
            return CloudSyncManager.isCurrentContextPayloadPreparedForSync();
        }
        return true;
    }

    private static boolean isSyncEnabledFor(SyncItemType type)
    {
        if (type == SyncItemType.WEBSITE_LINK_CLAIM)
        {
            return true;
        }

        if (Configs.Generic.WEBSITE_SYNC_ENABLED.getBooleanValue() == false)
        {
            return false;
        }

        return true;
    }

    private static boolean isMiningItem(SyncItemType type)
    {
        return type == SyncItemType.CLOUD_LIVE_STATE
                || type == SyncItemType.CLOUD_FINISHED_SESSION
                || type == SyncItemType.PLAYER_TOTAL_DIGS;
    }

    private static String payloadUuid(JsonObject payload)
    {
        if (payload == null
                || payload.has("minecraft_uuid") == false
                || payload.get("minecraft_uuid").isJsonPrimitive() == false)
        {
            return "";
        }
        return payload.get("minecraft_uuid").getAsString().trim();
    }

    private static void notifyUploading(QueuedSyncItem item)
    {
        switch (item.type)
        {
            case CLOUD_LIVE_STATE, CLOUD_FINISHED_SESSION -> CloudSyncManager.onQueueUploading(item.type);
            case PLAYER_TOTAL_DIGS -> DigsSyncManager.onQueueUploading();
            case WEBSITE_LINK_CLAIM -> { }
        }
    }

    private static void notifyWaitingForResponse(QueuedSyncItem item)
    {
        switch (item.type)
        {
            case CLOUD_LIVE_STATE, CLOUD_FINISHED_SESSION -> CloudSyncManager.onQueueWaitingForResponse(item.type);
            case PLAYER_TOTAL_DIGS -> DigsSyncManager.onQueueWaitingForResponse();
            case WEBSITE_LINK_CLAIM -> { }
        }
    }

    private static void logEnqueueSkippedDisabled(SyncItemType type)    {
        MmmDebugLogger.info("syncqueue-enqueue-disabled-" + type.name(), DISABLED_SYNC_LOG_INTERVAL_MS,
                "{} enqueue-skipped-disabled type={}",
                LOG_PREFIX,
                type);
    }

    private static boolean isWebsiteLinkRejection(String detail)
    {
        String normalized = detail == null ? "" : detail.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("website link")
                || normalized.contains("sync token")
                || normalized.contains("another installation")
                || normalized.contains("unauthorized");
    }

    private static boolean isPermanentLinkFailure(int statusCode, String body)
    {
        if (statusCode != 400 && statusCode != 404 && statusCode != 410)
        {
            return false;
        }

        String error = extractError(body, "");
        String normalized = error.toLowerCase(Locale.ROOT);
        return normalized.contains("invalid") || normalized.contains("expired") || normalized.contains("already");
    }

    private static boolean isPermanentSyncFailure(int statusCode, String detail)
    {
        if (statusCode != 400)
        {
            return false;
        }

        String normalized = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        return normalized.contains("invalid client")
                || normalized.contains("invalid username")
                || normalized.contains("invalid session")
                || normalized.contains("too many sessions")
                || normalized.contains("too many projects")
                || normalized.contains("too many block breakdown")
                || normalized.contains("too many accepted leaderboard")
                || normalized.contains("too many source scan");
    }

    private static String extractError(String body, String fallback)
    {
        try
        {
            JsonObject object = JsonParser.parseString(body).getAsJsonObject();
            if (object.has("error") && object.get("error").isJsonPrimitive())
            {
                return object.get("error").getAsString();
            }
        }
        catch (Exception e)
        {
            MmmDebugLogger.debug(
                    "syncqueue-error-response-parse",
                    ERROR_PARSE_DEBUG_LOG_INTERVAL_MS,
                    "{} failed to parse sync error response bodyLength={} error={}",
                    LOG_PREFIX,
                    body == null ? 0 : body.length(),
                    e.getMessage());
        }

        return fallback;
    }

    private static String summarizePayload(JsonObject payload)
    {
        if (payload == null)
        {
            return "null";
        }

        String username = payload.has("username") && payload.get("username").isJsonPrimitive()
                ? payload.get("username").getAsString()
                : "";
        String source = "";
        long total = -1L;
        if (payload.has("world") && payload.get("world").isJsonObject())
        {
            JsonObject world = payload.getAsJsonObject("world");
            if (world.has("display_name") && world.get("display_name").isJsonPrimitive())
            {
                source = world.get("display_name").getAsString();
            }
            else if (world.has("key") && world.get("key").isJsonPrimitive())
            {
                source = world.get("key").getAsString();
            }
        }
        if (payload.has("current_world_totals") && payload.get("current_world_totals").isJsonObject())
        {
            JsonObject totals = payload.getAsJsonObject("current_world_totals");
            if (totals.has("total_blocks") && totals.get("total_blocks").isJsonPrimitive())
            {
                total = totals.get("total_blocks").getAsLong();
            }
        }
        else if (payload.has("player_total_digs") && payload.get("player_total_digs").isJsonObject())
        {
            JsonObject digs = payload.getAsJsonObject("player_total_digs");
            if (digs.has("total_digs") && digs.get("total_digs").isJsonPrimitive())
            {
                total = digs.get("total_digs").getAsLong();
            }
            if (source.isBlank() && digs.has("server") && digs.get("server").isJsonPrimitive())
            {
                source = digs.get("server").getAsString();
            }
        }

        return "username=" + username + " source=" + source + " total=" + total;
    }

    private static int countPayloadRecords(JsonObject payload)
    {
        return payload == null ? 0 : countArrayElements(payload);
    }

    private static int countArrayElements(com.google.gson.JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return 0;
        }
        if (element.isJsonArray())
        {
            int count = element.getAsJsonArray().size();
            for (com.google.gson.JsonElement child : element.getAsJsonArray())
            {
                count += countArrayElements(child);
            }
            return count;
        }
        if (element.isJsonObject())
        {
            int count = 0;
            for (Map.Entry<String, com.google.gson.JsonElement> entry : element.getAsJsonObject().entrySet())
            {
                count += countArrayElements(entry.getValue());
            }
            return count;
        }
        return 0;
    }

    private static String summarizeResponse(String body)
    {
        if (body == null || body.isBlank())
        {
            return "<empty>";
        }

        try
        {
            JsonObject source = JsonParser.parseString(body).getAsJsonObject();
            JsonObject safe = new JsonObject();
            for (String key : new String[] {"ok", "status", "code", "synced", "daily_mining_synced", "error", "message"})
            {
                if (source.has(key) && source.get(key).isJsonPrimitive())
                {
                    safe.add(key, source.get(key));
                }
            }
            return safe.size() == 0 ? "<json response>" : safe.toString();
        }
        catch (Exception ignored)
        {
            return "<non-json response length=" + body.length() + ">";
        }
    }

    private static final class QueueListener implements PendingSyncQueue.Listener
    {
        @Override
        public void onLoaded(PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.info("{} queue-loaded size={} lastSuccess={} flushActive={}",
                    LOG_PREFIX,
                    snapshot.queueSize(),
                    PendingSyncQueue.formatInstant(snapshot.lastSuccessfulSyncAtMs()),
                    snapshot.flushActive());
        }

        @Override
        public void onLoadFailed(String detail)
        {
            MMM.LOGGER.warn("{} queue-load-failed detail={}", LOG_PREFIX, detail);
        }

        @Override
        public void onItemQueued(QueuedSyncItem item, boolean replaced, PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.info("{} item-enqueued id={} type={} trigger={} replaced={} queueSize={}",
                    LOG_PREFIX,
                    item.id,
                    item.type,
                    item.triggerReason,
                    replaced,
                    snapshot.queueSize());
            notifyQueued(item);
        }

        @Override
        public void onItemAttemptStarted(QueuedSyncItem item, PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.info("{} item-attempt-started id={} type={} trigger={} retryCount={} queueSize={}",
                    LOG_PREFIX,
                    item.id,
                    item.type,
                    item.triggerReason,
                    item.retryCount,
                    snapshot.queueSize());
            switch (item.type)
            {
                case CLOUD_LIVE_STATE, CLOUD_FINISHED_SESSION -> CloudSyncManager.onQueuePreparing(item.type);
                case PLAYER_TOTAL_DIGS -> DigsSyncManager.onQueuePreparing();
                case WEBSITE_LINK_CLAIM -> { }
            }
        }

        @Override
        public void onFlushStarted(String reason, PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.info("{} flush-started reason={} queueSize={} lastSuccess={}",
                    LOG_PREFIX,
                    reason,
                    snapshot.queueSize(),
                    PendingSyncQueue.formatInstant(snapshot.lastSuccessfulSyncAtMs()));
        }

        @Override
        public void onFlushFinished(String reason, PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.info("{} flush-finished reason={} queueSize={} flushActive={}",
                    LOG_PREFIX,
                    reason,
                    snapshot.queueSize(),
                    snapshot.flushActive());
        }

        @Override
        public void onItemSucceeded(QueuedSyncItem item, SyncSendResult result, PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.info("{} item-removed-success id={} type={} trigger={} status={} queueSize={}",
                    LOG_PREFIX,
                    item.id,
                    item.type,
                    item.triggerReason,
                    result.statusCode(),
                    snapshot.queueSize());
            notifySuccess(item, result);
        }

        @Override
        public void onRetryScheduled(QueuedSyncItem item, SyncSendResult result, long nextRetryAtMs, PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.warn("{} item-retained-retry id={} type={} trigger={} status={} retryCount={} nextRetry={} queueSize={} detail={}",
                    LOG_PREFIX,
                    item.id,
                    item.type,
                    item.triggerReason,
                    result.statusCode(),
                    item.retryCount,
                    PendingSyncQueue.formatInstant(nextRetryAtMs),
                    snapshot.queueSize(),
                    result.detail());
            notifyRetry(item, result, nextRetryAtMs);
        }

        @Override
        public void onItemDropped(QueuedSyncItem item, SyncSendResult result, PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.warn("{} item-dropped id={} type={} trigger={} status={} queueSize={} detail={}",
                    LOG_PREFIX,
                    item.id,
                    item.type,
                    item.triggerReason,
                    result.statusCode(),
                    snapshot.queueSize(),
                    result.detail());
            notifyDropped(item, result);
        }

        @Override
        public void onPersistenceFailed(String detail, PendingSyncQueue.Snapshot snapshot)
        {
            MMM.LOGGER.warn("{} queue-persist-failed queueSize={} detail={}",
                    LOG_PREFIX,
                    snapshot.queueSize(),
                    detail);
        }

        private void notifyQueued(QueuedSyncItem item)
        {
            switch (item.type)
            {
                case CLOUD_LIVE_STATE, CLOUD_FINISHED_SESSION -> CloudSyncManager.onQueued(item.type, item.payload);
                case PLAYER_TOTAL_DIGS -> DigsSyncManager.onQueued(item.payload);
                case WEBSITE_LINK_CLAIM -> WebsiteLinkManager.onQueued(item.payload);
            }
        }

        private void notifySuccess(QueuedSyncItem item, SyncSendResult result)
        {
            switch (item.type)
            {
                case CLOUD_LIVE_STATE, CLOUD_FINISHED_SESSION -> CloudSyncManager.onQueueSuccess(item.type, item.payload, result.responseBody());
                case PLAYER_TOTAL_DIGS -> DigsSyncManager.onQueueSuccess(item.payload, result.responseBody());
                case WEBSITE_LINK_CLAIM -> {
                    WebsiteLinkManager.onQueueSuccess(item.payload, result.responseBody());
                    forceFlush("successful website link");
                }
            }
        }

        private void notifyRetry(QueuedSyncItem item, SyncSendResult result, long nextRetryAtMs)
        {
            switch (item.type)
            {
                case CLOUD_LIVE_STATE, CLOUD_FINISHED_SESSION -> CloudSyncManager.onQueueRetry(item.type, result.detail(), nextRetryAtMs);
                case PLAYER_TOTAL_DIGS -> DigsSyncManager.onQueueRetry(result.detail(), nextRetryAtMs);
                case WEBSITE_LINK_CLAIM -> WebsiteLinkManager.onQueueRetry(result.detail(), nextRetryAtMs);
            }
        }

        private void notifyDropped(QueuedSyncItem item, SyncSendResult result)
        {
            switch (item.type)
            {
                case CLOUD_LIVE_STATE, CLOUD_FINISHED_SESSION -> CloudSyncManager.onQueueDropped(item.type, result.detail());
                case PLAYER_TOTAL_DIGS -> DigsSyncManager.onQueueDropped(result.detail());
                case WEBSITE_LINK_CLAIM -> WebsiteLinkManager.onQueueDropped(result.detail());
            }
        }
    }
}
