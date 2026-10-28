package com.mmm.sync;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyScoreReadTest {
    @Test void readingTotalsDoesNotCreateMissingRowsAndReflectsMutations() throws Exception {
        Scoreboard board = new Scoreboard();
        ScoreboardObjective objective = board.createObjective("digs", ScoreboardCriterion.DUMMY);
        var read = PersonalTotalDetector.class.getDeclaredMethod("readScore", Scoreboard.class, ScoreboardObjective.class, String.class);
        read.setAccessible(true);
        assertEquals(0L, read.invoke(null, board, objective, "Tester"));
        assertFalse(board.hasScore("Tester", objective));
        board.getScore("Tester", objective).set(123);
        assertEquals(123L, read.invoke(null, board, objective, "Tester"));
        board.getScore("Tester", objective).set(456);
        assertEquals(456L, read.invoke(null, board, objective, "Tester"));
    }
}
