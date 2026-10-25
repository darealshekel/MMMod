package com.mmm.legacy.settings;

import com.mmm.storage.SessionData;
import com.mmm.tracker.SessionEfficiency;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionUiModelTest {
    private static SessionData session() {
        SessionData session = new SessionData(1000);
        session.endTimeMs = 61000; session.wallDurationMs = 90000;
        session.totalBlocks = 1000; session.bestStreakSeconds = 12;
        session.blockBreakdown.put("minecraft:stone", 700L);
        session.blockBreakdown.put("minecraft:diamond_ore", 200L);
        session.blockBreakdown.put("minecraft:deepslate_diamond_ore", 100L);
        session.miningRateBuckets.add(1000);
        session.efficiency.add(SessionEfficiency.Activity.MINING, 250);
        return session;
    }
    @Test void searchesNamesAndIdsWithAllTokensCaseInsensitively() {
        var rows = SessionUiModel.blocks(session(), "MINECRAFT DIAMOND", SessionUiModel.Filter.ALL, false, Function.identity());
        assertEquals(2, rows.size());
        assertEquals("minecraft:diamond_ore", rows.get(0).getKey());
        assertTrue(SessionUiModel.blocks(session(), "not present", SessionUiModel.Filter.ALL, false, Function.identity()).isEmpty());
    }
    @Test void filtersKeepPost112BlockIdentitiesWithoutMerging() {
        var ores = SessionUiModel.blocks(session(), "", SessionUiModel.Filter.ORES, false, Function.identity());
        assertEquals(List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"), ores.stream().map(java.util.Map.Entry::getKey).toList());
        var other = SessionUiModel.blocks(session(), "", SessionUiModel.Filter.OTHER, false, Function.identity());
        assertEquals("minecraft:stone", other.get(0).getKey());
    }
    @Test void nameSortIsDeterministicAndDoesNotMutateOriginal() {
        SessionData session = session();
        var result = SessionUiModel.blocks(session, "", SessionUiModel.Filter.ALL, true, Function.identity());
        assertEquals("minecraft:deepslate_diamond_ore", result.get(0).getKey());
        assertEquals("minecraft:stone", session.blockBreakdown.keySet().iterator().next());
    }
    @Test void fullSnapshotContainsUnfilteredBreakdownRateAndEfficiency() {
        var session = session();
        var json = SessionUiModel.snapshot(session, "Test world", 60000, 90000, 1000);
        assertEquals(3, json.getAsJsonObject("blockBreakdown").size());
        assertEquals(1, json.getAsJsonArray("miningRateBuckets").size());
        assertEquals(250, json.getAsJsonObject("efficiency").get("MINING").getAsLong());
        assertEquals(59750, json.get("untrackedMs").getAsLong());
        assertEquals(90000, json.get("wallDurationMs").getAsLong());
        assertEquals(60000, json.get("averageBlocksPerHour").getAsLong());
        session.blockBreakdown.clear();
        assertEquals(3, json.getAsJsonObject("blockBreakdown").size());
    }
    @Test void clipboardIncludesAllModernMetricsAndUnavailableLegacyBlocks() {
        String text = SessionUiModel.share(session(), "World", 60000, 90000, 1000);
        for (String label : new String[]{"Session IGT", "Session Time", "Best Hour", "Peak", "Best Streak", "Untracked", "minecraft:deepslate_diamond_ore"})
            assertTrue(text.contains(label), label);
        for (SessionEfficiency.Activity activity : SessionEfficiency.Activity.values()) assertTrue(text.contains(activity.label));
    }
    @Test void percentagesAndRatesDoNotDivideByZeroOrExceedCaps() {
        assertEquals(0, SessionUiModel.percent(500, 0));
        assertEquals(100, SessionUiModel.percent(500, 100));
        assertEquals(25, SessionUiModel.percent(250, 1000));
        assertEquals(0, SessionUiModel.average(100, 0));
        assertEquals(SessionData.MAX_BLOCKS_PER_HOUR, SessionUiModel.average(Long.MAX_VALUE, 1));
    }
    @Test void zeroOrInvalidCountsAreNotRendered() {
        SessionData session = session();
        session.blockBreakdown.put("minecraft:sand", 0L);
        session.blockBreakdown.put("minecraft:dirt", -1L);
        assertEquals(3, SessionUiModel.blocks(session, "", SessionUiModel.Filter.ALL, false, Function.identity()).size());
    }
    @Test void rateGraphHasHeadroomAndDoesNotInflateOneBlockToFullHeight() {
        SessionData session = new SessionData(0);
        assertEquals(1000, SessionUiModel.graphCeiling(session));
        session.miningRateBuckets.add(1);
        assertEquals(1000, SessionUiModel.graphCeiling(session));
        session.miningRateBuckets.set(0, 1000);
        assertEquals(66000, SessionUiModel.graphCeiling(session));
        session.miningRateBuckets.set(0, Integer.MAX_VALUE);
        assertEquals(SessionData.MAX_BLOCKS_PER_HOUR, SessionUiModel.graphCeiling(session));
    }
}
