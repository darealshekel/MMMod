package com.mmm.compat;

import java.util.List;

import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.text.Text;

public final class ScoreboardCompat
{
    private ScoreboardCompat()
    {
    }

    public static List<Entry> entries(ScoreboardObjective objective)
    {
        if (objective == null)
        {
            return List.of();
        }
        return objective.getScoreboard().getAllPlayerScores(objective).stream()
                .map(score -> new Entry(score.getPlayerName(), Text.literal(score.getPlayerName()), score.getScore()))
                .toList();
    }

    public static int score(ScoreboardObjective objective, String playerName)
    {
        if (objective == null || playerName == null || playerName.isBlank())
        {
            return 0;
        }
        if (!objective.getScoreboard().playerHasObjective(playerName, objective))
        {
            return 0;
        }
        ScoreboardPlayerScore score = objective.getScoreboard().getPlayerScore(playerName, objective);
        return score == null ? 0 : score.getScore();
    }

    public record Entry(String owner, Text name, int value)
    {
        public boolean hidden()
        {
            return this.owner == null || this.owner.startsWith("#");
        }
    }

    public static String formatTabScore(String value, boolean commas)
    {
        if (!commas || value == null) return value;
        String prefix = value.startsWith("\u00a7") && value.length() >= 2 ? value.substring(0, 2) : "";
        try
        {
            return prefix + String.format(java.util.Locale.US, "%,d", Integer.parseInt(value.substring(prefix.length())));
        }
        catch (NumberFormatException ignored)
        {
            return value;
        }
    }
}
