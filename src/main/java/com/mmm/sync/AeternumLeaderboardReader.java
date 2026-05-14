package com.mmm.sync;

import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.storage.WorldSessionContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;

public final class AeternumLeaderboardReader
{
    private static final long DEBUG_LOG_INTERVAL_MS = 10_000L;
    private static long lastDebugLogMs;

    private AeternumLeaderboardReader()
    {
    }

    public static AeternumLeaderboardSnapshot read(MinecraftClient client)
    {
        List<AeternumLeaderboardSnapshot> snapshots = readAll(client);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    public static List<AeternumLeaderboardSnapshot> readAll(MinecraftClient client)
    {
        if (client == null || client.world == null || client.player == null)
        {
            return List.of();
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        String username = client.player.getGameProfile().getName();
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        String detectedServerName = ScoreboardSourceResolver.displayName(
                worldInfo != null ? worldInfo.displayName() : "",
                worldInfo
        );

        List<ScoreboardParser.Candidate> candidates = new ArrayList<>();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        addCandidate(candidates, ScoreboardParser.parse(
                username,
                detectedServerName,
                sidebar,
                sidebar == null ? List.of() : scoreboard.getScoreboardEntries(sidebar)));

        for (ScoreboardObjective objective : scoreboard.getObjectives())
        {
            if (objective == sidebar)
            {
                continue;
            }
            addCandidate(candidates, ScoreboardParser.parse(
                    username,
                    detectedServerName,
                    objective,
                    scoreboard.getScoreboardEntries(objective)));
        }

        if (candidates.isEmpty())
        {
            debug("Skipping leaderboard sync because no valid mining scoreboard candidates were found.");
            return List.of();
        }

        candidates.sort(Comparator
                .comparingInt(ScoreboardParser.Candidate::objectivePriority).reversed()
                .thenComparing(Comparator.comparingInt(ScoreboardParser.Candidate::confidence).reversed())
                .thenComparing(candidate -> candidate.snapshot().objectiveTitle().toLowerCase(Locale.ROOT)));

        String sourceName = ScoreboardSourceResolver.displayName(
                worldInfo != null ? worldInfo.displayName() : detectedServerName,
                worldInfo
        );
        Map<String, AeternumLeaderboardSnapshot> byObjective = new LinkedHashMap<>();
        for (ScoreboardParser.Candidate candidate : candidates)
        {
            AeternumLeaderboardSnapshot snapshot = candidate.snapshot();
            AeternumLeaderboardSnapshot normalized = new AeternumLeaderboardSnapshot(
                    sourceName,
                    snapshot.objectiveTitle(),
                    snapshot.capturedAtMs(),
                    snapshot.totalDigs(),
                    snapshot.entries()
            );
            byObjective.putIfAbsent(normalizeObjectiveKey(normalized.objectiveTitle()), normalized);
        }

        List<AeternumLeaderboardSnapshot> snapshots = new ArrayList<>(byObjective.values());
        AeternumLeaderboardSnapshot combinedToolsSnapshot = combineToolUseSnapshots(sourceName, snapshots);
        if (combinedToolsSnapshot != null && byObjective.containsKey(normalizeObjectiveKey(combinedToolsSnapshot.objectiveTitle())) == false)
        {
            boolean hasAuthoritativeTotal = snapshots.stream()
                    .anyMatch(snapshot -> ScoreboardParser.objectivePriority(snapshot.objectiveTitle()) >= 70);
            if (hasAuthoritativeTotal)
            {
                snapshots.add(combinedToolsSnapshot);
            }
            else
            {
                snapshots.add(0, combinedToolsSnapshot);
            }
        }

        AeternumLeaderboardSnapshot chosen = snapshots.isEmpty() ? null : snapshots.get(0);
        if (chosen != null)
        {
            debug(
                    "Leaderboard parsed from '{}' entries={} total={} candidateCount={}",
                    chosen.objectiveTitle(),
                    chosen.entries().size(),
                    chosen.totalDigs(),
                    snapshots.size()
            );
        }

        return snapshots;
    }

    private static void addCandidate(List<ScoreboardParser.Candidate> candidates, ScoreboardParser.Candidate candidate)
    {
        if (candidate != null && candidate.snapshot().isValid())
        {
            candidates.add(candidate);
        }
    }

    private static AeternumLeaderboardSnapshot combineToolUseSnapshots(String sourceName, List<AeternumLeaderboardSnapshot> snapshots)
    {
        AeternumLeaderboardSnapshot pickUses = bestPartialSnapshot(snapshots, true);
        AeternumLeaderboardSnapshot shovelUses = bestPartialSnapshot(snapshots, false);
        if (pickUses == null || shovelUses == null)
        {
            return null;
        }

        Map<String, CombinedEntry> combined = new LinkedHashMap<>();
        addPartialEntries(combined, pickUses);
        addPartialEntries(combined, shovelUses);
        if (combined.size() < 3)
        {
            return null;
        }

        List<CombinedEntry> sorted = combined.values().stream()
                .sorted(Comparator.comparingLong(CombinedEntry::digs).reversed().thenComparing(CombinedEntry::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<AeternumLeaderboardEntry> entries = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++)
        {
            CombinedEntry entry = sorted.get(index);
            entries.add(new AeternumLeaderboardEntry(entry.username(), entry.digs(), index + 1));
        }

        long total = partialSnapshotTotal(pickUses) + partialSnapshotTotal(shovelUses);
        return new AeternumLeaderboardSnapshot(
                sourceName,
                "PickUses + ShovelUses",
                Math.max(pickUses.capturedAtMs(), shovelUses.capturedAtMs()),
                total,
                entries
        );
    }

    private static AeternumLeaderboardSnapshot bestPartialSnapshot(List<AeternumLeaderboardSnapshot> snapshots, boolean pick)
    {
        return snapshots.stream()
                .filter(snapshot -> pick
                        ? ScoreboardParser.isPickUsesObjective(snapshot.objectiveTitle())
                        : ScoreboardParser.isShovelUsesObjective(snapshot.objectiveTitle()))
                .max(Comparator
                        .comparingLong(AeternumLeaderboardReader::partialSnapshotTotal)
                        .thenComparingInt(snapshot -> snapshot.entries().size()))
                .orElse(null);
    }

    private static long partialSnapshotTotal(AeternumLeaderboardSnapshot snapshot)
    {
        return snapshot.totalDigs() > 0L
                ? snapshot.totalDigs()
                : snapshot.entries().stream().mapToLong(AeternumLeaderboardEntry::digs).sum();
    }

    private static void addPartialEntries(Map<String, CombinedEntry> combined, AeternumLeaderboardSnapshot snapshot)
    {
        for (AeternumLeaderboardEntry entry : snapshot.entries())
        {
            String key = entry.username().toLowerCase(Locale.ROOT);
            CombinedEntry previous = combined.get(key);
            combined.put(key, new CombinedEntry(
                    previous == null ? entry.username() : previous.username(),
                    (previous == null ? 0L : previous.digs()) + Math.max(0L, entry.digs())
            ));
        }
    }

    private static String normalizeObjectiveKey(String value)
    {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static void debug(String message, Object... args)
    {
        if (Configs.Generic.WEBSITE_SYNC_DEBUG.getBooleanValue() == false)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastDebugLogMs < DEBUG_LOG_INTERVAL_MS)
        {
            return;
        }

        lastDebugLogMs = now;
        MMM.LOGGER.info(message, args);
    }

    private record CombinedEntry(String username, long digs)
    {
    }
}
