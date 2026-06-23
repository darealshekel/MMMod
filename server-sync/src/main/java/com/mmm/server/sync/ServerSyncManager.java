package com.mmm.server.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

final class ServerSyncManager
{
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10L))
            .build();
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private ServerSyncConfig config = new ServerSyncConfig();
    private ServerInstallCredentials credentials;
    private ServerSyncState state = new ServerSyncState();
    private MinecraftServer server;
    private boolean syncInFlight;

    void onServerStarted(MinecraftServer server)
    {
        this.server = server;
        this.config = ServerSyncConfig.load();
        this.credentials = ServerInstallCredentials.load();
        this.state = ServerSyncState.load();
        scheduleAfterStartup();
        System.out.println("[MMM Server Sync] Loaded. enabled=" + this.config.enabled
                + " endpoint=" + this.config.endpoint
                + " installId=" + this.credentials.installId
                + " source=" + this.config.resolvedSourceName(server)
                + " status=" + this.state.installStatus
                + " nextAttemptAt=" + this.state.nextAttemptAt);
    }

    void onServerStopping(MinecraftServer server)
    {
        if (this.state.dirty)
        {
            this.state.save();
        }
        if (this.state.dataChangedSinceLastSuccessfulSync && dailySyncDue(Instant.now()))
        {
            sendSync(true);
        }
        this.server = null;
    }

    void onServerTick(MinecraftServer server)
    {
        if (this.server == null)
        {
            this.server = server;
        }
        if (syncDue(Instant.now()))
        {
            if (this.state.dirty)
            {
                this.state.save();
            }
            sendSync(false);
        }
    }

    void onBlockBroken(ServerPlayerEntity player, BlockState state)
    {
        if (player == null || state == null || ServerBlockCatalog.isValid(state.getBlock()) == false)
        {
            return;
        }

        UUID uuid = player.getUuid();
        String username = player.getGameProfile().getName();
        ServerSyncState.PlayerRecord record = this.state.record(uuid, username);
        record.incrementBlock(ServerBlockCatalog.id(state.getBlock()));

        Item item = player.getMainHandStack().getItem();
        String itemId = item == null ? "" : Registries.ITEM.getId(item).toString();
        record.incrementTool(itemId);
        this.state.markDataChanged();
    }

    private void sendSync(boolean blocking)
    {
        if (this.server == null || this.config.enabled == false)
        {
            return;
        }
        if (this.syncInFlight && blocking == false)
        {
            return;
        }

        Instant attemptedAt = Instant.now();
        this.state.lastAttemptAt = attemptedAt.toString();
        this.state.nextAttemptAt = ServerSyncSchedule
                .nextRetryAt(Math.max(1, this.state.consecutiveFailures + 1), attemptedAt)
                .toString();
        this.state.dirty = true;
        this.state.save();

        JsonObject payload = buildPayload();
        String body = GSON.toJson(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.config.endpoint))
                .timeout(Duration.ofSeconds(20L))
                .header("Content-Type", "application/json")
                .header("x-mmm-server-install-id", this.credentials.installId)
                .header("x-mmm-server-install-token", this.credentials.installToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        this.syncInFlight = true;
        if (blocking)
        {
            try
            {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                handleResponse(response.statusCode(), response.body(), Instant.now());
            }
            catch (Exception exception)
            {
                handleFailure("Sync failed: " + exception.getMessage(), Instant.now());
            }
            finally
            {
                this.syncInFlight = false;
            }
            return;
        }

        CompletableFuture<HttpResponse<String>> future = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        future.whenComplete((response, throwable) -> {
            MinecraftServer activeServer = this.server;
            Runnable completion = () -> {
                this.syncInFlight = false;
                if (throwable != null)
                {
                    handleFailure("Sync failed: " + throwable.getMessage(), Instant.now());
                    return;
                }
                handleResponse(response.statusCode(), response.body(), Instant.now());
            };
            if (activeServer != null)
            {
                activeServer.execute(completion);
            }
            else
            {
                completion.run();
            }
        });
    }

    private void handleResponse(int statusCode, String responseBody, Instant completedAt)
    {
        if (statusCode < 200 || statusCode >= 300)
        {
            handleFailure("Sync rejected status=" + statusCode + " body=" + safeBody(responseBody), completedAt);
            return;
        }

        JsonObject response;
        try
        {
            response = GSON.fromJson(responseBody, JsonObject.class);
        }
        catch (Exception exception)
        {
            handleFailure("Sync returned an invalid response.", completedAt);
            return;
        }

        JsonObject install = response == null ? null : response.getAsJsonObject("server_install");
        String installStatus = install == null || install.has("status") == false
                ? "unknown"
                : clean(install.get("status").getAsString()).toLowerCase(Locale.ROOT);
        boolean acceptedPublicTotals = response != null
                && response.has("accepted_public_totals")
                && response.get("accepted_public_totals").getAsBoolean();

        this.state.installStatus = switch (installStatus)
        {
            case "pending", "approved", "rejected" -> installStatus;
            default -> "unknown";
        };
        this.state.consecutiveFailures = 0;

        if ("approved".equals(this.state.installStatus) && acceptedPublicTotals)
        {
            this.state.lastSuccessfulSyncAt = completedAt.toString();
            this.state.nextAttemptAt = ServerSyncSchedule
                    .nextAttemptAfterResponse(this.state.installStatus, true, completedAt, this.config.syncIntervalSeconds)
                    .toString();
            this.state.dataChangedSinceLastSuccessfulSync = false;
            System.out.println("[MMM Server Sync] Daily authoritative sync accepted. nextSyncAt=" + this.state.nextAttemptAt);
        }
        else if ("rejected".equals(this.state.installStatus))
        {
            this.state.nextAttemptAt = ServerSyncSchedule
                    .nextAttemptAfterResponse(this.state.installStatus, false, completedAt, this.config.syncIntervalSeconds)
                    .toString();
            System.out.println("[MMM Server Sync] Installation is rejected. It will check again at " + this.state.nextAttemptAt);
        }
        else
        {
            this.state.nextAttemptAt = ServerSyncSchedule
                    .nextAttemptAfterResponse(this.state.installStatus, false, completedAt, this.config.syncIntervalSeconds)
                    .toString();
            System.out.println("[MMM Server Sync] Installation is pending approval. nextApprovalCheckAt=" + this.state.nextAttemptAt);
        }
        this.state.dirty = true;
        this.state.save();
    }

    private void handleFailure(String message, Instant failedAt)
    {
        this.state.consecutiveFailures = Math.min(this.state.consecutiveFailures + 1, 4);
        this.state.nextAttemptAt = ServerSyncSchedule.nextRetryAt(this.state.consecutiveFailures, failedAt).toString();
        this.state.dirty = true;
        this.state.save();
        System.err.println("[MMM Server Sync] " + message + " retryAt=" + this.state.nextAttemptAt);
    }

    private void scheduleAfterStartup()
    {
        Instant now = Instant.now();
        this.state.nextAttemptAt = ServerSyncSchedule.startupAttempt(
                this.state.installStatus,
                this.state.lastSuccessfulSyncAt,
                this.state.nextAttemptAt,
                now,
                this.config.syncIntervalSeconds).toString();
        this.state.dirty = true;
        this.state.save();
    }

    private boolean syncDue(Instant now)
    {
        if (this.config.enabled == false || this.syncInFlight)
        {
            return false;
        }
        Instant nextAttempt = ServerSyncSchedule.parseInstant(this.state.nextAttemptAt);
        return nextAttempt == null || !now.isBefore(nextAttempt);
    }

    private boolean dailySyncDue(Instant now)
    {
        return ServerSyncSchedule.dailySyncDue(
                this.state.lastSuccessfulSyncAt,
                now,
                this.config.syncIntervalSeconds);
    }

    private JsonObject buildPayload()
    {
        String now = Instant.now().toString();
        String sourceName = this.config.resolvedSourceName(this.server);
        String sourceKey = this.config.resolvedSourceKey(this.server);
        SourceSnapshot snapshot = chooseSourceSnapshot(sourceName, now);
        String latestMinedAt = snapshot.latestMinedAt() == null || snapshot.latestMinedAt().isBlank() ? now : snapshot.latestMinedAt();

        JsonObject payload = new JsonObject();
        payload.addProperty("client_id", "mmm-server-sync:" + sourceKey);
        payload.addProperty("username", this.config.reporterUsername);
        payload.addProperty("mod_version", "server-sync");
        payload.addProperty("minecraft_version", this.server == null ? "" : this.server.getVersion());
        payload.addProperty("sync_origin", "server_authoritative");
        payload.addProperty("server_install_id", this.credentials.installId);

        JsonObject world = new JsonObject();
        world.addProperty("key", sourceKey);
        world.addProperty("display_name", sourceName);
        world.addProperty("kind", "multiplayer");
        if (this.config.host != null && this.config.host.isBlank() == false)
        {
            world.addProperty("host", this.config.host);
        }
        payload.add("world", world);

        JsonObject totals = new JsonObject();
        totals.addProperty("world_key", sourceKey);
        totals.addProperty("display_name", sourceName);
        totals.addProperty("kind", "multiplayer");
        totals.addProperty("total_blocks", snapshot.totalDigs());
        totals.addProperty("last_seen_at", latestMinedAt);
        if (this.config.host != null && this.config.host.isBlank() == false)
        {
            totals.addProperty("host", this.config.host);
        }
        payload.add("current_world_totals", totals);

        payload.add("source_scan", buildSourceScan(snapshot, sourceName, latestMinedAt));
        payload.add("source_leaderboard", buildSourceLeaderboard(snapshot, sourceName, latestMinedAt));
        payload.add("server_player_block_breakdowns", buildBlockBreakdowns(sourceKey, sourceName, latestMinedAt));
        return payload;
    }

    private JsonObject buildSourceScan(SourceSnapshot snapshot, String sourceName, String capturedAt)
    {
        JsonObject scan = new JsonObject();
        scan.addProperty("compatible", true);
        scan.addProperty("confidence", snapshot.fromScoreboard() ? 100 : 85);
        scan.addProperty("scoreboard_title", snapshot.objectiveTitle());
        scan.addProperty("total_digs", snapshot.totalDigs());
        scan.addProperty("server_name", sourceName);

        JsonArray fields = new JsonArray();
        fields.add(snapshot.fromScoreboard() ? "server_scoreboard_objective" : "server_combined_tool_uses");
        fields.add("server_block_break_events");
        scan.add("detected_stat_fields", fields);

        JsonObject evidence = new JsonObject();
        evidence.addProperty("sync_origin", "server_authoritative");
        evidence.addProperty("server_install_id", this.credentials.installId);
        evidence.addProperty("captured_at", capturedAt);
        evidence.addProperty("entry_count", snapshot.entries().size());
        evidence.addProperty("source_kind", snapshot.fromScoreboard() ? "scoreboard" : "fallback_tool_uses");
        scan.add("raw_scan_evidence", evidence);
        return scan;
    }

    private JsonObject buildSourceLeaderboard(SourceSnapshot snapshot, String sourceName, String capturedAt)
    {
        JsonObject leaderboard = new JsonObject();
        leaderboard.addProperty("server_name", sourceName);
        leaderboard.addProperty("objective_title", snapshot.objectiveTitle());
        leaderboard.addProperty("total_digs", snapshot.totalDigs());
        leaderboard.addProperty("captured_at", capturedAt);
        leaderboard.addProperty("source_type", "server_authoritative");
        leaderboard.addProperty("mode", "full");

        JsonArray entries = new JsonArray();
        int limit = Math.min(this.config.maxPlayersPerSync, snapshot.entries().size());
        for (int index = 0; index < limit; index++)
        {
            SourceEntry sourceEntry = snapshot.entries().get(index);
            JsonObject row = new JsonObject();
            row.addProperty("username", sourceEntry.username());
            row.addProperty("digs", sourceEntry.digs());
            row.addProperty("rank", index + 1);
            row.addProperty("source_server", sourceName);
            if (sourceEntry.lastMinedAt() != null && sourceEntry.lastMinedAt().isBlank() == false)
            {
                row.addProperty("last_mined_at", sourceEntry.lastMinedAt());
            }
            entries.add(row);
        }
        leaderboard.add("entries", entries);
        return leaderboard;
    }

    private JsonObject buildBlockBreakdowns(String sourceKey, String sourceName, String capturedAt)
    {
        JsonObject payload = new JsonObject();
        payload.addProperty("source_key", sourceKey);
        payload.addProperty("source_name", sourceName);
        payload.addProperty("captured_at", capturedAt);
        payload.addProperty("scope", "server_players");
        payload.addProperty("source", "server_authoritative");

        JsonArray players = new JsonArray();
        this.state.players.values().stream()
                .filter(record -> record.username != null && record.username.isBlank() == false)
                .filter(record -> record.breakdownTotal() > 0L)
                .sorted(Comparator.comparingLong(ServerSyncState.PlayerRecord::breakdownTotal).reversed())
                .limit(this.config.maxPlayersPerSync)
                .forEach(record -> {
                    JsonObject player = new JsonObject();
                    player.addProperty("username", record.username);
                    player.addProperty("total_count", record.breakdownTotal());
                    if (record.lastMinedAt != null && record.lastMinedAt.isBlank() == false)
                    {
                        player.addProperty("last_mined_at", record.lastMinedAt);
                    }

                    JsonArray items = new JsonArray();
                    record.sortedBreakdown().forEach((blockId, count) -> {
                        JsonObject item = new JsonObject();
                        item.addProperty("block_id", blockId);
                        item.addProperty("count", count);
                        items.add(item);
                    });
                    player.add("items", items);
                    players.add(player);
                });
        payload.add("players", players);
        return payload;
    }

    private SourceSnapshot chooseSourceSnapshot(String sourceName, String now)
    {
        SourceSnapshot scoreboard = readScoreboardSnapshot(sourceName);
        if (scoreboard != null && scoreboard.entries().isEmpty() == false)
        {
            return scoreboard;
        }
        return fallbackToolUseSnapshot(sourceName, now);
    }

    private SourceSnapshot readScoreboardSnapshot(String sourceName)
    {
        if (this.server == null)
        {
            return null;
        }

        Scoreboard scoreboard = this.server.getScoreboard();
        List<ObjectiveSnapshot> totalCandidates = new ArrayList<>();
        Map<String, ToolUseAccumulator> combinedToolUses = new LinkedHashMap<>();
        int detectedToolObjectives = 0;

        for (ScoreboardObjective objective : scoreboard.getObjectives())
        {
            String objectiveTitle = objectiveTitle(objective);
            List<SourceEntry> rows = objectiveEntries(scoreboard, objective);
            if (rows.isEmpty())
            {
                continue;
            }

            int priority = objectivePriority(objective.getName() + " " + objectiveTitle);
            if (priority >= 70)
            {
                totalCandidates.add(new ObjectiveSnapshot(objectiveTitle, priority, rows));
                continue;
            }

            ToolKind toolKind = toolKind(objective.getName() + " " + objectiveTitle);
            if (toolKind != ToolKind.NONE)
            {
                detectedToolObjectives += 1;
                for (SourceEntry row : rows)
                {
                    combinedToolUses
                            .computeIfAbsent(normalizeUsername(row.username()), ignored -> new ToolUseAccumulator(row.username()))
                            .putMax(toolKind, row.digs());
                }
            }
        }

        if (totalCandidates.isEmpty() == false)
        {
            ObjectiveSnapshot chosen = totalCandidates.stream()
                    .max(Comparator
                            .comparingInt(ObjectiveSnapshot::priority)
                            .thenComparingInt(snapshot -> snapshot.rows().size())
                            .thenComparingLong(snapshot -> snapshot.rows().stream().mapToLong(SourceEntry::digs).sum()))
                    .orElse(null);
            if (chosen != null)
            {
                List<SourceEntry> entries = rankEntries(withLastMinedAt(chosen.rows()));
                return new SourceSnapshot(
                        sourceName,
                        chosen.objectiveTitle(),
                        entries.stream().mapToLong(SourceEntry::digs).sum(),
                        entries,
                        true
                );
            }
        }

        if (combinedToolUses.isEmpty() == false && detectedToolObjectives > 0)
        {
            List<SourceEntry> entries = rankEntries(combinedToolUses.values().stream()
                    .map(accumulator -> new SourceEntry(
                            accumulator.username(),
                            accumulator.total(),
                            lastMinedAt(accumulator.username())))
                    .filter(entry -> entry.digs() > 0L)
                    .toList());
            return new SourceSnapshot(
                    sourceName,
                    "Combined Tool Uses",
                    entries.stream().mapToLong(SourceEntry::digs).sum(),
                    entries,
                    true
            );
        }
        return null;
    }

    private SourceSnapshot fallbackToolUseSnapshot(String sourceName, String now)
    {
        List<SourceEntry> entries = rankEntries(this.state.players.values().stream()
                .filter(record -> record.username != null && record.username.isBlank() == false)
                .map(record -> new SourceEntry(record.username, Math.max(record.toolUseTotal(), record.breakdownTotal()), record.lastMinedAt))
                .filter(entry -> entry.digs() > 0L)
                .toList());
        return new SourceSnapshot(
                sourceName,
                "Combined Tool Uses",
                entries.stream().mapToLong(SourceEntry::digs).sum(),
                entries,
                false
        );
    }

    private List<SourceEntry> objectiveEntries(Scoreboard scoreboard, ScoreboardObjective objective)
    {
        List<SourceEntry> entries = new ArrayList<>();
        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective))
        {
            String username = clean(entry.owner());
            if (isMinecraftUsername(username) == false)
            {
                continue;
            }
            long digs = Math.max(0L, entry.value());
            if (digs <= 0L)
            {
                continue;
            }
            entries.add(new SourceEntry(username, digs, lastMinedAt(username)));
        }
        return entries;
    }

    private List<SourceEntry> withLastMinedAt(List<SourceEntry> entries)
    {
        return entries.stream()
                .map(entry -> new SourceEntry(entry.username(), entry.digs(), lastMinedAt(entry.username())))
                .toList();
    }

    private String lastMinedAt(String username)
    {
        ServerSyncState.PlayerRecord record = this.state.findByUsername(username);
        return record == null ? null : record.lastMinedAt;
    }

    private static List<SourceEntry> rankEntries(List<SourceEntry> entries)
    {
        return entries.stream()
                .filter(entry -> entry.username() != null && entry.username().isBlank() == false && entry.digs() > 0L)
                .collect(LinkedHashMap<String, SourceEntry>::new, (map, entry) -> {
                    String key = normalizeUsername(entry.username());
                    SourceEntry previous = map.get(key);
                    if (previous == null || entry.digs() > previous.digs())
                    {
                        map.put(key, entry);
                    }
                }, LinkedHashMap::putAll)
                .values()
                .stream()
                .sorted(Comparator.comparingLong(SourceEntry::digs).reversed().thenComparing(entry -> normalizeUsername(entry.username())))
                .toList();
    }

    private static String objectiveTitle(ScoreboardObjective objective)
    {
        String display = objective.getDisplayName() == null ? "" : clean(objective.getDisplayName().getString());
        String name = clean(objective.getName());
        return display.isBlank() ? name : display;
    }

    private static int objectivePriority(String value)
    {
        String lower = normalized(value);
        String compact = compact(lower);
        if (compact.equals("sum")
                || compact.equals("total")
                || compact.contains("combinedblocks")
                || (compact.contains("total") && (compact.contains("block") || compact.contains("mine") || compact.contains("dig") || compact.contains("dug")))
                || (compact.contains("sum") && (compact.contains("block") || compact.contains("mine") || compact.contains("dig") || compact.contains("dug"))))
        {
            return 90;
        }
        if (compact.contains("dig") || compact.contains("dug") || compact.contains("blockmined") || compact.contains("minedblock"))
        {
            return 70;
        }
        return 0;
    }

    private static ToolKind toolKind(String value)
    {
        String compact = compact(normalized(value));
        if (compact.equals("pick") || compact.equals("pickaxe") || compact.contains("pickuses") || compact.contains("pickaxeuses") || (compact.contains("pick") && compact.contains("use")))
        {
            return ToolKind.PICKAXE;
        }
        if (compact.equals("shovel") || compact.contains("shoveluses") || (compact.contains("shovel") && compact.contains("use")))
        {
            return ToolKind.SHOVEL;
        }
        if ((compact.equals("axe") || compact.contains("axeuses") || (compact.contains("axe") && compact.contains("use"))) && compact.contains("pick") == false)
        {
            return ToolKind.AXE;
        }
        return ToolKind.NONE;
    }

    private static String normalized(String value)
    {
        return clean(value).toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
    }

    private static String compact(String value)
    {
        return value == null ? "" : value.replaceAll("[^a-z0-9]", "");
    }

    private static String clean(String value)
    {
        return value == null
                ? ""
                : value
                        .replaceAll("§.", "")
                        .replace('\u00A0', ' ')
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    private static boolean isMinecraftUsername(String value)
    {
        if (value == null || USERNAME_PATTERN.matcher(value).matches() == false)
        {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("total") == false
                && lower.equals("player") == false
                && lower.equals("you") == false
                && lower.equals("your") == false
                && lower.equals("server") == false
                && lower.equals("global") == false;
    }

    private static String normalizeUsername(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeBody(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }

    private record SourceSnapshot(String sourceName, String objectiveTitle, long totalDigs, List<SourceEntry> entries, boolean fromScoreboard)
    {
        String latestMinedAt()
        {
            return this.entries.stream()
                    .map(SourceEntry::lastMinedAt)
                    .filter(value -> value != null && value.isBlank() == false)
                    .max(String::compareTo)
                    .orElse(null);
        }
    }

    private record SourceEntry(String username, long digs, String lastMinedAt)
    {
    }

    private record ObjectiveSnapshot(String objectiveTitle, int priority, List<SourceEntry> rows)
    {
    }

    private enum ToolKind
    {
        NONE,
        PICKAXE,
        SHOVEL,
        AXE
    }

    private static final class ToolUseAccumulator
    {
        private final String username;
        private long pickaxe;
        private long shovel;
        private long axe;

        private ToolUseAccumulator(String username)
        {
            this.username = username;
        }

        private String username()
        {
            return this.username;
        }

        private void putMax(ToolKind kind, long value)
        {
            if (kind == ToolKind.PICKAXE)
            {
                this.pickaxe = Math.max(this.pickaxe, value);
            }
            else if (kind == ToolKind.SHOVEL)
            {
                this.shovel = Math.max(this.shovel, value);
            }
            else if (kind == ToolKind.AXE)
            {
                this.axe = Math.max(this.axe, value);
            }
        }

        private long total()
        {
            return Math.max(0L, this.pickaxe) + Math.max(0L, this.shovel) + Math.max(0L, this.axe);
        }
    }
}
