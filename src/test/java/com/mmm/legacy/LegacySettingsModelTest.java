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
    @Test void scrollableSidebarAndSettingsControlsFitSupportedGuiSizes() {
        for (int height : new int[]{240, 256, 270, 360, 480, 720, 1080}) {
            int visible = Math.min(Page.values().length, SettingsUi.sidebarCapacity(height));
            assertTrue(visible > 0);
            assertTrue(44 + (visible - 1) * 22 + 20 < height - 27, "height=" + height);
        }
        for (int width : new int[]{320, 360, 427, 640, 854, 1280, 1920}) {
            int panel = Math.min(1060, width - 16);
            int side = width < 460 ? 82 : 106;
            int content = panel - side - 12;
            int card = SettingsUi.cardWidth(content), control = SettingsUi.controlWidth(card);
            assertTrue(card - 16 - control - 30 >= 48, "Label width at " + width);
            assertTrue(SettingsUi.columns(content) * card + (SettingsUi.columns(content) - 1) * 12 <= content - 8);
        }
    }
    @Test void searchMatchesMultipleWordsAndIncludesKeyBindings() {
        assertTrue(filter(allSettings(), Page.GENERAL, "menu opacity").stream().anyMatch(row -> row.config() == Configs.Generic.MENU_OPACITY));
        assertTrue(LegacySettingsModel.hotkeys().contains(com.mmm.config.Hotkeys.OPEN_CONFIG_GUI));
        assertTrue(LegacySettingsModel.hotkeys().contains(com.mmm.config.FeatureToggle.MMM_HUD.getHotkey()));
        assertEquals(LegacySettingsModel.hotkeys().size(), new HashSet<>(LegacySettingsModel.hotkeys()).size());
    }
}
