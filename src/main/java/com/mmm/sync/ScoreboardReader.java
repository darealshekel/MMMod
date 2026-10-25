package com.mmm.sync;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
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

    public static List<ObjectiveSnapshot> readObjectives(MinecraftClient client)
    {
        if (client == null || client.world == null)
        {
            cachedScoreboard = null;
            OBJECTIVES.clear();
            return List.of();
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        if (cachedScoreboard != scoreboard)
        {
            OBJECTIVES.clear();
            cachedScoreboard = scoreboard;
        }
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        OBJECTIVES.keySet().retainAll(scoreboard.getObjectives());
        List<ObjectiveSnapshot> snapshots = new ArrayList<>();
        for (ScoreboardObjective objective : scoreboard.getObjectives())
        {
            ObjectiveCache cache = OBJECTIVES.computeIfAbsent(objective, ignored -> new ObjectiveCache());
            List<ScoreboardLine> lines = cache.read(scoreboard.getScoreboardEntries(objective));
            if (!lines.isEmpty())
            {
                snapshots.add(new ObjectiveSnapshot(cleanup(objective.getDisplayName().getString()),
                        objective.getCriterion().getName(), objective == sidebar, lines));
            }
        }
        return List.copyOf(snapshots);
    }

    static final class ObjectiveCache
    {
        private Map<String, CachedLine> previous = Map.of();
        private List<ScoreboardLine> sorted = List.of();

        List<ScoreboardLine> read(java.util.Collection<ScoreboardEntry> entries)
        {
            Map<String, CachedLine> next = new HashMap<>();
            boolean changed = entries.size() != previous.size();
            for (ScoreboardEntry entry : entries)
            {
                String raw = entry.display() != null ? entry.display().getString() : entry.name().getString();
                raw = raw == null || raw.isBlank() ? entry.owner() : raw;
                CachedLine old = previous.get(entry.owner());
                CachedLine current;
                int score = Math.max(0, entry.value());
                if (old != null && java.util.Objects.equals(old.raw(), raw))
                {
                    current = old.line().scoreValue() == score ? old : new CachedLine(raw,
                            new ScoreboardLine(old.line().owner(), old.line().cleaned(), score));
                }
                else
                {
                    current = new CachedLine(raw, new ScoreboardLine(cleanup(entry.owner()), cleanup(raw), score));
                }
                changed |= current != old;
                next.put(entry.owner(), current);
            }
            previous = next;
            if (changed)
            {
                sorted = entries.stream().map(entry -> next.get(entry.owner()).line())
                        .filter(line -> !line.cleaned().isBlank())
                        .sorted(Comparator.comparingInt(ScoreboardLine::scoreValue).reversed()).toList();
            }
            return sorted;
        }
    }

    private record CachedLine(String raw, ScoreboardLine line) {}

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
