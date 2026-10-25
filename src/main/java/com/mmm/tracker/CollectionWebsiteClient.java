package com.mmm.tracker;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.config.Configs;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class CollectionWebsiteClient
{
    public record Snapshot(Map<String, Long> counts, int worlds, long receivedAt) {
        public Snapshot(Map<String, Long> counts, int worlds) { this(counts, worlds, System.nanoTime()); }
    }
    private record Cached(CompletableFuture<Snapshot> result, long expires) {}
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>();

    private CollectionWebsiteClient() {}

    public static synchronized CompletableFuture<Snapshot> load(String playerKey, String username)
    {
        boolean linked = Configs.websiteLinkedMinecraftUsername != null && username.equalsIgnoreCase(Configs.websiteLinkedMinecraftUsername)
                && Configs.websiteSyncToken != null && !Configs.websiteSyncToken.isBlank();
        String key = playerKey + ":" + username.toLowerCase(Locale.ROOT) + ":" + linked;
        Cached cached = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && (!cached.result.isDone() || cached.expires > now)) return cached.result;
        URI uri = URI.create(linked ? "https://www.mmmaniacs.com/api/mod-collections" : "https://www.mmmaniacs.com/api/player-detail?slug="
                + URLEncoder.encode(username, StandardCharsets.UTF_8));
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
        if (linked) builder.header("x-mmm-client-sync-token", Configs.websiteSyncToken)
                .header("x-mmm-minecraft-uuid", Configs.websiteLinkedMinecraftUuid).header("x-mmm-client-id", Configs.cloudClientId);
        var request = builder.GET().build();
        var future = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) throw new IllegalStateException("Website unavailable (HTTP " + response.statusCode() + ")");
            return parse(response.body());
        });
        CACHE.put(key, new Cached(future, now + 60_000));
        while (CACHE.size() > 4) CACHE.remove(CACHE.keySet().iterator().next());
        future.whenComplete((result, failure) -> {
            synchronized (CollectionWebsiteClient.class) {
                Cached current = CACHE.get(key);
                if (current != null && current.result == future)
                    CACHE.put(key, new Cached(future, System.currentTimeMillis() + (failure == null ? 60_000 : 30_000)));
            }
        });
        return future;
    }

    public static Snapshot parse(String body)
    {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("blockBreakdowns") || !root.get("blockBreakdowns").isJsonArray())
            throw new IllegalStateException("Website block breakdown unavailable");
        if (root.has("_profileIncompleteFields"))
            for (var field : root.getAsJsonArray("_profileIncompleteFields"))
                if ("blockBreakdowns".equals(field.getAsString()))
                    throw new IllegalStateException("Website block breakdown is still loading");
        var sources = new ArrayList<CollectionProgress.Source>();
        var worlds = new HashSet<String>();
        for (var element : root.getAsJsonArray("blockBreakdowns")) {
            JsonObject source = element.getAsJsonObject();
            String id = text(source, "sourceKey");
            if (id.isBlank()) id = text(source, "worldId");
            if (id.isBlank()) throw new IllegalStateException("Website world identity missing");
            var counts = new HashMap<String, Long>();
            for (var block : source.getAsJsonArray("items")) {
                JsonObject item = block.getAsJsonObject();
                String blockId = text(item, "blockId");
                long count = item.get("count").getAsBigDecimal().longValueExact();
                if (!blockId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || count < 0)
                    throw new IllegalStateException("Invalid website block count");
                counts.merge(blockId, count, Math::max);
            }
            worlds.add(id);
            sources.add(new CollectionProgress.Source(id, counts));
        }
        return new Snapshot(CollectionProgress.aggregate(sources), worlds.size());
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : "";
    }
}
