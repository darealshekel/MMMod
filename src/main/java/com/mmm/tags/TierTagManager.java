package com.mmm.tags;

import com.mmm.util.MmmLog;
import com.mmm.Reference;
import com.mmm.config.Configs;
import com.mmm.legacy.social.LegacySocialText;
import com.mmm.social.ActiveDiggerManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;

public final class TierTagManager
{
    private static final String PLAYER_TAG_API = System.getProperty("mmm.playerTagEndpoint", "https://www.mmmaniacs.com/api/mod-player-tags") + "?names=";
    private static final String PLAYER_PROFILE_API = System.getProperty("mmm.playerProfileEndpoint", "https://www.mmmaniacs.com/api/player-detail") + "?slug=";
    private static final long REFRESH_INTERVAL_MS = 60_000L;
    private static final long PROFILE_FALLBACK_REFRESH_INTERVAL_MS = 300_000L;
    private static final long RETRY_INTERVAL_MS = 15_000L;
    private static final int MAX_NAMES_PER_REQUEST = 80;
    private static final Pattern MINECRAFT_USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8L))
            .build();
    private static final AtomicBoolean REFRESH_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile Map<String, PlayerTagData> tags = Map.of();
    private static volatile Map<String, String> knownNames = Map.of();
    private static volatile String observedSignature = "";
    private static volatile String requestedSignature = "";
    private static volatile String lastResultLog = "";
    private static volatile long nextRefreshAtMs;
    private static int tickCounter;
    private static long generation;
    private static Object observedWorld;

    private TierTagManager()
    {
    }

    public static synchronized void onClientTick(Minecraft client)
    {
        Object world = client == null ? null : client.world;
        if (world != observedWorld) { clear(); observedWorld = world; }
        if (world == null || !Configs.Generic.TIER_NAME_TAGS.getBooleanValue()) { clear(); return; }
        if (++tickCounter < 20)
        {
            return;
        }
        tickCounter = 0;

        if (client == null || client.getNetworkHandler() == null)
        {
            clear();
            return;
        }

        LinkedHashMap<String, String> discoveredNames = new LinkedHashMap<>();
        for (PlayerInfo entry : client.getNetworkHandler().getOnlinePlayers())
        {
            addValidName(discoveredNames, entry.getProfile().getName());
        }
        addSidebarNames(discoveredNames, client.world.getScoreboard(), client.player == null ? "" : client.player.getName());
        for (String name : ActiveDiggerManager.visibleRemoteDiggers(List.of())) addValidName(discoveredNames, name);

        LinkedHashMap<String, String> currentNames = new LinkedHashMap<>();
        discoveredNames.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> currentNames.put(entry.getKey(), entry.getValue()));

        String signature = generation + ":" + String.join(",", currentNames.keySet());
        knownNames = Map.copyOf(currentNames);
        observedSignature = signature;
        if (currentNames.isEmpty())
        {
            tags = Map.of();
            return;
        }
        long now = System.currentTimeMillis();
        if (signature.equals(requestedSignature) && now < nextRefreshAtMs)
        {
            return;
        }
        refresh(List.copyOf(currentNames.values()), signature, now);
    }

    public static Text decorateName(String username, Text original)
    {
        if (original == null || !Configs.Generic.TIER_NAME_TAGS.getBooleanValue())
        {
            return null;
        }
        String normalized = PlayerTagPayload.normalize(username);
        PlayerTagData tag = tags.get(normalized);
        if (tag == null)
        {
            return null;
        }

        String displayName = knownNames.getOrDefault(normalized, username);
        Text decorated = new LiteralText("").setStyle(original.getStyle().copy());
        decorated.append(LegacySocialText.rgb(PlayerTagPayload.formatBlocks(tag.totalBlocks()), tag.colorRgb()));
        decorated.append(LegacySocialText.text(" | ", Formatting.DARK_GRAY));
        decorated.append(new LiteralText(displayName).setStyle(original.getStyle().copy().setColor(Formatting.WHITE)));
        return decorated;
    }

    public static Text decorateDisplayedName(Text original)
    {
        if (original == null || !Configs.Generic.TIER_NAME_TAGS.getBooleanValue())
        {
            return null;
        }
        Collection<String> names = knownNames.values();
        String username = PlayerTagPayload.findKnownUsername(Formatting.strip(original.getString()), names);
        return username.isBlank() ? null : decorateName(username, original);
    }

    private static void addValidName(Map<String, String> names, String name)
    {
        if (name != null && MINECRAFT_USERNAME.matcher(name).matches())
        {
            names.putIfAbsent(PlayerTagPayload.normalize(name), name);
        }
    }

    public static String decorateNameString(String username, String original) {
        Text decorated = decorateName(username, new LiteralText(original));
        return decorated == null ? original : decorated.getFormattedString();
    }

    static void addSidebarNames(Map<String, String> names, Scoreboard scoreboard, String localName) {
        Team team = scoreboard.getTeamOfMember(localName);
        ScoreboardObjective objective = team != null && team.getColor().getId() >= 0
                ? scoreboard.getDisplayObjective(3 + team.getColor().getId()) : null;
        if (objective == null) objective = scoreboard.getDisplayObjective(1);
        if (objective != null) scoreboard.getScores(objective).forEach(score -> addValidName(names, score.getOwner()));
    }

    private static void refresh(List<String> names, String signature, long now)
    {
        if (!REFRESH_IN_FLIGHT.compareAndSet(false, true))
        {
            return;
        }
        requestedSignature = signature;
        nextRefreshAtMs = now + REFRESH_INTERVAL_MS;

        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < names.size(); start += MAX_NAMES_PER_REQUEST)
        {
            List<String> batch = List.copyOf(names.subList(start, Math.min(names.size(), start + MAX_NAMES_PER_REQUEST)));
            batches.add(batch);
        }

        // Process batches one at a time so their fallback limits are global, not per batch.
        BoundedRequests.map(batches, 1, batch -> signature.equals(observedSignature)
                ? requestBatch(batch, signature)
                : CompletableFuture.completedFuture(new BatchResult(Map.of(), false, false)))
                .whenComplete((results, throwable) -> {
            synchronized (TierTagManager.class) {
            try
            {
                if (!signature.equals(observedSignature))
                {
                    nextRefreshAtMs = 0L;
                    return;
                }
                if (throwable != null)
                {
                    nextRefreshAtMs = System.currentTimeMillis() + RETRY_INTERVAL_MS;
                    return;
                }

                Map<String, PlayerTagData> next = new LinkedHashMap<>();
                for (String name : names)
                {
                    PlayerTagData existing = tags.get(PlayerTagPayload.normalize(name));
                    if (existing != null)
                    {
                        next.put(PlayerTagPayload.normalize(name), existing);
                    }
                }

                boolean anySuccess = false;
                boolean usedProfileFallback = false;
                for (BatchResult result : results)
                {
                    if (!result.success())
                    {
                        continue;
                    }
                    anySuccess = true;
                    usedProfileFallback |= result.profileFallback();
                    result.tags().forEach(next::put);
                }

                if (anySuccess)
                {
                    tags = Map.copyOf(next);
                    if (usedProfileFallback)
                    {
                        nextRefreshAtMs = System.currentTimeMillis() + PROFILE_FALLBACK_REFRESH_INTERVAL_MS;
                    }
                }
                else
                {
                    nextRefreshAtMs = System.currentTimeMillis() + RETRY_INTERVAL_MS;
                }
            }
            finally
            {
                REFRESH_IN_FLIGHT.set(false);
            }
            }
        });
    }

    private static CompletableFuture<BatchResult> requestBatch(List<String> batch, String signature)
    {
        String encodedNames = URLEncoder.encode(String.join(",", batch), StandardCharsets.UTF_8);
        HttpRequest request = buildRequest(PLAYER_TAG_API + encodedNames);
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (!isSuccessful(response, throwable) || !PlayerTagPayload.isTagPayload(response.body()))
                    {
                        logResult("failed", batch.size(), 0, response, throwable);
                        return requestProfileBatch(batch, signature);
                    }
                    Map<String, PlayerTagData> loadedTags = PlayerTagPayload.parse(response.body());
                    logResult("profile-tags", batch.size(), loadedTags.size(), response, null);
                    return CompletableFuture.completedFuture(new BatchResult(loadedTags, true, false));
                })
                .thenCompose(result -> result);
    }

    private static CompletableFuture<BatchResult> requestProfileBatch(List<String> batch, String signature)
    {
        return BoundedRequests.<String, Map.Entry<String, PlayerTagData>>map(batch, 4,
                name -> signature.equals(observedSignature) ? requestProfile(name) : CompletableFuture.completedFuture(null))
                .thenApply(results -> {
                    Map<String, PlayerTagData> loadedTags = new LinkedHashMap<>();
                    for (Map.Entry<String, PlayerTagData> entry : results)
                    {
                        if (entry != null)
                        {
                            loadedTags.put(entry.getKey(), entry.getValue());
                        }
                    }
                    logResult("profile-fallback", batch.size(), loadedTags.size(), null, null);
                    return new BatchResult(loadedTags, !loadedTags.isEmpty(), true);
                });
    }

    private static CompletableFuture<Map.Entry<String, PlayerTagData>> requestProfile(String username)
    {
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        return HTTP_CLIENT.sendAsync(buildRequest(PLAYER_PROFILE_API + encodedName), HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (!isSuccessful(response, throwable))
                    {
                        return null;
                    }
                    PlayerTagData tag = PlayerTagPayload.parseProfile(response.body(), username);
                    return tag == null ? null : Map.entry(PlayerTagPayload.normalize(username), tag);
                });
    }

    private static HttpRequest buildRequest(String url)
    {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12L))
                .header("Accept", "application/json")
                .header("User-Agent", "MMMod/" + Reference.MOD_VERSION)
                .GET()
                .build();
    }

    private static boolean isSuccessful(HttpResponse<String> response, Throwable throwable)
    {
        return throwable == null && response != null && response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private static void logResult(String source, int requested, int matched, HttpResponse<String> response, Throwable throwable)
    {
        String detail = throwable != null
                ? throwable.getClass().getSimpleName()
                : response == null ? "no-response" : "http-" + response.statusCode();
        String fingerprint = source + ':' + requested + ':' + matched + ':' + detail;
        if (fingerprint.equals(lastResultLog))
        {
            return;
        }
        lastResultLog = fingerprint;
        if ("failed".equals(source))
        {
            MmmLog.LOGGER.warn("[MMM_TAGS] load failed requested={} detail={}", requested, detail);
        }
        else
        {
            MmmLog.LOGGER.info("[MMM_TAGS] loaded source={} requested={} matched={} detail={}", source, requested, matched, detail);
        }
    }

    public static synchronized void clear()
    {
        observedWorld = null;
        tickCounter = 0;
        if (observedSignature.isEmpty() && tags.isEmpty() && knownNames.isEmpty())
        {
            return;
        }
        generation++;
        tags = Map.of();
        knownNames = Map.of();
        observedSignature = "";
        requestedSignature = "";
        lastResultLog = "";
        nextRefreshAtMs = 0L;
    }

    private record BatchResult(Map<String, PlayerTagData> tags, boolean success, boolean profileFallback)
    {
    }
}
