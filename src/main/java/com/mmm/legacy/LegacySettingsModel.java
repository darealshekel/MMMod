package com.mmm.legacy;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.config.Hotkeys;
import com.mmm.config.value.*;
import com.mmm.hotkey.MmmHotkey;
import com.mmm.legacy.settings.LegacyUiRegistry;
import com.mmm.legacy.settings.SettingsUi;
import java.util.*;

final class LegacySettingsModel {
    enum Page {
        GENERAL("General"), SYNC("Sync"), NOTIFICATIONS("Notifications"), SOCIAL("Social"),
        HUD("HUD"), COLORS("Colors"), VISUALS("Visuals"), BLOCK_ESP("Block ESP"),
        SESSIONS("Sessions"), PROJECTS("Projects"), TIMER("Timer"), HOTKEYS("Hotkeys");
        final String label;
        Page(String label) { this.label = label; }
    }
    record Setting(Page page, String section, String label, IConfigBase config, boolean slider, String capability) {
        String description() {
            return config == Configs.Generic.HOURLY_STATS_VISIBLE
                    ? "Show the Hour / Best Hour line in the main HUD." : config.getComment();
        }
        boolean matches(String query) {
            return SettingsUi.matches(label + " " + section + " " + page.label + " " + config.getName() + " " + description(), query);
        }
    }
    static List<Setting> settings() { return allSettings().stream().filter(s -> LegacyUiRegistry.available(s.capability)).toList(); }
    static List<Setting> allSettings() {
        List<Setting> rows = new ArrayList<>();
        for (FeatureToggle feature : FeatureToggle.values()) {
            Page page = Page.HUD; String section = "Main HUD", capability = "";
            switch (feature) {
                case MMM_FLAT_DIGGER, MMM_PERIMETER_WALL_DIG_HELPER -> { page = Page.GENERAL; section = "Mining helpers"; }
                case MMM_TOGGLE_TAB -> { page = Page.GENERAL; section = "Mining helpers"; capability = "toggle-tab"; }
                case MMM_DAILY_GOAL, MMM_NOTIFICATIONS, MMM_SOUND_ALERTS -> { page = Page.NOTIFICATIONS; section = "Goals & notifications"; }
                case MMM_BLOCK_ESP -> { page = Page.BLOCK_ESP; section = "Block ESP"; capability = "block-esp"; }
                case MMM_HUD_GOAL_PROGRESS -> { section = "Goal XP bar"; capability = "goal-xp"; }
                case MMM_MINING_TRACKER, MMM_SUMMARY_ON_EXIT, MMM_CARRY_GOAL_PROGRESS -> section = "Tracking & session";
                case MMM_HUD_SPEED_GRAPH -> { continue; } // Disabled in modern 1.0.20 as well.
                default -> {}
            }
            String label = switch (feature) {
                case MMM_MINING_TRACKER -> "Mining Tracker";
                case MMM_DAILY_GOAL -> "Goal Tracking";
                case MMM_NOTIFICATIONS -> "Milestone Messages";
                case MMM_SOUND_ALERTS -> "Sound Alerts";
                case MMM_SUMMARY_ON_EXIT -> "Summary on Exit";
                case MMM_CARRY_GOAL_PROGRESS -> "Carry Goal Progress";
                case MMM_HUD -> "Main HUD";
                case MMM_HUD_PROJECT -> "Project";
                case MMM_HUD_TOTAL_MINED -> "Mining Totals";
                case MMM_HUD_GOAL_PROGRESS -> "Override XP Bar";
                case MMM_HUD_BLOCKS_PER_HOUR -> "Mining Speed";
                case MMM_HUD_ETA -> "Goal ETA";
                case MMM_HUD_BOUNDING_BOX -> "Main Background";
                case MMM_PERIMETER_WALL_DIG_HELPER -> "Perimeter Wall Helper";
                default -> feature.getPrettyName();
            };
            add(rows, page, section, label, feature, capability);
        }
        for (var field : Configs.Generic.class.getFields()) {
            try {
                if (!(field.get(null) instanceof IConfigBase config)) continue;
                String name = field.getName(), capability = "";
                // Retain these keys in storage for compatibility, but no separate hourly overlay exists.
                if (name.equals("HOURLY_STATS_X") || name.equals("HOURLY_STATS_Y") || name.equals("HOURLY_STATS_SCALE")) continue;
                Page page; String section;
                if (name.startsWith("HUD_") || name.startsWith("TIMER_") || name.equals("HOURLY_STATS_VISIBLE") || name.startsWith("BLOCK_STATS_") || name.equals("BLOCKS_PER_MINUTE_VISIBLE")) {
                    page = Page.HUD;
                    section = name.startsWith("BLOCK_STATS_") ? "Block stats" : name.startsWith("TIMER_NOTIFICATION_") ? "Notification position" : name.startsWith("TIMER_") ? "Block timer" : "Main HUD";
                    if (name.equals("TIMER_CREDITS")) capability = "timer-credits";
                } else if (name.equals("MENU_HEX_COLOR") || name.equals("MENU_OPACITY")) {
                    page = Page.COLORS; section = "Menu";
                } else if (name.equals("ALWAYS_OVERRIDE_XP_BAR") || name.equals("SHOW_GOAL_PERCENT")) {
                    page = Page.HUD; section = "Goal XP bar"; capability = "goal-xp";
                } else if (name.startsWith("BLOCK_ESP_")) {
                    page = Page.BLOCK_ESP; section = "Block ESP"; capability = "block-esp";
                } else if (name.equals("BPS_SMOOTHING")) {
                    page = Page.VISUALS; section = "Mining speed";
                } else if (name.startsWith("SMALL_DIG_") || name.equals("NO_SWINGING_ANIMATION")) {
                    page = Page.VISUALS; section = "Items & tools"; capability = "items";
                } else if (name.startsWith("BREAKING_INDICATOR")) {
                    page = Page.VISUALS; section = "Breaking indicators"; capability = "breaking-indicators";
                } else if (name.equals("TRANSLUCENT_LAVA") || name.equals("LAVA_OPACITY")) {
                    page = Page.VISUALS; section = "Translucent lava"; capability = "lava";
                } else if (name.equals("DAILY_GOAL") || name.startsWith("GOAL_PERCENT_") || name.equals("GOAL_PICKAXE_ANIMATION")) {
                    page = Page.NOTIFICATIONS; section = "Goals & notifications";
                    if (name.equals("GOAL_PICKAXE_ANIMATION")) capability = "goal-animation";
                } else if (name.equals("WEBSITE_SYNC_ENABLED") || name.equals("WEBSITE_SYNC_DEBUG") || name.equals("ABBREVIATED_NUMBERS")) {
                    page = Page.SYNC; section = "Website sync";
                } else if (name.equals("SHOW_ACTIVE_DIGGERS") || name.equals("ACTIVE_DIGGERS_FRIENDS_ONLY") || name.equals("SHARE_GOAL_MILESTONES") || name.equals("RECEIVE_GOAL_MILESTONES") || name.equals("SHOW_MMM_CHAT_MESSAGES") || name.equals("CENSOR_MMM_CHAT")) {
                    page = Page.SOCIAL; section = "Social"; capability = "social";
                } else if (name.equals("SHARE_COLLECTION_MILESTONES")) {
                    page = Page.SOCIAL; section = "Collections"; capability = "collections";
                } else continue;
                if ((config instanceof ConfigColor && page == Page.HUD) || name.equals("HUD_TEXT_SHADOW")) { page = Page.COLORS; section = "HUD colors"; }
                if (page == Page.HUD && (name.endsWith("_X") || name.endsWith("_Y") || name.endsWith("_SCALE") || name.equals("HUD_ALIGNMENT")))
                    section += " position";
                String label = switch (name) {
                    case "HUD_TITLE_VISIBLE" -> "MMM Header";
                    case "HUD_GLOBAL_TOTAL_VISIBLE" -> "Global Total";
                    case "HUD_RANK_OVERTAKE_VISIBLE" -> "Rank Overtake Estimate";
                    case "HUD_WORLD_TOTAL_VISIBLE" -> "World Total";
                    case "HUD_SESSION_TOTAL_VISIBLE" -> "Session Total";
                    case "HUD_DAILY_WEEK_VISIBLE" -> "Today / Week";
                    case "HUD_RECORDS_VISIBLE" -> "Personal Records";
                    case "HUD_FASTEST_100K_VISIBLE" -> "Fastest 100k";
                    case "HUD_SESSION_TIME_VISIBLE" -> "Session Times";
                    case "HUD_DAILY_RESET_VISIBLE" -> "Daily Reset";
                    case "HUD_TIMER_STATUS_VISIBLE" -> "Timer Status";
                    case "HUD_DAILY_GOAL_BAR_VISIBLE" -> "Daily Goal Bar";
                    case "HUD_TEXT_BACKGROUND" -> "Text Background";
                    case "HUD_TEXT_SHADOW" -> "Text Shadow";
                    case "MENU_HEX_COLOR" -> "MMM Menu";
                    case "GOAL_PERCENT_DECIMALS" -> "Decimal Percent";
                    case "GOAL_PERCENT_DECIMAL_PLACES" -> "Decimal Places";
                    case "BLOCKS_PER_MINUTE_VISIBLE" -> "Blocks/min";
                    case "HOURLY_STATS_VISIBLE" -> "Hour / Best Hour";
                    case "TIMER_HUD_VISIBLE" -> "Timer HUD";
                    case "TIMER_CREDITS" -> "Credits Screen";
                    case "BLOCK_STATS_ICONS" -> "Block Icons";
                    case "SMALL_DIG_ITEM_SCALE" -> "Item Size";
                    case "BPS_SMOOTHING" -> "Blocks/sec Smoothing";
                    case "BLOCK_ESP_COLOR_MODE" -> "Color Mode";
                    case "BLOCK_ESP_HEX_COLOR" -> "Custom Color";
                    case "BLOCK_ESP_RENDER_MODE" -> "Render Mode";
                    case "BLOCK_ESP_OPACITY" -> "Opacity";
                    case "BLOCK_ESP_RAINBOW_SPEED" -> "Rainbow Speed";
                    case "BREAKING_INDICATOR_START_HEX_COLOR" -> "Start Color";
                    case "BREAKING_INDICATOR_END_HEX_COLOR" -> "End Color";
                    case "SHOW_ACTIVE_DIGGERS" -> "Active Digger Icons";
                    case "ACTIVE_DIGGERS_FRIENDS_ONLY" -> "Friends Only";
                    case "SHARE_GOAL_MILESTONES" -> "Share Milestones";
                    case "RECEIVE_GOAL_MILESTONES" -> "Receive Milestones";
                    case "SHARE_COLLECTION_MILESTONES" -> "Collection Milestones";
                    case "SHOW_MMM_CHAT_MESSAGES" -> "MMM Chat Messages";
                    default -> config.getPrettyName().replace("Hud ", "HUD ").replace(" Hex Color", "").replace(" Visible", "");
                };
                add(rows, page, section, label, config, capability);
            } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        return List.copyOf(rows);
    }
    private static void add(List<Setting> rows, Page page, String section, String label, IConfigBase config, String capability) {
        String name = config.getName().toLowerCase(Locale.ROOT);
        rows.add(new Setting(page, section, label, config, name.contains("opacity") || name.equals("goalpercentdecimalplaces") || name.equals("smalldigitemscale"), capability));
    }
    static List<MmmHotkey> hotkeys() {
        List<MmmHotkey> keys = new ArrayList<>(List.of(Hotkeys.OPEN_CONFIG_GUI, Hotkeys.OPEN_SUMMARY, Hotkeys.OPEN_HISTORY,
                Hotkeys.PAUSE_SESSION, Hotkeys.TOGGLE_SESSION, Hotkeys.EXPORT_HISTORY));
        for (Setting setting : settings()) if (setting.config instanceof FeatureToggle feature) keys.add(feature.getHotkey());
        for (MmmHotkey key : Hotkeys.HOTKEY_LIST) {
            boolean implemented = key == Hotkeys.TOGGLE_NO_SWINGING_ANIMATION
                    ? LegacyUiRegistry.available("items") : LegacyUiRegistry.available("scoreboard");
            if (implemented && !keys.contains(key)) keys.add(key);
        }
        return List.copyOf(keys);
    }
    static List<Setting> filter(List<Setting> settings, Page page, String query) {
        return settings.stream().filter(s -> query.isBlank() ? s.page == page : s.matches(query)).toList();
    }
    static int rowsPerPage(int height) { return Math.max(1, (height - 116) / 36); }
}
