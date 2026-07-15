package com.mmm.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScoreboardStateTest
{
    @BeforeEach
    void resetPage()
    {
        ScoreboardState.resetPage();
    }

    @Test
    void pagesWithoutRunningPastTheLastEntry()
    {
        assertTrue(ScoreboardState.pageDown(31, 15));
        assertEquals(15, ScoreboardState.getPageOffset());
        assertTrue(ScoreboardState.pageDown(31, 15));
        assertEquals(30, ScoreboardState.getPageOffset());
        assertFalse(ScoreboardState.pageDown(31, 15));
        assertTrue(ScoreboardState.pageUp(15));
        assertEquals(15, ScoreboardState.getPageOffset());
    }

    @Test
    void clampsPageWhenTheScoreboardShrinks()
    {
        ScoreboardState.pageDown(60, 15);
        ScoreboardState.pageDown(60, 15);
        ScoreboardState.clampPage(8, 15);
        assertEquals(0, ScoreboardState.getPageOffset());
    }

    @Test
    void abbreviatesOnlyLargeScores()
    {
        assertEquals("999", ScoreboardService.formatAbbreviated(999));
        assertEquals("1.2k", ScoreboardService.formatAbbreviated(1_200));
        assertEquals("2.5M", ScoreboardService.formatAbbreviated(2_500_000));
        assertEquals("-1.5M", ScoreboardService.formatAbbreviated(-1_500_000));
    }
}
