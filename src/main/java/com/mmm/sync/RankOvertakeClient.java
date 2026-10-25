package com.mmm.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.config.Configs;
import com.mmm.storage.AsyncPersistence;
import com.mmm.storage.AtomicJsonStorage;
import com.mmm.storage.SharedStoragePaths;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

public final class RankOvertakeClient
{
    private static final long REFRESH_MS = 300_000L;
    private static final int PAGE_SIZE = 100;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final AtomicBoolean SYNCED = new AtomicBoolean();
    private static String username = "", playerKey = "";
    private static long generation, nextRefresh, nextHudUpdate, nextPageAttempt, nextSave;
    private static boolean inFlight, dirty, freshAfterSync;
    private static RankLocalProgress progress = new RankLocalProgress();
    private static RankOvertakeEstimate.Player websitePlayer;
    private static final Map<Integer, RankOvertakeEstimate.Player> rows = new HashMap<>();
    private static List<String> lines = List.of("Rank Overtake: Loading...");
    private static String status = "Loading...";

    private RankOvertakeClient() {}
    public static List<String> hudLines() { return lines; }
    public static void onWebsiteSync() { SYNCED.set(true); }
    public static void onAcceptedBlock()
    {
        if (progress.website() >= 0)
        {
            progress.mined();
            dirty = true;
        }
    }

    public static void onClientTick(Minecraft client)
    {
        String key = client.getSession().getProfile().getId() == null ? client.getSession().getUsername()
                : client.getSession().getProfile().getId().toString();
        if (!key.equals(playerKey)) activate(key, client.getSession().getUsername());
        long now = System.currentTimeMillis();
        if (dirty && (now >= nextSave || client.player == null)) save(now);
        if (SYNCED.getAndSet(false)) { freshAfterSync = true; nextRefresh = 0; }
        if (!Configs.Generic.HUD_RANK_OVERTAKE_VISIBLE.getBooleanValue() || client.player == null) return;
        if (!inFlight && now >= nextRefresh) refresh(client, now);
        if (now < nextHudUpdate) return;
        nextHudUpdate = now + 1000;
        if (websitePlayer == null)
        {
            lines = List.of("Rank Overtake: " + status);
            return;
        }
        RankOvertakeEstimate estimate = RankOvertakeEstimate.project(websitePlayer, rows, progress.total());
        if (estimate == null)
        {
            lines = List.of("Rank Overtake: Loading next ranks...", "Estimated Total: " + UiFormat.formatCompact(progress.total()));
            if (!inFlight && now >= nextPageAttempt) loadMore(client, now);
            return;
        }
        String ranks = "Estimated Rank: #" + estimate.player().rank() + " | Website: #" + websitePlayer.rank();
        if (estimate.target() == null)
        {
            lines = List.of(ranks, "No player ahead at this estimated total");
            return;
        }
        long blocksPerHour = MiningStats.getDisplayedBlocksPerHour();
        long seconds = estimate.estimatedSeconds(blocksPerHour);
        String eta = !MiningStats.isSessionActive() ? "Start a session" : MiningStats.isSessionPaused() ? "Paused"
                : seconds < 0 ? "Waiting for mining pace" : "~" + RankOvertakeEstimate.formatActiveTime(seconds)
                        + " at " + UiFormat.formatCompact(blocksPerHour) + " blocks/hr";
        lines = List.of(ranks, "Next: #" + estimate.target().rank() + " " + estimate.target().name(),
                "To Pass: " + UiFormat.formatCompact(estimate.blocksNeeded()) + " blocks", "Overtake ETA: " + eta);
    }

    private static void activate(String key, String name)
    {
        if (dirty) save(System.currentTimeMillis());
        playerKey = key;
        username = name;
        generation++;
        progress = new RankLocalProgress();
        websitePlayer = null;
        rows.clear();
        nextRefresh = nextHudUpdate = nextPageAttempt = 0;
        lines = List.of("Rank Overtake: Loading...");
        status = "Loading...";
        freshAfterSync = false;
        try
        {
            JsonObject saved = AtomicJsonStorage.readObjectWithBackup(statePath()).value();
            if (saved != null) progress = new RankLocalProgress(saved.get("website").getAsLong(), saved.get("pending").getAsLong());
        }
        catch (Exception error) { com.mmm.util.MmmLog.LOGGER.warn("[MMM] Could not read rank estimate state: {}", error.getMessage()); }
    }

