package com.mmm.leaderboard;

import com.mmm.config.Configs;
import com.mmm.scoreboard.ScoreboardService;
import com.mmm.scoreboard.ScoreboardState;
import java.lang.reflect.Field;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeaderboardSidebarTest {
    private int oldSize;
    private boolean oldEnabled, oldScores, oldAbbreviated, oldCommas;
    private String oldKind, oldBlock;
    @BeforeEach void setup() throws Exception {
        oldSize = Configs.Generic.SCOREBOARD_MAX_ENTRIES.getIntegerValue();
        oldEnabled = Configs.Generic.MMM_LEADERBOARD_SIDEBAR.getBooleanValue();
        oldScores = Configs.Generic.SCOREBOARD_SCORES_VISIBLE.getBooleanValue();
        oldAbbreviated = Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED.getBooleanValue();
        oldCommas = Configs.Generic.SCOREBOARD_SCORE_COMMAS.getBooleanValue();
        oldKind = Configs.sidebarLeaderboardKind; oldBlock = Configs.sidebarLeaderboardBlock;
        Configs.Generic.SCOREBOARD_MAX_ENTRIES.setIntegerValue(15);
        Configs.Generic.MMM_LEADERBOARD_SIDEBAR.setBooleanValue(true);
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.setBooleanValue(true);
        Configs.Generic.SCOREBOARD_SCORE_COMMAS.setBooleanValue(true);
        Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED.setBooleanValue(false);
        Configs.sidebarLeaderboardKind = "PLAYERS";
        set("data", data(1)); set("page", 1); set("pending", null); set("error", null); set("lastChunk", false);
        ScoreboardState.resetPage();
    }
    @AfterEach void restore() throws Exception {
        Configs.Generic.SCOREBOARD_MAX_ENTRIES.setIntegerValue(oldSize);
        Configs.Generic.MMM_LEADERBOARD_SIDEBAR.setBooleanValue(oldEnabled);
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.setBooleanValue(oldScores);
        Configs.Generic.SCOREBOARD_SCORE_COMMAS.setBooleanValue(oldCommas);
        Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED.setBooleanValue(oldAbbreviated);
        Configs.sidebarLeaderboardKind = oldKind; Configs.sidebarLeaderboardBlock = oldBlock;
        set("data", null); set("page", 1); set("pending", null); set("error", null); set("lastChunk", false);
        ScoreboardState.resetPage(); ScoreboardService.setSidebarProvider(null);
    }
    @Test void pagesWithinAndBetweenWebsitePagesWithoutTruncatingLongTotals() throws Exception {
        var rows = LeaderboardSidebar.entries();
        assertEquals(15, rows.size());
        assertEquals("#1 Miner1", rows.getFirst().name());
        assertEquals("9,000,000,001", rows.getFirst().score());
        assertTrue(LeaderboardSidebar.changePage(true));
        assertEquals(10, LeaderboardSidebar.entries().size());
        assertEquals("#16 Miner16", LeaderboardSidebar.entries().getFirst().name());
        assertTrue(LeaderboardSidebar.changePage(true));
        assertNull(get("data")); assertEquals(2, get("page")); assertEquals(0, ScoreboardState.getPageOffset());
        set("data", data(2));
        assertTrue(LeaderboardSidebar.changePage(false));
        assertEquals(1, get("page")); assertEquals(true, get("lastChunk"));
    }
    @Test void hidesScoresAndHonorsZeroRowLimit() {
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.setBooleanValue(false);
        assertTrue(LeaderboardSidebar.entries().stream().allMatch(row -> row.score().isEmpty()));
        Configs.Generic.SCOREBOARD_MAX_ENTRIES.setIntegerValue(0);
        assertTrue(LeaderboardSidebar.entries().isEmpty());
        assertFalse(LeaderboardSidebar.changePage(true));
    }
    @Test void providerDoesNotRequireAVanillaObjectiveAndRespectsToggle() {
        LeaderboardSidebar.register();
        assertNotNull(ScoreboardService.activeSidebar());
        assertEquals("MMM Players | 1/3", ScoreboardService.activeSidebar().title());
        Configs.Generic.MMM_LEADERBOARD_SIDEBAR.setBooleanValue(false);
        assertNull(ScoreboardService.activeSidebar());
    }
    @Test void malformedSelectionFallsBackAndCachedFailureIsExplicit() throws Exception {
        Configs.sidebarLeaderboardKind = "NOT_A_KIND"; Configs.sidebarLeaderboardBlock = "broken";
        assertEquals(LeaderboardData.Kind.PLAYERS, LeaderboardSidebar.selection().kind());
        assertEquals("minecraft:stone", LeaderboardSidebar.selection().block());
        set("error", "Cached - retrying");
        assertTrue(LeaderboardSidebar.title().contains("Cached"));
        assertFalse(LeaderboardSidebar.entries().isEmpty());
        set("data", null); set("error", "Unavailable - retrying");
        assertEquals("Unavailable - retrying", LeaderboardSidebar.entries().getFirst().name());
    }
    private static LeaderboardData.Page data(int page) {
        return new LeaderboardData.Page(IntStream.rangeClosed(1, 25)
                .mapToObj(i -> new LeaderboardData.Row(i, "Miner" + i, 9_000_000_000L + i, "world")).toList(), page, 3, 70, 100_000_000_000L);
    }
    private static Object get(String name) throws Exception { Field f = LeaderboardSidebar.class.getDeclaredField(name); f.setAccessible(true); return f.get(null); }
    private static void set(String name, Object value) throws Exception { Field f = LeaderboardSidebar.class.getDeclaredField(name); f.setAccessible(true); f.set(null, value); }
}
