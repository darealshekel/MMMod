package com.mmm.compat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.text.Text;

class ScoreboardCompatTest
{
    @Test
    void readingAMissingPlayerDoesNotCreateAScore()
    {
        var scoreboard = new Scoreboard();
        var objective = scoreboard.addObjective("digs", ScoreboardCriterion.DUMMY, Text.literal("Digs"), ScoreboardCriterion.RenderType.INTEGER);
        assertEquals(0, ScoreboardCompat.score(objective, "Missing"));
        assertFalse(scoreboard.playerHasObjective("Missing", objective));
        scoreboard.getPlayerScore("Miner", objective).setScore(42);
        assertEquals(42, ScoreboardCompat.score(objective, "Miner"));
        assertEquals(1, ScoreboardCompat.entries(objective).size());
    }

    @Test
    void formatsTabScoresWithMatchingWidthAndColorWithoutTouchingLabels()
    {
        assertEquals("123,456", ScoreboardCompat.formatTabScore("123456", true));
        assertEquals("\u00a7e123,456", ScoreboardCompat.formatTabScore("\u00a7e123456", true));
        assertEquals("123456", ScoreboardCompat.formatTabScore("123456", false));
        assertEquals("Player", ScoreboardCompat.formatTabScore("Player", true));
        assertEquals("-1,234", ScoreboardCompat.formatTabScore("-1234", true));
    }
}
