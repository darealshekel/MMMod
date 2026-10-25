package com.mmm.sync;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyCustomScoreboardTest {
    @Test void customTitleRequiresExplicitSelectionAndPreservesExactPlayerTotal() {
        Scoreboard board = new Scoreboard();
        var objective = board.createObjective("Counter", ScoreboardCriterion.DUMMY);
        board.getScore("5hekel", objective).set(168139);
        assertFalse(SyncScoreboardSelector.isEligible(objective));
        assertTrue(SyncScoreboardSelector.isManuallySelectable(objective));
        assertNull(ScoreboardParser.parse("5hekel", "Test Source", objective, board.getScores(objective)));
        var candidate = ScoreboardParser.parseSelected("5hekel", "Test Source", objective, board.getScores(objective));
        assertNotNull(candidate);
        assertTrue(candidate.snapshot().isValid());
        assertEquals(168139, candidate.snapshot().entries().getFirst().digs());
        assertEquals(0, candidate.snapshot().totalDigs(), "A player row is not the combined server total");
        board.getScore("5hekel", objective).set(168140);
        candidate = ScoreboardParser.parseSelected("5hekel", "Test Source", objective, board.getScores(objective));
        assertEquals(168140, candidate.snapshot().entries().getFirst().digs());
    }

    @Test void analogDihsIsDetectedWithoutManualSelection() throws Exception {
        Scoreboard board = new Scoreboard();
        var objective = board.createObjective("Dihs", ScoreboardCriterion.DUMMY);
        board.setDisplayObjective(1, objective);
        board.getScore("5hekel", objective).set(183977);
        assertTrue(SyncScoreboardSelector.isEligible(objective));
        var candidate = ScoreboardParser.parse("5hekel", "Analog", objective, board.getScores(objective));
        assertNotNull(candidate);
        assertEquals(183977, candidate.snapshot().entries().getFirst().digs());
        var sanitize = PersonalTotalDetector.class.getDeclaredMethod("sanitizeDirectScore",
                long.class, String.class, String.class, String.class, String.class);
        sanitize.setAccessible(true);
        assertEquals(183977L, sanitize.invoke(null, 183977L, "5hekel", "Dihs", "5hekel", "sidebar"));
        assertEquals(0L, sanitize.invoke(null, 64000000L, "5hekel", "Sprint Distance", "5hekel", "sidebar"));
    }

    @Test void renamedDisplayTitleRetainsMiningEvidenceFromObjectiveName() {
        Scoreboard board = new Scoreboard();
        var objective = board.createObjective("dig-all", ScoreboardCriterion.DUMMY);
        objective.setDisplayName("Analog");
        board.getScore("5hekel", objective).set(183977);
        assertEquals("dig-all", ScoreboardParser.detectionTitle(objective));
        assertTrue(ScoreboardParser.isMiningEvidence(ScoreboardReader.readScoreboard(board).getFirst().title()));
        objective.setDisplayName("Project Digs");
        assertFalse(ScoreboardParser.isMiningEvidence(ScoreboardParser.detectionTitle(objective)));
        assertNull(ScoreboardParser.parse("5hekel", "Analog", objective, board.getScores(objective)));
    }

    @Test void explicitSelectionStillRejectsKnownNonTotalObjectives() {
        for (String name : new String[]{"Sprint", "distance", "daily_digs", "project_blocks", "pick_uses", "deaths"}) {
            Scoreboard board = new Scoreboard();
            var objective = board.createObjective(name, ScoreboardCriterion.DUMMY);
            board.getScore("5hekel", objective).set(64000000);
            assertFalse(SyncScoreboardSelector.isManuallySelectable(objective), name);
            assertNull(ScoreboardParser.parseSelected("5hekel", "Test Source", objective, board.getScores(objective)), name);
        }
    }
}
