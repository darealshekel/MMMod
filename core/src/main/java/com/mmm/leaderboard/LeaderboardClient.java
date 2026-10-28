package com.mmm.leaderboard;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

public final class LeaderboardClient
{
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final LinkedHashMap<URI, Cached> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private record Cached(String body, long expires) {}
    private LeaderboardClient() {}

    public static CompletableFuture<LeaderboardData.Page> load(LeaderboardData.Query query, boolean refresh) {
        URI uri = query.uri();
        synchronized (CACHE) {
            Cached cached = CACHE.get(uri);
            if (!refresh && cached != null && cached.expires > System.currentTimeMillis())
                return CompletableFuture.completedFuture(LeaderboardData.parse(query, cached.body));
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").GET().build();
        var requestFuture = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var result = requestFuture.thenApply(response -> {
            if (response.statusCode() == 429) throw new IllegalStateException("Too many requests. Wait a moment, then refresh.");
            if (response.statusCode() != 200) throw new IllegalStateException("Website unavailable (HTTP " + response.statusCode() + "). Refresh to retry.");
            LeaderboardData.Page page = LeaderboardData.parse(query, response.body());
            synchronized (CACHE) {
                CACHE.put(uri, new Cached(response.body(), System.currentTimeMillis() + 60_000));
                while (CACHE.size() > 24) CACHE.remove(CACHE.keySet().iterator().next());
            }
            return page;
        });
        result.whenComplete((page, failure) -> { if (result.isCancelled()) requestFuture.cancel(true); });
        return result;
    }
}
