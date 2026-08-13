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
}
