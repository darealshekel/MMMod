package com.mmm.sync;

import java.util.Comparator;
import java.util.List;
import com.mmm.compat.ScoreboardCompat;
import com.mmm.compat.ScoreboardCompat.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

public final class ScoreboardReader
{
    private ScoreboardReader()
    {
    }

    public static List<ObjectiveSnapshot> readObjectives(MinecraftClient client)
    {
        if (client == null || client.world == null)
        {
            return List.of();
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(Scoreboard.SIDEBAR_DISPLAY_SLOT_ID);

        return scoreboard.getObjectives().stream()
                .map(objective -> new ObjectiveSnapshot(
                        cleanup(objective.getDisplayName().getString()),
                        objective.getCriterion().getName(),
                        sidebar != null && objective.equals(sidebar),
                        ScoreboardCompat.entries(objective).stream()
                        .map(ScoreboardReader::toLine)
                        .filter(line -> line != null)
                        .sorted(Comparator.comparingInt(ScoreboardLine::scoreValue).reversed())
                        .toList()))

                        .filter(snapshot -> snapshot.lines().isEmpty() == false)
                        .toList();
    }

    private static ScoreboardLine toLine(Entry entry)
    {
        String owner = cleanup(entry.owner());
        String raw = entry.name().getString();
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
