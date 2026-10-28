package com.mmm.sync;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import org.junit.jupiter.api.Test;

class ScoreboardReaderTest
{
    @Test
    void cleanupMatchesOriginalIncludingFormattingAndWhitespace()
    {
        for (String text : List.of("Player_1", "\u00a7aPlayer", "  Total\t Digs ", "a\u00a0b", "\u00a7x", "a\nb", "", "\u00a7\nb"))
        {
            String expected = text.replaceAll("\u00a7.", "").replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
            assertEquals(expected, ScoreboardReader.cleanup(text));
        }
        assertEquals("", ScoreboardReader.cleanup(null));
        String name = new String("Player_1");
        assertSame(name, ScoreboardReader.cleanup(name));
    }

    @Test
    void reusesUnchangedRowsButShowsScoreAndDisplayChangesImmediately()
    {
        var cache = new ScoreboardReader.ObjectiveCache();
        var first = cache.read(List.of(entry("One", 20, "One"), entry("Two", 10, "Two")));
        assertSame(first, cache.read(List.of(entry("One", 20, "One"), entry("Two", 10, "Two"))));
        var changed = cache.read(List.of(entry("One", 20, "One"), entry("Two", 30, "[VIP] Two")));
        assertEquals("Two", changed.getFirst().owner());
        assertEquals(30, changed.getFirst().scoreValue());
        assertEquals("[VIP] Two", changed.getFirst().cleaned());
        assertSame(first.getFirst(), changed.get(1));
        assertEquals(1, cache.read(List.of(entry("One", 20, "One"))).size());
        assertTrue(cache.read(List.of()).isEmpty());
        assertEquals(1, cache.read(List.of(entry("New", 5, "New"))).size());
    }

    private static ScoreboardScore entry(String name, int value, String display)
    {
        Scoreboard scoreboard = new Scoreboard();
        var objective = scoreboard.createObjective("digs", ScoreboardCriterion.DUMMY);
        var score = scoreboard.getScore(name, objective);
        score.set(value);
        if (!display.equals(name)) {
            var team = scoreboard.addTeam("label");
            team.setPrefix(display.substring(0, display.length() - name.length()));
            scoreboard.addMemberToTeam(name, "label");
        }
        return score;
    }

    @Test
    void mutableLegacyScoresAndTeamLabelsRefreshWithoutLosingEarlierSnapshots()
    {
        Scoreboard board = new Scoreboard();
        var objective = board.createObjective("digs", ScoreboardCriterion.DUMMY);
        board.setDisplayObjective(1, objective);
        var score = board.getScore("Miner", objective);
        score.set(100);
        var first = ScoreboardReader.readScoreboard(board);
        score.set(150);
        var team = board.addTeam("vip");
        team.setPrefix("\u00a7a[VIP] ");
        team.setSuffix("!");
        board.addMemberToTeam("Miner", "vip");
        var second = ScoreboardReader.readScoreboard(board);
        assertEquals(100, first.getFirst().lines().getFirst().scoreValue());
        assertEquals(150, second.getFirst().lines().getFirst().scoreValue());
        assertEquals("[VIP] Miner!", second.getFirst().lines().getFirst().cleaned());
        assertTrue(second.getFirst().sidebar());
        objective.setDisplayName("Total Digs");
        assertEquals("Total Digs", ScoreboardReader.readScoreboard(board).getFirst().title());
        board.removeObjective(objective);
        assertTrue(ScoreboardReader.readScoreboard(board).isEmpty());
        assertTrue(ScoreboardReader.readScoreboard(null).isEmpty());
    }
}
