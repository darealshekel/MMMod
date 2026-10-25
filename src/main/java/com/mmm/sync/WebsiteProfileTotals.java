package com.mmm.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.util.MmmLog;
import com.mmm.config.Configs;
import com.mmm.tracker.MiningStats;
import com.mmm.util.MmmDebugLogger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import net.minecraft.client.Minecraft;

public final class WebsiteProfileTotals
{
    private static final String PROFILE_API = "https://www.mmmaniacs.com/api/player-detail?slug=%s&view=total&refreshCache=1&liveTs=%d";
    private static final long REFRESH_LOG_INTERVAL_MS = 30_000L;
    private static final long JSON_PARSE_DEBUG_LOG_INTERVAL_MS = 30_000L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8L))
            .build();
    private static final ProfileRequestIdentity REQUESTS = new ProfileRequestIdentity();

    private WebsiteProfileTotals()
    {
    }

    public static void refresh(boolean force)
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        if (!client.isOnSameThread()) { client.executeTask(() -> refresh(force)); return; }
        long now = System.currentTimeMillis();
        String identity = requestIdentity();
        String uuid = activeUuid();
        if (uuid.isEmpty()) return;
        if (!uuid.equals(Configs.websiteGlobalTotalOwner))
        {
            Configs.websiteGlobalTotalBlocks = 0;
            Configs.websiteGlobalTotalUpdatedAtMs = 0;
            Configs.websiteGlobalTotalOwner = uuid;
        }
        String username = resolveUsername();
        if (username.isBlank())
        {
            return;
        }

        long ticket = REQUESTS.begin(identity, now, force);
        if (ticket == 0) return;
        String encodedUsername = URLEncoder.encode(username.toLowerCase(), StandardCharsets.UTF_8);
        String url = String.format(PROFILE_API, encodedUsername, now);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10L))
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> client.executeTask(() -> {
                    if (!REQUESTS.finish(ticket, requestIdentity())) return;
                    try
                    {
                        if (throwable != null || response == null || response.statusCode() < 200 || response.statusCode() >= 300)
                        {
                            MmmDebugLogger.info(
                                    "website-profile-total-refresh-failed",
                                    REFRESH_LOG_INTERVAL_MS,
                                    "[MMM_SYNC] profile-total-refresh-failed username={} status={} error={}",
                                    username,
                                    response == null ? -1 : response.statusCode(),
                                    throwable == null ? "" : throwable.getMessage());
                            return;
                        }

                        JsonObject profileTotal = parseProfileTotal(response.body());
                        long blocksNum = extractBlocksNum(profileTotal);
                        if (blocksNum < 0L || !isAuthoritative(profileTotal))
                        {
                            return;
                        }
                        applyMiningRecords(profileTotal);

                        long currentTotal = Math.max(0L, Configs.websiteGlobalTotalBlocks);
                        long profileUpdatedAtMs = extractUpdatedAtMs(profileTotal);
                        boolean newerCorrection = profileUpdatedAtMs > Configs.websiteGlobalTotalUpdatedAtMs;
                        if (blocksNum != currentTotal && (blocksNum >= currentTotal || currentTotal <= 0L || newerCorrection))
                        {
                            Configs.websiteGlobalTotalBlocks = blocksNum;
                            Configs.websiteGlobalTotalOwner = uuid;
                            Configs.websiteGlobalTotalUpdatedAtMs = Math.max(System.currentTimeMillis(), profileUpdatedAtMs);
                            Configs.saveToFile();
                        }
                        MmmDebugLogger.info(
                                "website-profile-total-refresh-ok",
                                REFRESH_LOG_INTERVAL_MS,
                                "[MMM_SYNC] profile-total-refresh-ok username={} blocksNum={}",
                                username,
                                blocksNum);
                    }
                    catch (RuntimeException error)
                    {
                        MmmLog.LOGGER.warn("[MMM] Invalid website profile response: {}", error.getMessage());
                    }
                }));
    }

    private static JsonObject parseProfileTotal(String body)
    {
        try
        {
            return JsonParser.parseString(body).getAsJsonObject();
        }
        catch (Exception e)
        {
            MmmLog.LOGGER.warn("[MMM_SYNC] failed to parse profile total response: {}", e.getMessage());
            return new JsonObject();
        }
    }

    private static long extractBlocksNum(JsonObject object)
    {
        return getLong(object, "blocksNum", getLong(object, "blocksMined", -1L));
    }

    private static boolean isAuthoritative(JsonObject object)
    {
        return object != null
                && object.has("authoritative")
                && object.get("authoritative").isJsonPrimitive()
                && object.get("authoritative").getAsBoolean();
    }

    private static long extractUpdatedAtMs(JsonObject object)
    {
        try
        {
            String value = object.has("lastUpdated") && object.get("lastUpdated").isJsonPrimitive()
                    ? object.get("lastUpdated").getAsString()
                    : object.has("updatedAt") && object.get("updatedAt").isJsonPrimitive()
                            ? object.get("updatedAt").getAsString()
                            : "";
            return value.isBlank() ? 0L : Instant.parse(value).toEpochMilli();
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }
    private static void applyMiningRecords(JsonObject profileTotal)
    {
        if (profileTotal == null || !profileTotal.has("miningRecords"))
        {
            return;
        }

        JsonElement element = profileTotal.get("miningRecords");
        if (element == null || !element.isJsonObject())
        {
            return;
        }

        JsonObject records = element.getAsJsonObject();
        MiningStats.applyAuthoritativeFastest100k(
                getLong(records, "fastest100kSeconds", 0L),
                getTimestamp(records, "fastest100kStartedAt"),
                getTimestamp(records, "fastest100kFinishedAt"));
    }

    private static long getTimestamp(JsonObject object, String key)
    {
        try
        {
            if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive())
            {
                return 0L;
            }

            String value = object.get(key).getAsString();
            return value.isBlank() ? 0L : Instant.parse(value).toEpochMilli();
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }
    private static long getLong(JsonObject object, String key, long fallback)
    {
        if (object == null || !object.has(key))
        {
            return fallback;
        }

        try
        {
            JsonElement element = object.get(key);
            return element != null && element.isJsonPrimitive() ? element.getAsLong() : fallback;
        }
        catch (Exception e)
        {
            MmmDebugLogger.debug(
                    "website-profile-total-" + key,
                    JSON_PARSE_DEBUG_LOG_INTERVAL_MS,
                    "[MMM_SYNC] failed to parse profile total field '{}': {}",
                    key,
                    e.getMessage());
            return fallback;
        }
    }

    private static String resolveUsername()
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getSession() == null)
        {
            return "";
        }

        try
        {
            String username = client.getSession().getUsername();
            return username == null ? "" : username.trim();
        }
        catch (Exception e)
        {
            MmmDebugLogger.debug(
                    "website-profile-username",
                    JSON_PARSE_DEBUG_LOG_INTERVAL_MS,
                    "[MMM_SYNC] failed to resolve profile username: {}",
                    e.getMessage());
            return "";
        }
    }

    public static String activeUuid()
    {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.getSession() == null || client.getSession().getProfile().getId() == null ? ""
                : com.mmm.storage.AccountSessionStorage.normalize(client.getSession().getProfile().getId().toString());
    }

    private static String requestIdentity()
    {
        return activeUuid() + "|" + Configs.websiteLinkedMinecraftUuid + "|" + Configs.websiteLinkedAtMs;
    }

    public static boolean ownsCachedTotal()
    {
        return !activeUuid().isEmpty() && activeUuid().equals(Configs.websiteGlobalTotalOwner);
    }
}
