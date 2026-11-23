package com.mmm.ui;

import com.mmm.leaderboard.BlockRankingCategory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BoardAdapterTest {
    @Test void settingsAreSearchableAndReachableExactlyOnce() {
        var inventory = MmmSettingsScreen.settingsInventory();
        var home = SettingsNavigation.build(inventory);
        var everyday = home.initialCategory();
        assertEquals("Everyday settings", everyday.title());
        assertEquals(List.of("Mining Tracker", "Main HUD", "Move HUD", "Daily Goal", "MMM Chat Messages", "Collections"),
                MmmSettingsScreen.inlineSections(everyday).getFirst().rows().stream()
                        .map(MmmSettingsScreen.SettingRow::label).toList());
        var counts = new IdentityHashMap<MmmSettingsScreen.SettingRow, Integer>();
        for (var category : home.children()) {
            var sections = MmmSettingsScreen.inlineSections(category);
            if ("Tools".equals(category.title())) {
                assertTrue(sections.isEmpty());
                assertEquals(MmmUi.availableTools().stream().map(tool -> tool.label).toList(),
                        category.children().stream().map(SettingsNavigation.Page::title).toList());
                for (var tile : category.children()) {
                    assertEquals(1, tile.rows().size());
                    var action = tile.rows().getFirst();
                    assertEquals(tile.title(), action.label());
                    counts.merge(action, 1, Integer::sum);
                }
            } else {
                assertFalse(sections.isEmpty(), category.title());
                sections.forEach(group -> group.rows().forEach(row -> counts.merge(row, 1, Integer::sum)));
            }
            if ("Digging & goals".equals(category.title())) {
                assertEquals(List.of("Digging helpers", "Goals & notifications"),
                        sections.stream().map(MmmSettingsScreen.VisibleSection::title).toList());
                assertEquals("Flat Digger", sections.getFirst().rows().getFirst().label());
            }
        }
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

    @Test void reopeningRestoresCategoryScopeSortAndFilter(@TempDir Path directory) {
        var file = directory.resolve("board.json");
        assertEquals(new CollectionPreferences(null, 0, 0, true), CollectionPreferences.load(file));
        var saved = new CollectionPreferences(BlockRankingCategory.ALL, 2, 1, false);
        saved.save(file);
        assertEquals(saved, CollectionPreferences.load(file));
    }
}
