package com.mmm.sync;

import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public final class ScoreboardReader
{
    private ScoreboardReader()
    {
    }

    public static List<ObjectiveSnapshot> readObjectives(Minecraft client)
    {
        if (client == null || client.level == null)
        {
            return List.of();
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

        return scoreboard.getObjectives().stream()
                .map(objective -> new ObjectiveSnapshot(
                        cleanup(objective.getDisplayName().getString()),
                        objective.getCriteria().getName(),
                        sidebar != null && objective.equals(sidebar),
                        scoreboard.listPlayerScores(objective).stream()
                        .map(ScoreboardReader::toLine)
                        .filter(line -> line != null)
                        .sorted(Comparator.comparingInt(ScoreboardLine::scoreValue).reversed())
                        .toList()))

                        .filter(snapshot -> snapshot.lines().isEmpty() == false)
                        .toList();
    }

    private static ScoreboardLine toLine(PlayerScoreEntry entry)
    {
        String owner = cleanup(entry.owner());
        String raw = entry.display() != null ? entry.display().getString() : entry.ownerName().getString();
        if (raw == null || raw.isBlank())
        {
            raw = owner;
        }

        if (raw == null || raw.isBlank())
        {
            return null;
        }

        String cleaned = cleanup(raw);
        if (cleaned.isBlank())
        {
            return null;
        }

        return new ScoreboardLine(owner, cleaned, Math.max(0, entry.value()));
    }

    private static String cleanup(String value)
    {
        return value == null
                ? ""
                : value
                        .replaceAll("§.", "")
                        .replace('\u00A0', ' ')
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    public record ObjectiveSnapshot(String title, String criterionName, boolean sidebar, List<ScoreboardLine> lines) {}

    public record ScoreboardLine(String owner, String cleaned, int scoreValue) {}
}
