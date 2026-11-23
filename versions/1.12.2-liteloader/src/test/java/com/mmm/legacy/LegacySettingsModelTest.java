package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.value.ConfigColor;
import com.mmm.legacy.settings.SettingsUi;
import com.mmm.legacy.settings.LegacyUiRegistry;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.mmm.legacy.LegacySettingsModel.*;

class LegacySettingsModelTest {
    @Test void boardKeepsEveryAvailableSettingAndBindingExactlyOnce() {
        LegacyNavigation.register();
        var board = new SettingsBoard();
        var reachable = board.home.children().stream().flatMap(p -> p.rowGroups().stream())
                .flatMap(group -> group.rows().stream()).toList();
        var everyday = board.home.initialCategory();
        assertEquals("Everyday settings", everyday.title());
        assertEquals(java.util.List.of("Mining Tracker", "Main HUD", "Move HUD", "Daily Goal", "MMM Chat Messages"),
                everyday.rows().stream().map(SettingsBoard.Row::label).toList());
        var hud = board.home.children().stream().filter(p -> "HUD".equals(p.title())).findFirst().orElseThrow();
        assertTrue(hud.rowGroups().size() > 1);
        assertTrue(hud.rowGroups().stream().flatMap(g -> g.rows().stream()).anyMatch(r -> r.config()==Configs.Generic.HUD_X));
        for (var setting : allSettings()) if (!LegacyUiRegistry.available(setting.capability())) {
            assertTrue(reachable.stream().noneMatch(r -> r.config()==setting.config()), setting.label());
            assertTrue(board.search(setting.label()).stream().noneMatch(r -> r.config()==setting.config()), setting.label());
        }
        for (var setting : settings())
            assertEquals(1, reachable.stream().filter(r -> r.config() == setting.config()).count(), setting.label());
        for (var key : hotkeys())
            assertEquals(1, board.search(key.getPrettyName()).stream().filter(r -> r.key() == key).count(), key.getName());
        var tools = board.home.children().stream().filter(p -> "Tools".equals(p.title())).findFirst().orElseThrow();
        assertEquals(1, board.home.children().stream().filter(p -> "Tools".equals(p.title())).count());
        assertFalse(board.home.children().stream().anyMatch(p -> "Hotkeys".equals(p.title()) || "Tool shortcuts".equals(p.title())));
        assertTrue(tools.rows().isEmpty());
        var expected = java.util.Arrays.stream(com.mmm.ui.ToolDirectory.values())
                .filter(t -> t != com.mmm.ui.ToolDirectory.PROFILE).map(t -> t.label).toList();
        assertEquals(expected, tools.children().stream().map(p -> p.title()).toList());
        for (var child : tools.children()) {
            assertEquals("Tools", child.source().title());
            assertEquals(1, child.rows().size());
            assertEquals(child.title(), child.rows().getFirst().label());
            assertNotNull(child.rows().getFirst().action());
            assertEquals(1, reachable.stream().filter(r -> child.title().equals(r.label()) && r.action()!=null).count());
        }
        assertTrue(board.search("hour best main").stream().anyMatch(r -> r.config() == Configs.Generic.HOURLY_STATS_VISIBLE));
        assertTrue(board.search("Website Link").stream().anyMatch(r -> r.action() != null));
        for (var registered : LegacyUiRegistry.entries())
            assertTrue(reachable.stream().anyMatch(r -> registered.label().equals(r.label()) && r.action() != null));
    }
    @Test void collectionPreferencesRoundTripInTheNormalConfig() {
        var prefs = java.util.List.of(Configs.Generic.COLLECTION_FILTER, Configs.Generic.COLLECTION_SORT,
                Configs.Generic.COLLECTION_CATEGORY, Configs.Generic.COLLECTION_GLOBAL);
        var snapshot = new com.google.gson.JsonObject();
        var updated = new com.google.gson.JsonObject();
        com.mmm.config.MmmConfigIO.writeConfigBase(snapshot, "Generic", prefs);
        try {
            Configs.Generic.COLLECTION_FILTER.setIntegerValue(2); Configs.Generic.COLLECTION_SORT.setIntegerValue(1);
            Configs.Generic.COLLECTION_CATEGORY.setIntegerValue(4); Configs.Generic.COLLECTION_GLOBAL.setBooleanValue(false);
            com.mmm.config.MmmConfigIO.writeConfigBase(updated, "Generic", Configs.Generic.PERSISTED_OPTIONS);
            prefs.forEach(c -> c.resetToDefault());
            com.mmm.config.MmmConfigIO.readConfigBase(updated, "Generic", prefs);
            assertEquals(2, Configs.Generic.COLLECTION_FILTER.getIntegerValue());
            assertEquals(1, Configs.Generic.COLLECTION_SORT.getIntegerValue());
            assertEquals(4, Configs.Generic.COLLECTION_CATEGORY.getIntegerValue());
            assertFalse(Configs.Generic.COLLECTION_GLOBAL.getBooleanValue());
        } finally { com.mmm.config.MmmConfigIO.readConfigBase(snapshot, "Generic", prefs); }
    }
    @Test void completeSettingsHaveOneControlEachAndRespectCapabilityGates() {
        var ids = new HashSet<String>();
        for (var row : allSettings()) {
            assertTrue(ids.add(row.config().getName()), row.config().getName());
            assertFalse(row.label().isBlank());
            assertFalse(row.section().isBlank());
            assertEquals(LegacyUiRegistry.available(row.capability()), settings().contains(row));
        }
        assertTrue(allSettings().stream().anyMatch(row -> row.config() == Configs.Generic.TIMER_CREDITS));
        assertTrue(allSettings().stream().anyMatch(row -> row.config() == Configs.Generic.MENU_OPACITY && row.slider()));
        assertTrue(allSettings().stream().anyMatch(row -> row.config() == Configs.Generic.HUD_X));
        assertTrue(allSettings().stream().anyMatch(row -> row.config() == Configs.Generic.HUD_TEXT_SHADOW && row.page() == Page.COLORS));
        assertTrue(allSettings().stream().anyMatch(row -> row.config() == Configs.Generic.BREAKING_INDICATOR_START_HEX_COLOR && row.page() == Page.VISUALS));
    }
    @Test void searchFindsSettingsOutsideCurrentPageAndHandlesCaseAndSpaces() {
        var rows = filter(settings(), Page.GENERAL, "  SHADOW  ");
        assertTrue(rows.stream().anyMatch(row -> row.config() == Configs.Generic.HUD_TEXT_SHADOW));
        assertTrue(filter(settings(), Page.HUD, "not a real setting").isEmpty());
        assertTrue(filter(settings(), Page.COLORS, "").stream().allMatch(row -> row.page() == Page.COLORS));
    }
    @Test void hourlySettingControlsOnlyTheMainHudLine() {
        var hourly = settings().stream().filter(row -> row.config() == Configs.Generic.HOURLY_STATS_VISIBLE).toList();
        assertEquals(1, hourly.size());
        var row = hourly.getFirst();
        assertEquals(Page.HUD, row.page());
        assertEquals("Main HUD", row.section());
        assertEquals("Hour / Best Hour", row.label());
        assertEquals("Show the Hour / Best Hour line in the main HUD.", row.description());
        assertTrue(filter(settings(), Page.GENERAL, "hour best main").contains(row));
        assertTrue(allSettings().stream().noneMatch(setting -> setting.config() == Configs.Generic.HOURLY_STATS_X
                || setting.config() == Configs.Generic.HOURLY_STATS_Y
                || setting.config() == Configs.Generic.HOURLY_STATS_SCALE));
        assertTrue(filter(allSettings(), Page.GENERAL, "hourlyStatsX").isEmpty());
        assertTrue(filter(allSettings(), Page.GENERAL, "hourlyStatsY").isEmpty());
        assertTrue(filter(allSettings(), Page.GENERAL, "hourlyStatsScale").isEmpty());
    }
    @Test void settingsControlsLeaveRoomForTheirLabels() {
        for (int width : new int[]{320, 360, 427, 640, 854, 1280, 1920}) {
            int panel = com.mmm.ui.BoardEditorStyle.contentWidth(width, 920);
            int content = panel - (panel >= 475 ? 122 : 72) - 7;
            assertTrue(content - SettingsUi.controlWidth(content) - 38 >= 48, "Label width at " + width);
        }
    }
    @Test void searchMatchesMultipleWordsAndIncludesKeyBindings() {
        assertTrue(filter(allSettings(), Page.GENERAL, "menu opacity").stream().anyMatch(row -> row.config() == Configs.Generic.MENU_OPACITY));
        assertTrue(LegacySettingsModel.hotkeys().contains(com.mmm.config.Hotkeys.OPEN_CONFIG_GUI));
        assertTrue(LegacySettingsModel.hotkeys().contains(com.mmm.config.FeatureToggle.MMM_HUD.getHotkey()));
        assertEquals(LegacySettingsModel.hotkeys().size(), new HashSet<>(LegacySettingsModel.hotkeys()).size());
    }
}
