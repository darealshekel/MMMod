package com.mmm.ui;

import com.mmm.leaderboard.BlockRankingCategory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BoardAdapterTest {
    @Test void settingsAreSearchableAndReachableExactlyOnce() {
        var inventory = MmmSettingsScreen.settingsInventory();
        var home = MmmSettingsScreen.buildSettingsPages(inventory);
        assertEquals("Everyday settings", home.children().getFirst().title());
        var counts = new IdentityHashMap<MmmSettingsScreen.SettingRow, Integer>();
        visit(home, counts);
        var labels = new ArrayList<String>();
        for (var section : inventory) for (var row : section.rows()) {
            if (row.heading()) continue;
            labels.add(row.label());
            assertEquals(1, counts.get(row), row.label());
            assertTrue(MmmSettingsScreen.searchSections(inventory, row.label()).stream()
                    .anyMatch(match -> match.rows().contains(row)), row.label());
        }
        assertTrue(labels.containsAll(java.util.List.of("Website Link", "Move HUD", "Daily Goal", "MMM Chat Messages")));
        assertEquals(BoardPlatform.SPEED_GRAPH, labels.contains("Speed Graph"));
        assertEquals(BoardPlatform.SPEED_GRAPH, labels.contains("Rank Overtake Estimate"));
        assertEquals(BoardPlatform.SPEED_GRAPH, labels.contains("Ignored Players"));
    }

    private static void visit(MmmSettingsScreen.SettingsPage page, IdentityHashMap<MmmSettingsScreen.SettingRow, Integer> count) {
        page.rows().forEach(row -> count.merge(row, 1, Integer::sum));
        page.children().forEach(child -> visit(child, count));
    }

    @Test void reopeningRestoresCategoryScopeSortAndFilter(@TempDir Path directory) {
        var file = directory.resolve("board.json");
        assertEquals(new CollectionPreferences(null, 0, 0, true), CollectionPreferences.load(file));
        var saved = new CollectionPreferences(BlockRankingCategory.ALL, 2, 1, false);
        saved.save(file);
        assertEquals(saved, CollectionPreferences.load(file));
    }
}
