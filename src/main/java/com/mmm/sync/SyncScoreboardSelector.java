package com.mmm.sync;

import com.mmm.config.Configs;
import com.mmm.storage.WorldSessionContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

public final class SyncScoreboardSelector
{

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

    public static ScoreboardObjective resolveSelectedObjective(Minecraft client)
    {
        String selected = selectedObjectiveName();
        if (selected.isBlank() || client == null || client.world == null)
        {
            return null;
        }

        return client.world.getScoreboard().getObjectives().stream()
                .filter(objective -> objective.getName().equalsIgnoreCase(selected))
                .filter(SyncScoreboardSelector::isManuallySelectable)
                .findFirst()
                .orElse(null);
    }

    public static List<ObjectiveChoice> availableObjectives(Minecraft client)
    {
        if (client == null || client.world == null || client.player == null)
        {
            return List.of();
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        String username = client.player.getGameProfile().getName();
        String sourceName = ScoreboardSourceResolver.displayName(
                WorldSessionContext.getCurrentWorldName(),
                WorldSessionContext.getCurrentWorldInfo());
        List<ObjectiveChoice> choices = new ArrayList<>();
        for (ScoreboardObjective objective : scoreboard.getObjectives())
        {
            if (isManuallySelectable(objective) == false)
            {
                continue;
            }
            ScoreboardParser.Candidate candidate = ScoreboardParser.parseSelected(
                    username,
                    sourceName,
                    objective,
                    scoreboard.getScores(objective));
            if (candidate == null || candidate.snapshot().isValid() == false)
            {
                continue;
            }

            choices.add(new ObjectiveChoice(
                    objective.getName(),
                    clean(objective.getDisplayName()),
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
        String context = clean(objective.getName()) + " " + clean(objective.getDisplayName());
        return isEligibleContext(context);
    }

    static boolean isEligibleContext(String context)
    {
        String cleaned = clean(context);
        if (cleaned.isBlank())
        {
            return false;
        }
        return ScoreboardParser.isMiningEvidence(cleaned)
                && ScoreboardParser.isAllowedSelectionContext(cleaned);
    }

    static boolean isManuallySelectable(ScoreboardObjective objective)
    {
        return objective != null && isManuallySelectableContext(clean(objective.getName()) + " " + clean(objective.getDisplayName()));
    }

    static boolean isManuallySelectableContext(String context)
    {
        String lower = clean(context).toLowerCase(Locale.ROOT);
        // Explicit selection can identify a custom-named total; automatic detection remains strict.
        return ScoreboardParser.isAllowedSelectionContext(lower)
                && !ScoreboardParser.isToolUsesObjective(lower);
    }

    static long readSelectedPlayerTotal(Minecraft client)
    {
        ScoreboardObjective objective = resolveSelectedObjective(client);
        if (objective == null || client == null || client.world == null || client.player == null)
        {
            return 0L;
        }
        return readPlayerScore(client.world.getScoreboard(), objective, client);
    }

    private static long readPlayerScore(Scoreboard scoreboard, ScoreboardObjective objective, Minecraft client)
    {
        String name = client.player.getGameProfile().getName();
        return scoreboard.hasScore(name, objective) ? Math.max(0L, scoreboard.getScore(name, objective).get()) : 0L;
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
