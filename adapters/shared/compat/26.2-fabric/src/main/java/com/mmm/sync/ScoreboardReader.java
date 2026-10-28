package com.mmm.sync;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public final class ScoreboardReader
{
    private static final Pattern FORMATTING = Pattern.compile("\u00a7.");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static Scoreboard cachedScoreboard;
    private static final Map<Objective, ObjectiveCache> OBJECTIVES = new HashMap<>();
    private ScoreboardReader()
    {
    }

    public static List<ObjectiveSnapshot> readObjectives(Minecraft client)
    {
        if (client == null || client.level == null)
        {
            cachedScoreboard = null;
            OBJECTIVES.clear();
            return List.of();
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        if (cachedScoreboard != scoreboard)
        {
            OBJECTIVES.clear();
            cachedScoreboard = scoreboard;
        }
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        OBJECTIVES.keySet().retainAll(scoreboard.getObjectives());
        List<ObjectiveSnapshot> snapshots = new ArrayList<>();
        for (Objective objective : scoreboard.getObjectives())
        {
            ObjectiveCache cache = OBJECTIVES.computeIfAbsent(objective, ignored -> new ObjectiveCache());
            List<ScoreboardLine> lines = cache.read(scoreboard.listPlayerScores(objective));
            if (!lines.isEmpty())
            {
                snapshots.add(new ObjectiveSnapshot(cleanup(objective.getDisplayName().getString()),
                        objective.getCriteria().getName(), objective == sidebar, lines));
            }
        }
        return List.copyOf(snapshots);
    }

    static final class ObjectiveCache
    {
        private Map<String, CachedLine> previous = Map.of();
        private List<ScoreboardLine> sorted = List.of();

        List<ScoreboardLine> read(java.util.Collection<PlayerScoreEntry> entries)
        {
            Map<String, CachedLine> next = new HashMap<>();
            boolean changed = entries.size() != previous.size();
            for (PlayerScoreEntry entry : entries)
            {
                String raw = entry.display() != null ? entry.display().getString() : entry.ownerName().getString();
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
