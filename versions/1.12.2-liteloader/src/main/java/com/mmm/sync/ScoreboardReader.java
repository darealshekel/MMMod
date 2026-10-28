package com.mmm.sync;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.scoreboard.ScoreboardObjective;

public final class ScoreboardReader
{
    private static final Pattern FORMATTING = Pattern.compile("\u00a7.");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static Scoreboard cachedScoreboard;
    private static final Map<ScoreboardObjective, ObjectiveCache> OBJECTIVES = new HashMap<>();
    private ScoreboardReader()
    {
    }

    public static List<ObjectiveSnapshot> readObjectives(Minecraft client)
    {
        return readScoreboard(client == null || client.world == null ? null : client.world.getScoreboard());
    }

    static List<ObjectiveSnapshot> readScoreboard(Scoreboard scoreboard)
    {
        if (scoreboard == null)
        {
            cachedScoreboard = null;
            OBJECTIVES.clear();
            return List.of();
        }

        if (cachedScoreboard != scoreboard)
        {
            OBJECTIVES.clear();
            cachedScoreboard = scoreboard;
        }
        ScoreboardObjective sidebar = scoreboard.getDisplayObjective(1);
        OBJECTIVES.keySet().retainAll(scoreboard.getObjectives());
        List<ObjectiveSnapshot> snapshots = new ArrayList<>();
        for (ScoreboardObjective objective : scoreboard.getObjectives())
        {
            ObjectiveCache cache = OBJECTIVES.computeIfAbsent(objective, ignored -> new ObjectiveCache());
            List<ScoreboardLine> lines = cache.read(scoreboard.getScores(objective));
            if (!lines.isEmpty())
            {
                snapshots.add(new ObjectiveSnapshot(ScoreboardParser.detectionTitle(objective),
                        objective.getCriterion().getName(), objective == sidebar, lines));
            }
        }
        return List.copyOf(snapshots);
    }

    static final class ObjectiveCache
    {
        private Map<String, CachedLine> previous = Map.of();
        private List<ScoreboardLine> sorted = List.of();

        List<ScoreboardLine> read(java.util.Collection<ScoreboardScore> entries)
        {
            Map<String, CachedLine> next = new HashMap<>();
            boolean changed = entries.size() != previous.size();
            for (ScoreboardScore entry : entries)
            {
                String raw = displayName(entry);
                CachedLine old = previous.get(entry.getOwner());
                CachedLine current;
                int score = Math.max(0, entry.get());
                if (old != null && java.util.Objects.equals(old.raw(), raw))
                {
                    current = old.line().scoreValue() == score ? old : new CachedLine(raw,
                            new ScoreboardLine(old.line().owner(), old.line().cleaned(), score));
                }
                else
                {
                    current = new CachedLine(raw, new ScoreboardLine(cleanup(entry.getOwner()), cleanup(raw), score));
                }
                changed |= current != old;
                next.put(entry.getOwner(), current);
            }
            previous = next;
            if (changed)
            {
                sorted = entries.stream().map(entry -> next.get(entry.getOwner()).line())
                        .filter(line -> !line.cleaned().isBlank())
                        .sorted(Comparator.comparingInt(ScoreboardLine::scoreValue).reversed()).toList();
            }
            return sorted;
        }
    }

    private record CachedLine(String raw, ScoreboardLine line) {}

    static String displayName(ScoreboardScore score)
    {
        String owner = score.getOwner();
        return Team.getMemberDisplayName(score.getScoreboard().getTeamOfMember(owner), owner);
    }

    static String cleanup(String value)
    {
        if (value == null)
        {
            return "";
        }
        // Most scoreboard owners are plain usernames: avoid allocating matchers for them.
        boolean plain = true;
        for (int index = 0; index < value.length(); index++)
        {
            char character = value.charAt(index);
            if (character <= ' ' || character == '\u00a7' || character == '\u00a0')
            {
                plain = false;
                break;
            }
        }
        if (plain)
        {
            return value;
        }
        String unformatted = FORMATTING.matcher(value).replaceAll("").replace('\u00a0', ' ');
        return WHITESPACE.matcher(unformatted).replaceAll(" ").trim();
    }

    public record ObjectiveSnapshot(String title, String criterionName, boolean sidebar, List<ScoreboardLine> lines) {}

    public record ScoreboardLine(String owner, String cleaned, int scoreValue) {}
}
