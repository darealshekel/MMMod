package com.mmm.sync;

import com.mmm.config.Configs;
import com.mmm.storage.WorldSessionContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

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

    public static Objective resolveSelectedObjective(Minecraft client)
    {
        String selected = selectedObjectiveName();
        if (selected.isBlank() || client == null || client.level == null)
        {
            return null;
        }

        return client.level.getScoreboard().getObjectives().stream()
                .filter(objective -> objective.getName().equalsIgnoreCase(selected))
                .filter(SyncScoreboardSelector::isEligible)
                .findFirst()
                .orElse(null);
    }

    public static List<ObjectiveChoice> availableObjectives(Minecraft client)
    {
        if (client == null || client.level == null || client.player == null)
        {
            return List.of();
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        String username = client.player.getGameProfile().name();
        String sourceName = ScoreboardSourceResolver.displayName(
                WorldSessionContext.getCurrentWorldName(),
                WorldSessionContext.getCurrentWorldInfo());
        List<ObjectiveChoice> choices = new ArrayList<>();
        for (Objective objective : scoreboard.getObjectives())
        {
            if (isEligible(objective) == false)
            {
                continue;
            }
            ScoreboardParser.Candidate candidate = ScoreboardParser.parse(
                    username,
                    sourceName,
                    objective,
                    scoreboard.listPlayerScores(objective));
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

    static boolean isEligible(Objective objective)
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

    static long readSelectedPlayerTotal(Minecraft client)
    {
        Objective objective = resolveSelectedObjective(client);
        if (objective == null || client == null || client.level == null || client.player == null)
        {
            return 0L;
        }
        return readPlayerScore(client.level.getScoreboard(), objective, client);
    }

    private static long readPlayerScore(Scoreboard scoreboard, Objective objective, Minecraft client)
    {
        ReadOnlyScoreInfo byProfile = scoreboard.getPlayerScoreInfo(
                ScoreHolder.fromGameProfile(client.player.getGameProfile()), objective);
        ReadOnlyScoreInfo byName = scoreboard.getPlayerScoreInfo(
                ScoreHolder.forNameOnly(client.player.getGameProfile().name()), objective);
        return Math.max(
                byProfile == null ? 0L : Math.max(0L, byProfile.value()),
                byName == null ? 0L : Math.max(0L, byName.value()));
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
