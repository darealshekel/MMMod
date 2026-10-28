package com.mmm.sync;

import com.mmm.config.Configs;
import com.mmm.storage.WorldSessionContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

public final class SyncScoreboardSelector
{
    private static final List<String> EXCLUDED_MARKERS = List.of(
            "project", "goal", "session", "daily", "weekly", "challenge", "quest",
            "sprint", "distance", "playtime", "death", "kill", "balance", "money");

    private SyncScoreboardSelector()
    {
    }

    public static String currentSourceKey()
    {
        WorldSessionContext.WorldInfo worldInfo = WorldSessionContext.getCurrentWorldInfo();
        return worldInfo == null ? "" : ScoreboardSourceResolver.sourceKey(worldInfo.displayName(), worldInfo);
    }

    public static String selectedObjectiveName()
    {
        return Configs.getSourceSyncObjective(currentSourceKey());
    }

    public static void selectObjective(String objectiveName)
    {
        Configs.setSourceSyncObjective(currentSourceKey(), objectiveName);
        Configs.saveToFile();
        DigsSyncManager.onSyncScoreboardSelectionChanged();
    }

    public static boolean hasManualSelection()
    {
        return selectedObjectiveName().isBlank() == false;
    }

    public static ScoreboardObjective resolveSelectedObjective(MinecraftClient client)
    {
        String selected = selectedObjectiveName();
        if (selected.isBlank() || client == null || client.world == null)
        {
            return null;
        }

        return client.world.getScoreboard().getObjectives().stream()
                .filter(objective -> objective.getName().equalsIgnoreCase(selected))
                .filter(SyncScoreboardSelector::isEligible)
                .findFirst()
                .orElse(null);
    }

    public static List<ObjectiveChoice> availableObjectives(MinecraftClient client)
    {
        if (client == null || client.world == null || client.player == null)
        {
            return List.of();
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        String username = client.player.getGameProfile().name();
        String sourceName = ScoreboardSourceResolver.displayName(
                WorldSessionContext.getCurrentWorldName(),
                WorldSessionContext.getCurrentWorldInfo());
        List<ObjectiveChoice> choices = new ArrayList<>();
        for (ScoreboardObjective objective : scoreboard.getObjectives())
        {
            if (isEligible(objective) == false)
            {
                continue;
            }
            ScoreboardParser.Candidate candidate = ScoreboardParser.parse(
                    username,
                    sourceName,
                    objective,
                    scoreboard.getScoreboardEntries(objective));
            if (candidate == null || candidate.snapshot().isValid() == false)
            {
                continue;
            }

            choices.add(new ObjectiveChoice(
                    objective.getName(),
                    clean(objective.getDisplayName().getString()),
                    candidate.snapshot().entries().size(),
                    candidate.snapshot().totalDigs(),
                    readPlayerScore(scoreboard, objective, client)));
        }

        choices.sort(Comparator
                .comparingLong(ObjectiveChoice::sourceTotal).reversed()
                .thenComparing(ObjectiveChoice::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(choices);
    }

    static boolean isEligible(ScoreboardObjective objective)
    {
        if (objective == null)
        {
            return false;
        }
        String context = clean(objective.getName()) + " " + clean(objective.getDisplayName().getString());
        return isEligibleContext(context);
    }

    static boolean isEligibleContext(String context)
    {
        String cleaned = clean(context);
        if (cleaned.isBlank())
        {
            return false;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        return ScoreboardParser.isMiningEvidence(cleaned)
                && EXCLUDED_MARKERS.stream().noneMatch(lower::contains);
    }

    static long readSelectedPlayerTotal(MinecraftClient client)
    {
        ScoreboardObjective objective = resolveSelectedObjective(client);
        if (objective == null || client == null || client.world == null || client.player == null)
        {
            return 0L;
        }
        return readPlayerScore(client.world.getScoreboard(), objective, client);
    }

    private static long readPlayerScore(Scoreboard scoreboard, ScoreboardObjective objective, MinecraftClient client)
    {
        ReadableScoreboardScore byProfile = scoreboard.getScore(
                ScoreHolder.fromProfile(client.player.getGameProfile()), objective);
        ReadableScoreboardScore byName = scoreboard.getScore(
                ScoreHolder.fromName(client.player.getGameProfile().name()), objective);
        return Math.max(
                byProfile == null ? 0L : Math.max(0L, byProfile.getScore()),
                byName == null ? 0L : Math.max(0L, byName.getScore()));
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
    }

    public record ObjectiveChoice(
            String objectiveName,
            String displayName,
            int playerCount,
            long sourceTotal,
            long playerTotal)
    {
    }
}
