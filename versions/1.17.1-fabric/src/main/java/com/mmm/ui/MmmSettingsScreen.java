package com.mmm.ui;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** Settings inventory for this Minecraft version. Navigation and rendering are shared. */
public final class MmmSettingsScreen extends SettingsBoard {
    private static final boolean SPEED_GRAPH_AVAILABLE = false;

    public MmmSettingsScreen(Screen parent) { super(parent); }
    public MmmSettingsScreen(Screen parent, boolean hotkey) { super(parent, hotkey); }

    static void createSections(List<SettingsSection> sections)
    {
        sections.clear();
        sections.add(SettingsSection.helpers(
                new SettingRow("Flat Digger", "Stop digging below your feet.", FeatureToggle.MMM_FLAT_DIGGER, ControlKind.BOOLEAN),
                new SettingRow("Perimeter Wall Helper", "Protect the configured perimeter floor.", FeatureToggle.MMM_PERIMETER_WALL_DIG_HELPER, ControlKind.BOOLEAN),
                new SettingRow("Perimeter Block List", "Choose which floor blocks are protected.", null, ControlKind.ACTION),
                new SettingRow("Toggle Tab", "Keep the player list open.", FeatureToggle.MMM_TOGGLE_TAB, ControlKind.BOOLEAN)
        ));
        sections.add(SettingsSection.sync(
                new SettingRow("Website Link", "Open the MMM website or link your account with a code.", null, ControlKind.ACTION),
                new SettingRow("Sync Status", "", null, ControlKind.STATUS),
                new SettingRow("Website Sync", "Send mining updates.", Configs.Generic.WEBSITE_SYNC_ENABLED, ControlKind.BOOLEAN),
                new SettingRow("Sync Scoreboard", "Choose the total-mined scoreboard for this source.", null, ControlKind.ACTION),
                new SettingRow("Sync Debug", "Save detailed sync logs.", Configs.Generic.WEBSITE_SYNC_DEBUG, ControlKind.BOOLEAN),
                new SettingRow("Abbreviated Numbers", "Show 12k instead of 12,000.", Configs.Generic.ABBREVIATED_NUMBERS, ControlKind.BOOLEAN)
        ));
        sections.add(SettingsSection.notifications(
                new SettingRow("Daily Goal", "Blocks to mine today.", Configs.Generic.DAILY_GOAL, ControlKind.NUMBER),
                new SettingRow("Decimal Percent", "Show decimals in goal progress.", Configs.Generic.GOAL_PERCENT_DECIMALS, ControlKind.BOOLEAN),
                new SettingRow("Decimal Places", "Choose 1, 2, or 3 decimal places.", Configs.Generic.GOAL_PERCENT_DECIMAL_PLACES, ControlKind.SLIDER),
                new SettingRow("Goal Tracking", "Track today's goal.", FeatureToggle.MMM_DAILY_GOAL, ControlKind.BOOLEAN),
                new SettingRow("Milestone Messages", "Post progress alerts in chat.", FeatureToggle.MMM_NOTIFICATIONS, ControlKind.BOOLEAN),
                new SettingRow("Share Milestones", "Share progress with linked MMM players.", Configs.Generic.SHARE_GOAL_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("Receive Milestones", "Show global milestones from linked players.", Configs.Generic.RECEIVE_GOAL_MILESTONES, ControlKind.BOOLEAN),
                new SettingRow("MMM Chat Messages", "Show global MMM messages in Minecraft chat.", Configs.Generic.SHOW_MMM_CHAT_MESSAGES, ControlKind.BOOLEAN),
                new SettingRow("Sound Alerts", "Play milestone sounds.", FeatureToggle.MMM_SOUND_ALERTS, ControlKind.BOOLEAN),
                new SettingRow("Custom Sounds", "Choose sounds for 25%, 50%, 75%, and 100%.", null, ControlKind.ACTION),
                new SettingRow("Pickaxe Animation", "Show a pickaxe at each milestone.", Configs.Generic.GOAL_PICKAXE_ANIMATION, ControlKind.BOOLEAN)
        ));
        sections.add(SettingsSection.hud(
                new SettingRow("Move HUD", "Move and resize sections.", null, ControlKind.ACTION),
                SettingRow.heading("MAIN HUD POSITION"),
                new SettingRow("HUD X", "Move left or right.", Configs.Generic.HUD_X, ControlKind.NUMBER),
                new SettingRow("HUD Y", "Move up or down.", Configs.Generic.HUD_Y, ControlKind.NUMBER),
                new SettingRow("HUD Alignment", "Choose its screen corner.", Configs.Generic.HUD_ALIGNMENT, ControlKind.OPTION),
                new SettingRow("HUD Scale", "Change the HUD size.", Configs.Generic.HUD_SCALE, ControlKind.NUMBER),
                SettingRow.heading("BLOCK TIMER POSITION"),
                new SettingRow("Timer X", "Move left or right.", Configs.Generic.TIMER_HUD_X, ControlKind.NUMBER),
                new SettingRow("Timer Y", "Move up or down.", Configs.Generic.TIMER_HUD_Y, ControlKind.NUMBER),
                new SettingRow("Timer Scale", "Change the timer size.", Configs.Generic.TIMER_HUD_SCALE, ControlKind.NUMBER),
                SettingRow.heading("BLOCK STATS POSITION"),
                new SettingRow("Block Stats X", "Move left or right.", Configs.Generic.BLOCK_STATS_X, ControlKind.NUMBER),
                new SettingRow("Block Stats Y", "Move up or down.", Configs.Generic.BLOCK_STATS_Y, ControlKind.NUMBER),
                new SettingRow("Block Stats Scale", "Change block-stats size.", Configs.Generic.BLOCK_STATS_SCALE, ControlKind.NUMBER)
        ));
        sections.add(SettingsSection.hudContent(
                SettingRow.heading("TRACKING & SESSION"),
                new SettingRow("Mining Tracker", "Track mined blocks and sessions.", FeatureToggle.MMM_MINING_TRACKER, ControlKind.BOOLEAN),
                new SettingRow("Summary on Exit", "Open the summary when leaving.", FeatureToggle.MMM_SUMMARY_ON_EXIT, ControlKind.BOOLEAN),
                new SettingRow("Carry Goal Progress", "Keep today's progress between sessions.", FeatureToggle.MMM_CARRY_GOAL_PROGRESS, ControlKind.BOOLEAN),
                SettingRow.heading("MAIN HUD"),
                new SettingRow("Main HUD", "Show the stats HUD.", FeatureToggle.MMM_HUD, ControlKind.BOOLEAN),
                new SettingRow("MMM Header", "Show MMM and sync status.", Configs.Generic.HUD_TITLE_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Project", "Show your active project.", FeatureToggle.MMM_HUD_PROJECT, ControlKind.BOOLEAN),
                new SettingRow("Mining Totals", "Enable mining total rows.", FeatureToggle.MMM_HUD_TOTAL_MINED, ControlKind.BOOLEAN),
                new SettingRow("Global Total", "Show your website total.", Configs.Generic.HUD_GLOBAL_TOTAL_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("World Total", "Show this world's total.", Configs.Generic.HUD_WORLD_TOTAL_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Session Total", "Show this session's total.", Configs.Generic.HUD_SESSION_TOTAL_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Today / Week", "Show today and this week.", Configs.Generic.HUD_DAILY_WEEK_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Personal Records", "Show day and week bests.", Configs.Generic.HUD_RECORDS_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Fastest 100k", "Show your fastest 100k time.", Configs.Generic.HUD_FASTEST_100K_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Mining Speed", "Show Blocks/hr and Blocks/sec.", FeatureToggle.MMM_HUD_BLOCKS_PER_HOUR, ControlKind.BOOLEAN),
                new SettingRow("Blocks/min", "Also show Blocks/min.", Configs.Generic.BLOCKS_PER_MINUTE_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Hour / Best Hour", "Show current and best hour.", Configs.Generic.HOURLY_STATS_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Timer Status", "Show timer status.", Configs.Generic.HUD_TIMER_STATUS_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Session Times", "Show active time and time with pauses.", Configs.Generic.HUD_SESSION_TIME_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Daily Reset", "Show time until UTC reset.", Configs.Generic.HUD_DAILY_RESET_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Goal ETA", "Show time left to goal.", FeatureToggle.MMM_HUD_ETA, ControlKind.BOOLEAN),
                new SettingRow("Daily Goal Bar", "Show goal progress and % in the HUD.", Configs.Generic.HUD_DAILY_GOAL_BAR_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Override XP Bar", "Use goal progress in XP bar.", FeatureToggle.MMM_HUD_GOAL_PROGRESS, ControlKind.BOOLEAN),
                new SettingRow("Always Override XP Bar", "Always replace vanilla XP.", Configs.Generic.ALWAYS_OVERRIDE_XP_BAR, ControlKind.BOOLEAN),
                new SettingRow("Show Goal %", "Show goal % as XP level.", Configs.Generic.SHOW_GOAL_PERCENT, ControlKind.BOOLEAN),
                new SettingRow("Main Background", "Show one HUD background.", FeatureToggle.MMM_HUD_BOUNDING_BOX, ControlKind.BOOLEAN),
                new SettingRow("Text Background", "Show backgrounds per line.", Configs.Generic.HUD_TEXT_BACKGROUND, ControlKind.BOOLEAN),
                SettingRow.heading("BLOCK TIMER"),
                new SettingRow("Timer HUD", "Show the challenge timer.", Configs.Generic.TIMER_HUD_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Notifications", "Show timer milestones.", Configs.Generic.TIMER_NOTIFICATIONS, ControlKind.BOOLEAN),
                new SettingRow("Credits Screen", "Show results when finished.", Configs.Generic.TIMER_CREDITS, ControlKind.BOOLEAN),
                SettingRow.heading("BLOCK STATS"),
                new SettingRow("Block Stats", "Show blocks from this run.", Configs.Generic.BLOCK_STATS_VISIBLE, ControlKind.BOOLEAN),
                new SettingRow("Background", "Show the stats panel.", Configs.Generic.BLOCK_STATS_BACKGROUND, ControlKind.BOOLEAN),
                new SettingRow("Block Icons", "Show each block icon.", Configs.Generic.BLOCK_STATS_ICONS, ControlKind.BOOLEAN)
        ));
        sections.add(SettingsSection.colors(
                new SettingRow("MMM Menu", "Set the menu accent.", Configs.Generic.MENU_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Menu Opacity", "Change menu background transparency.", Configs.Generic.MENU_OPACITY, ControlKind.SLIDER),
                new SettingRow("HUD Title", "Set the title color.", Configs.Generic.HUD_TITLE_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Text", "Set the label color.", Configs.Generic.HUD_TEXT_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Numbers", "Set the number color.", Configs.Generic.HUD_NUMBER_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Inactive Text", "Set the paused color.", Configs.Generic.HUD_INACTIVE_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("HUD Background", "Set the panel color.", Configs.Generic.HUD_BACKGROUND_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Text Shadow", "Add a dark pixel shadow.", Configs.Generic.HUD_TEXT_SHADOW, ControlKind.BOOLEAN)
        ));
        sections.add(SettingsSection.blockEsp(
                new SettingRow("Block ESP", "Highlight the block you look at.", FeatureToggle.MMM_BLOCK_ESP, ControlKind.BOOLEAN),
                new SettingRow("Color Mode", "Use one color or rainbow.", Configs.Generic.BLOCK_ESP_COLOR_MODE, ControlKind.OPTION),
                new SettingRow("Custom Color", "Set the highlight color.", Configs.Generic.BLOCK_ESP_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("Render Mode", "Choose the highlight shape.", Configs.Generic.BLOCK_ESP_RENDER_MODE, ControlKind.OPTION),
                new SettingRow("Opacity", "Change highlight strength.", Configs.Generic.BLOCK_ESP_OPACITY, ControlKind.NUMBER),
                new SettingRow("Rainbow Speed", "Change rainbow speed.", Configs.Generic.BLOCK_ESP_RAINBOW_SPEED, ControlKind.NUMBER)
        ));
        if (SPEED_GRAPH_AVAILABLE)
        {
            sections.add(SettingsSection.speedGraph(
                    new SettingRow("Speed Graph", "Show the live speed graph.", FeatureToggle.MMM_HUD_SPEED_GRAPH, ControlKind.BOOLEAN),
                    new SettingRow("Background Opacity", "Change background strength.", Configs.Generic.GRAPH_BG_OPACITY, ControlKind.NUMBER),
                    new SettingRow("Line Color", "Set the graph line color.", Configs.Generic.GRAPH_LINE_HEX_COLOR, ControlKind.COLOR),
                    new SettingRow("Fill Color", "Set the graph fill color.", Configs.Generic.GRAPH_FILL_HEX_COLOR, ControlKind.COLOR),
                    new SettingRow("Fill Opacity", "Change fill strength.", Configs.Generic.GRAPH_FILL_OPACITY, ControlKind.NUMBER),
                    new SettingRow("Grid Color", "Set the grid color.", Configs.Generic.GRAPH_GRID_HEX_COLOR, ControlKind.COLOR),
                    new SettingRow("Grid Opacity", "Change grid strength.", Configs.Generic.GRAPH_GRID_OPACITY, ControlKind.NUMBER),
                    new SettingRow("Scale Step", "Set Blocks/hr grid gaps.", Configs.Generic.GRAPH_SCALE_STEP, ControlKind.NUMBER)
            ));
        }
        sections.add(SettingsSection.visuals(
                new SettingRow("Blocks/sec Smoothing", "Change speed response.", Configs.Generic.BPS_SMOOTHING, ControlKind.OPTION),
                new SettingRow("Small Dig Items", "Shrink tracked block items.", Configs.Generic.SMALL_DIG_ITEMS, ControlKind.BOOLEAN),
                new SettingRow("Item Size", "Choose the smaller size.", Configs.Generic.SMALL_DIG_ITEM_SCALE, ControlKind.SLIDER),
                new SettingRow("No Swinging Animation", "Keep your tool still.", Configs.Generic.NO_SWINGING_ANIMATION, ControlKind.BOOLEAN),
                SettingRow.heading("BREAKING INDICATORS"),
                new SettingRow("Breaking Indicators", "Show live block-breaking progress.", Configs.Generic.BREAKING_INDICATORS, ControlKind.BOOLEAN),
                new SettingRow("Start Color", "Color when breaking begins.", Configs.Generic.BREAKING_INDICATOR_START_HEX_COLOR, ControlKind.COLOR),
                new SettingRow("End Color", "Color when the block is nearly broken.", Configs.Generic.BREAKING_INDICATOR_END_HEX_COLOR, ControlKind.COLOR),
                SettingRow.heading("TRANSLUCENT LAVA"),
                new SettingRow("Translucent Lava", "See through lava.", Configs.Generic.TRANSLUCENT_LAVA, ControlKind.BOOLEAN),
                new SettingRow("Lava Opacity", "Choose how visible lava looks.", Configs.Generic.LAVA_OPACITY, ControlKind.SLIDER)
        ));
        if (Configs.isDevToolsEnabled())
        {
            sections.add(SettingsSection.developer(
                    new SettingRow("Generate Dev Run", "Create a test run.", null, ControlKind.ACTION)
            ));
        }
    }

}
