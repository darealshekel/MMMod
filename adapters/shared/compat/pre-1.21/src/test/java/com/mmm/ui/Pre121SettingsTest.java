package com.mmm.ui;

import com.mmm.config.Configs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Pre121SettingsTest {
    @Test void originalSettingsRemainReachableAndSearchableByConfigIdentity() {
        var original = new ArrayList<SettingsBoard.SettingsSection>();
        MmmSettingsScreen.createSections(original);
        var sections = SettingsBoard.settingsInventory();
        var root = SettingsBoard.buildSettingsPages(sections);
        assertTrue(root.rows().isEmpty());
        assertEquals("Everyday settings", root.children().get(0).title());
        var leaves = new ArrayList<SettingsBoard.SettingRow>();
        collect(root, leaves);
        for (var section : original) for (var row : section.rows()) {
            if (row.heading()) continue;
            assertEquals(1, leaves.stream().filter(found -> found.label().equals(row.label())
                    && found.kind() == row.kind() && found.config() == row.config()).count(), row.label());
            String query = row.config() == null ? row.label() : row.configName();
            assertTrue(SettingsBoard.searchSections(sections, query).stream().flatMap(hit -> hit.rows().stream())
                    .anyMatch(found -> found.label().equals(row.label()) && found.config() == row.config()), query);
        }
        for (String action : List.of("Website Link", "Collections", "Move HUD", "Hotkeys", "Profile"))
            assertTrue(leaves.stream().anyMatch(row -> row.label().equals(action) && row.kind() == SettingsBoard.ControlKind.ACTION), action);
    }

    @Test void collectionPreferencesUseThePersistedConfigRegistry() {
        assertTrue(Configs.Generic.PERSISTED_OPTIONS.containsAll(List.of(Configs.Generic.COLLECTION_CATEGORY, Configs.Generic.COLLECTION_FILTER,
                Configs.Generic.COLLECTION_SORT, Configs.Generic.COLLECTION_GLOBAL)));
    }

    @Test void toolsAreDirectBoardTopicsAndSearchFindsTheSameActions() {
        var available = java.util.EnumSet.allOf(ToolDirectory.class);
        if (getClass().getResource("/com/mmm/ui/LeaderboardScreen.class") == null)
            available.remove(ToolDirectory.LEADERBOARDS);
        assertEquals(available, MmmUi.TOOL_SCREENS.keySet());
        var sections = SettingsBoard.settingsInventory();
        var root = SettingsBoard.buildSettingsPages(sections);
        var board = root.children().stream().filter(page -> page.title().equals("Tools")).findFirst().orElseThrow();
        assertNull(SettingsBoard.toolForPage(board));
        assertEquals(List.copyOf(available), board.children().stream().map(SettingsBoard::toolForPage).toList());
        for (var page : board.children()) {
            var tool = SettingsBoard.toolForPage(page);
            assertTrue(page.children().isEmpty());
            assertEquals(1, page.rows().size());
            var action = page.rows().get(0);
            assertEquals(tool.label, action.label());
            assertEquals(tool.description, action.description());
            assertTrue(SettingsBoard.searchSections(sections, tool.label).stream()
                    .anyMatch(hit -> hit.section() == page.source() && hit.rows().contains(action)));
        }
        assertNull(ToolDirectory.find("Website Link"));
    }

    @Test void destinationsUseBoardControls() {
        for (Class<?> type : List.of(com.mmm.gui.GuiConfigs.class, com.mmm.hud.SessionHistoryScreen.class,
                com.mmm.hud.SummaryScreen.class, com.mmm.scoreboard.ScoreboardScreen.class,
                com.mmm.scoreboard.ScoreboardEditScreen.class, com.mmm.scoreboard.ScoreboardRecordsScreen.class,
                PlayerProfileScreen.class, ProjectManagerScreen.class, GoalSoundSettingsScreen.class,
                PerimeterBlockListScreen.class, SyncScoreboardScreen.class, ProgressShareScreen.class))
            assertTrue(BoardChrome.Page.class.isAssignableFrom(type), type.getSimpleName());
    }

    private static void collect(SettingsBoard.SettingsPage page, List<SettingsBoard.SettingRow> rows) {
        assertTrue(page.rows().isEmpty() || page.children().isEmpty());
        rows.addAll(page.rows());
        page.children().forEach(child -> collect(child, rows));
    }
}
