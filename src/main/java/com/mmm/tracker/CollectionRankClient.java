package com.mmm.tracker;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Public, synced block rankings; never inferred from a local world's count. */
public final class CollectionRankClient {
    public enum Status { LOADING, RANKED, UNRANKED, UNSUPPORTED, UNAVAILABLE }
    public record Rank(Status status, long position, long players) {
        public String topPercent() {
            if (status != Status.RANKED) return "";
            double percent = 100.0 * position / players;
            return percent < 0.1 ? "<0.1%" : String.format(Locale.ROOT, "%.1f%%", percent);
        }
    }
    private record Parsed(Rank rank, int page, int pages) {}
    private record Cached(CompletableFuture<Rank> result, long expires) {}
    private static final Rank LOADING = new Rank(Status.LOADING, 0, 0);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
        var thread = new Thread(task, "MMM collection rankings"); thread.setDaemon(true); return thread;
    });
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>();
    private CollectionRankClient() {}

    public static synchronized Rank get(String block, String username) {
        // These collections combine multiple block IDs or use local-only evidence.
        if (!block.startsWith("minecraft:") || block.equals("minecraft:terracotta"))
            return new Rank(Status.UNSUPPORTED, 0, 0);
        if (username == null || !username.matches("[A-Za-z0-9_]{1,16}"))
            return new Rank(Status.UNAVAILABLE, 0, 0);
        String key = block + ":" + username.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Cached cached = CACHE.get(key);
        if (cached == null || cached.result.isDone() && now >= cached.expires) {
            if (cached == null && CACHE.size() >= 64) {
                var oldest = CACHE.entrySet().stream().filter(entry -> entry.getValue().result.isDone()).findFirst();
                if (oldest.isEmpty()) return LOADING;
                CACHE.remove(oldest.get().getKey());
            }
            var future = CompletableFuture.supplyAsync(() -> fetch(block, username), WORKERS);
            cached = new Cached(future, Long.MAX_VALUE);
            CACHE.put(key, cached);
            future.thenAccept(rank -> {
                synchronized (CollectionRankClient.class) {
                    Cached current = CACHE.get(key);
                    if (current != null && current.result == future)
                        CACHE.put(key, new Cached(future, System.currentTimeMillis()
                                + (rank.status == Status.UNAVAILABLE ? 30_000 : 60_000)));
                }
            });
        }
        return cached.result.getNow(LOADING);
    }
    private static Rank fetch(String block, String username) {
        try {
            for (int page = 1; page <= 10; page++) {
                URI uri = URI.create("https://www.mmmaniacs.com/api/leaderboard-blocks?block="
                        + URLEncoder.encode(block, StandardCharsets.UTF_8) + "&query="
                        + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&page=" + page);
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                        .header("Accept", "application/json").GET().build();
                var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) return new Rank(Status.UNSUPPORTED, 0, 0);
                if (response.statusCode() != 200) return new Rank(Status.UNAVAILABLE, 0, 0);
                Parsed result = parsePage(block, username, response.body());
                if (result.page != page) throw new IllegalArgumentException("Unexpected ranking page");
                if (result.rank.status == Status.RANKED || result.page >= result.pages) return result.rank;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Failure is displayed explicitly and cached briefly before retrying.
        }
        return new Rank(Status.UNAVAILABLE, 0, 0);
    }
    public static Rank parse(String block, String username, String body) { return parsePage(block, username, body).rank; }
    private static Parsed parsePage(String block, String username, String body) {
        var root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.get("blockId").getAsString().equals(block)) throw new IllegalArgumentException("Wrong block ranking");
        long players = root.get("totalPlayers").getAsBigDecimal().longValueExact();
        long matches = root.get("matchingPlayers").getAsBigDecimal().longValueExact();
        int page = root.get("page").getAsInt(), size = root.get("pageSize").getAsInt();
        if (players < 0 || matches < 0 || matches > players || page < 1 || size <= 0)
            throw new IllegalArgumentException("Invalid ranking population");
        Rank rank = new Rank(Status.UNRANKED, 0, players);
        for (var element : root.getAsJsonArray("rows")) {
            var row = element.getAsJsonObject();
            if (!row.get("username").getAsString().equalsIgnoreCase(username)) continue;
            long position = row.get("rank").getAsBigDecimal().longValueExact();
            if (position < 1 || position > players) throw new IllegalArgumentException("Invalid player rank");
            rank = new Rank(Status.RANKED, position, players);
            break;
        }
        return new Parsed(rank, page, Math.toIntExact(Math.max(1, (matches + size - 1) / size)));
    }
}