    private static Path statePath()
    {
        return SharedStoragePaths.root().resolve("rank-estimate").resolve(playerKey.replaceAll("[^a-zA-Z0-9_-]", "") + ".json");
    }

    private static void save(long now)
    {
        Path path = statePath();
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("website", progress.website());
        snapshot.addProperty("pending", progress.pending());
        AsyncPersistence.submit("rank-estimate:" + path, () -> {
            try { AtomicJsonStorage.write(path, snapshot, true); }
            catch (Exception error) { throw new IllegalStateException("Could not save rank estimate", error); }
        });
        dirty = false;
        nextSave = now + 5000;
    }

    private static CompletableFuture<JsonObject> get(String query)
    {
        HttpRequest request = HttpRequest.newBuilder(RankOvertakeEstimate.leaderboardUri(query))
                .timeout(Duration.ofSeconds(15)).header("Accept", "application/json").GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) throw new IllegalStateException("Leaderboard unavailable");
            return JsonParser.parseString(response.body()).getAsJsonObject();
        });
    }

    private record Window(RankOvertakeEstimate.Player player, List<RankOvertakeEstimate.Player> rows) {}

    private static void refresh(Minecraft client, long now)
    {
        inFlight = true;
        nextRefresh = now + REFRESH_MS;
        long requestGeneration = generation;
        String requestedName = username;
        String encoded = URLEncoder.encode(requestedName.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
        String refresh = freshAfterSync ? "&refreshCache=1" : "";
        freshAfterSync = false;
        get("query=" + encoded + "&pageSize=100" + refresh).thenCompose(response -> {
            var player = RankOvertakeEstimate.findPlayer(response, requestedName);
            if (player == null) return CompletableFuture.completedFuture(new Window(null, List.of()));
            if (player.rank() == 1) return CompletableFuture.completedFuture(new Window(player, List.of()));
            int page = (player.rank() - 2) / PAGE_SIZE + 1;
            return get("pageSize=" + PAGE_SIZE + "&page=" + page + refresh)
                    .thenApply(targets -> {
                        RankOvertakeEstimate.fromTarget(player, targets);
                        var pagePlayer = RankOvertakeEstimate.findPlayer(targets, requestedName);
                        if (pagePlayer != null && !pagePlayer.equals(player))
                            throw new IllegalStateException("Leaderboard changed while loading");
                        return new Window(player, RankOvertakeEstimate.players(targets));
                    });
        }).whenComplete((result, error) -> client.executeTask(() -> {
            inFlight = false;
            if (generation != requestGeneration) return;
            if (error == null)
            {
                websitePlayer = result.player();
                rows.clear();
                for (var row : result.rows()) rows.put(row.rank(), row);
                if (websitePlayer != null) { progress.reconcile(websitePlayer.blocks()); dirty = true; }
                status = "Not ranked on website";
            }
            else { status = "Website unavailable - retrying in 1m"; nextRefresh = System.currentTimeMillis() + 60_000; }
            if (freshAfterSync) nextRefresh = 0;
            nextHudUpdate = 0;
        }));
    }

    private static void loadMore(Minecraft client, long now)
    {
        int missing = websitePlayer.rank() - 1;
        while (missing > 0 && rows.containsKey(missing)) missing--;
        if (missing < 1) return;
        inFlight = true;
        nextPageAttempt = now + 60_000;
        int requestedRank = missing;
        long requestGeneration = generation;
        get("pageSize=" + PAGE_SIZE + "&page=" + ((missing - 1) / PAGE_SIZE + 1))
                .thenApply(RankOvertakeEstimate::players).whenComplete((more, error) -> client.executeTask(() -> {
                    inFlight = false;
                    if (generation != requestGeneration) return;
                    if (error == null)
                    {
                        for (var row : more) rows.put(row.rank(), row);
                        if (more.stream().anyMatch(row -> row.rank() == requestedRank))
                            nextPageAttempt = System.currentTimeMillis() + 1000;
                    }
                    nextHudUpdate = 0;
                }));
    }
}
