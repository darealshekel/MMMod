package com.mmm.tags;

import com.mmm.Reference;
import com.mmm.config.Configs;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class TierTagManager
{
    private static final String TAG_API = "https://www.mmmaniacs.com/api/mod-player-tags?names=";
    private static final String LEADERBOARD_FALLBACK_API = "https://www.mmmaniacs.com/api/leaderboard?friendsOnly=1&page=1&pageSize=100&friendNames=";
    private static final long REFRESH_INTERVAL_MS = 60_000L;
    private static final long RETRY_INTERVAL_MS = 15_000L;
    private static final int MAX_NAMES_PER_REQUEST = 80;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8L))
            .build();
    private static final AtomicBoolean REFRESH_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile Map<String, PlayerTagData> tags = Map.of();
    private static volatile Map<String, String> onlineNames = Map.of();
    private static volatile String observedSignature = "";
    private static volatile String requestedSignature = "";
    private static volatile long nextRefreshAtMs;
    private static int tickCounter;

    private TierTagManager()
    {
    }

    public static void onClientTick(MinecraftClient client)
    {
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

        LinkedHashMap<String, String> currentNames = new LinkedHashMap<>();
        client.getNetworkHandler().getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(profile -> profile.getName())
                .filter(name -> name != null && name.matches("[A-Za-z0-9_]{1,16}"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(name -> currentNames.putIfAbsent(PlayerTagPayload.normalize(name), name));

        onlineNames = Map.copyOf(currentNames);
        String signature = String.join(",", currentNames.keySet());
        observedSignature = signature;
        if (signature.isBlank())
        {
            tags = Map.of();
            requestedSignature = "";
            return;
        }
        if (!Configs.Generic.TIER_NAME_TAGS.getBooleanValue())
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (signature.equals(requestedSignature) && now < nextRefreshAtMs)
        {
            return;
        }
        refresh(List.copyOf(currentNames.values()), signature, now);
    }

    public static MutableText decorateName(String username, Text original)
    {
        if (!Configs.Generic.TIER_NAME_TAGS.getBooleanValue())
        {
            return null;
        }
        String normalized = PlayerTagPayload.normalize(username);
        PlayerTagData tag = tags.get(normalized);
        if (tag == null)
        {
            return null;
        }

        String displayName = onlineNames.getOrDefault(normalized, username);
        MutableText decorated = Text.empty().setStyle(original.getStyle());
        decorated.append(Text.literal(PlayerTagPayload.formatBlocks(tag.totalBlocks()))
                .styled(style -> style.withColor(tag.colorRgb())));
        decorated.append(Text.literal(" | ").styled(style -> style.withColor(0x777777)));
        decorated.append(Text.literal(displayName)
                .setStyle(original.getStyle().withColor(0xFFFFFF)));
        return decorated;
    }

    public static MutableText decorateDisplayedName(Text original)
    {
        if (original == null)
        {
            return null;
        }
        Collection<String> names = onlineNames.values();
        String username = PlayerTagPayload.findKnownUsername(original.getString(), names);
        return username.isBlank() ? null : decorateName(username, original);
    }

    private static void refresh(List<String> names, String signature, long now)
    {
        if (!REFRESH_IN_FLIGHT.compareAndSet(false, true))
        {
            return;
        }
        requestedSignature = signature;
        nextRefreshAtMs = now + REFRESH_INTERVAL_MS;

        List<CompletableFuture<BatchResult>> requests = new ArrayList<>();
        for (int start = 0; start < names.size(); start += MAX_NAMES_PER_REQUEST)
        {
            List<String> batch = List.copyOf(names.subList(start, Math.min(names.size(), start + MAX_NAMES_PER_REQUEST)));
            requests.add(requestBatch(batch));
        }

        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) -> {
            try
            {
                if (!signature.equals(observedSignature))
                {
                    nextRefreshAtMs = 0L;
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
                for (CompletableFuture<BatchResult> request : requests)
                {
                    BatchResult result = request.join();
                    if (!result.success())
                    {
                        continue;
                    }
                    anySuccess = true;
                    for (String name : result.names())
                    {
                        next.remove(PlayerTagPayload.normalize(name));
                    }
                    next.putAll(result.tags());
                }

                if (anySuccess)
                {
                    tags = Map.copyOf(next);
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
        });
    }

    private static CompletableFuture<BatchResult> requestBatch(List<String> batch)
    {
        String encodedNames = URLEncoder.encode(String.join(",", batch), StandardCharsets.UTF_8);
        HttpRequest request = buildRequest(TAG_API + encodedNames);
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (isSuccessful(response, throwable) && PlayerTagPayload.isTagPayload(response.body()))
                    {
                        return CompletableFuture.completedFuture(
                                new BatchResult(batch, PlayerTagPayload.parse(response.body()), true));
                    }
                    return requestLeaderboardFallback(batch, encodedNames);
                })
                .thenCompose(result -> result);
    }

    private static CompletableFuture<BatchResult> requestLeaderboardFallback(List<String> batch, String encodedNames)
    {
        HttpRequest request = buildRequest(LEADERBOARD_FALLBACK_API + encodedNames);
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (!isSuccessful(response, throwable) || !PlayerTagPayload.isLeaderboardPayload(response.body()))
                    {
                        return new BatchResult(batch, Map.of(), false);
                    }
                    return new BatchResult(batch, PlayerTagPayload.parseLeaderboard(response.body()), true);
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

    private static void clear()
    {
        if (observedSignature.isEmpty() && tags.isEmpty())
        {
            return;
        }
        tags = Map.of();
        onlineNames = Map.of();
        observedSignature = "";
        requestedSignature = "";
        nextRefreshAtMs = 0L;
    }

    private record BatchResult(List<String> names, Map<String, PlayerTagData> tags, boolean success)
    {
    }
}
