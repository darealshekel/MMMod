package com.mmm.scoreboard;

import com.mmm.config.Configs;
import java.util.List;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.text.Formatting;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ScoreboardServiceTest {
    @BeforeEach @AfterEach void reset() {
        Configs.Generic.SCOREBOARD_SORTING.resetToDefault();
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.resetToDefault();
        Configs.Generic.SCOREBOARD_SCORE_COMMAS.resetToDefault();
        Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.resetToDefault();
        Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED.resetToDefault();
        ScoreboardService.setNameDecorator(null);
        ScoreboardService.setSidebarProvider(null);
    }
    @Test void teamSidebarOverridesNormalSlotAndFallsBack() {
        Scoreboard board = new Scoreboard();
        var normal = board.createObjective("normal", ScoreboardCriterion.DUMMY);
        var red = board.createObjective("red", ScoreboardCriterion.DUMMY);
        board.setDisplayObjective(1, normal);
        board.setDisplayObjective(3 + Formatting.RED.getId(), red);
        var team = board.addTeam("redteam");
        team.setColor(Formatting.RED);
        board.addMemberToTeam("Miner", "redteam");
        assertSame(red, ScoreboardService.sidebarObjective(board, "Miner"));
        assertSame(normal, ScoreboardService.sidebarObjective(board, "Other"));
        board.setDisplayObjective(3 + Formatting.RED.getId(), null);
        assertSame(normal, ScoreboardService.sidebarObjective(board, "Miner"));
    }
    @Test void hiddenRowsAreExcludedButFakeScoreboardTextIsRetained() {
        var board = new Scoreboard();
        var objective = board.createObjective("test", ScoreboardCriterion.DUMMY);
        board.getScore("A", objective).set(2);
        board.getScore("B", objective).set(5);
        board.getScore("Server rules", objective).set(3);
        board.getScore("#hidden", objective).set(999);
        assertEquals(List.of("B", "Server rules", "A"), ScoreboardService.getSortedEntries(objective).stream().map(ScoreboardScore::getOwner).toList());
        board.getScore("A", objective).set(100);
        assertEquals("A", ScoreboardService.getSortedEntries(objective).getFirst().getOwner());
    }
    @Test void teamFormattingAndTagDecoratorComposeWithoutChangingScores() {
        var board = new Scoreboard();
        var objective = board.createObjective("test", ScoreboardCriterion.DUMMY);
        board.getScore("Miner", objective).set(10000);
        var team = board.addTeam("t");
        team.setPrefix("[T] "); team.setSuffix("!");
        board.addMemberToTeam("Miner", "t");
        ScoreboardService.setNameDecorator((owner, vanilla) -> "[MMM] " + vanilla);
        var row = ScoreboardService.getRenderEntries(objective).getFirst();
        assertEquals("[MMM] [T] Miner!", row.name());
        assertEquals(Formatting.RED + "10,000", row.score());
        assertEquals(10000, row.value());
    }
    @Test void scoresCanBeHiddenAndTabCommasAreIndependent() {
        assertEquals("1,234,567", ScoreboardService.formatTabScore(1234567));
        Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.setBooleanValue(false);
        assertEquals("1234567", ScoreboardService.formatTabScore(1234567));
        assertEquals(Formatting.RED + "1,234,567", ScoreboardService.formatScore(1234567));
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.setBooleanValue(false);
        assertEquals("", ScoreboardService.formatScore(1234567));
        assertEquals("", ScoreboardService.formatTabScore(1234567));
    }
    @Test void abbreviatedMinimumIntegerDoesNotOverflow() {
        assertEquals("-2.15B", ScoreboardService.formatAbbreviated(Integer.MIN_VALUE));
        assertEquals("1M", ScoreboardService.formatAbbreviated(999999));
    }
    @Test void sortOptionsAreAppliedWithoutMutatingTheBoard() {
        var board = new Scoreboard();
        var objective = board.createObjective("test", ScoreboardCriterion.DUMMY);
        board.getScore("A", objective).set(10); board.getScore("B", objective).set(-5);
        Configs.Generic.SCOREBOARD_SORTING.setValueFromString("score_ascending");
        assertEquals("B", ScoreboardService.getSortedEntries(objective).getFirst().getOwner());
        Configs.Generic.SCOREBOARD_SORTING.setValueFromString("name_ascending");
        assertEquals("A", ScoreboardService.getSortedEntries(objective).getFirst().getOwner());
        assertEquals(2, board.getScores(objective).size());
    }
}

