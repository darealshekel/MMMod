package com.mmm.config;

import java.io.File;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mmm.MMM;
import com.mmm.Reference;
import com.mmm.storage.AtomicJsonStorage;
import com.mmm.storage.SharedStoragePaths;
import com.mmm.storage.WorldIdentity;
import com.mmm.feature.PerimeterWallDigHelper;
import com.mmm.util.BlockBreakdownCatalog;
import com.mmm.util.PeriodKeys;
import com.mmm.util.WeeklyProgressPolicy;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.BooleanHotkeyGuiWrapper;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import net.fabricmc.loader.api.FabricLoader;

public class Configs implements IConfigHandler
{
    private static final String CONFIG_FILE_NAME = Reference.STORAGE_ID + ".json";
    private static final long CURRENT_SETTINGS_MIGRATION_VERSION = 2L;
    private static final Set<String> MIGRATION_CONFIG_FILE_NAMES = Set.of(
            Reference.STORAGE_ID + ".json",
            Reference.LEGACY_STORAGE_ID + ".json",
            "aetweaks.json");
    private static final String DEFAULT_CLOUD_SYNC_ENDPOINT = "https://sync.mmmaniacs.com/v1/sync";
    public static final int MIN_DAILY_GOAL = 35_000;

    public static boolean isDevToolsEnabled()
    {
        String override = System.getProperty("mmm.devTools");
        if (override != null)
        {
            return Boolean.parseBoolean(override);
        }

        try (InputStream input = Configs.class.getResourceAsStream("/mmm-build.properties"))
        {
            if (input == null)
            {
                return false;
            }

            Properties properties = new Properties();
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty("devTools", "false"));
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    public static class Generic
    {
        public static final String DEFAULT_HUD_TITLE_HEX_COLOR = "#FFE00000";
        public static final String DEFAULT_HUD_TEXT_HEX_COLOR = "#FFF6F3EF";
        public static final String DEFAULT_HUD_NUMBER_HEX_COLOR = "#FFFFFFFF";
        public static final String DEFAULT_HUD_INACTIVE_HEX_COLOR = "#FF949494";
        public static final String DEFAULT_MENU_HEX_COLOR = "#FFE00000";
        public static final String DEFAULT_BLOCK_ESP_HEX_COLOR = "#FF55FF55";
        public static final String DEFAULT_GRAPH_LINE_HEX_COLOR = "#FFE00000";
        public static final String DEFAULT_GRAPH_FILL_HEX_COLOR = "#FFE00000";
        public static final String DEFAULT_GRAPH_GRID_HEX_COLOR = "#FFC8C8C8";

        public static final ConfigBoolean WEBSITE_SYNC_ENABLED = new ConfigBoolean("websiteSyncEnabled", false, "Enable MMM website sync.");
        public static final ConfigBoolean TOTAL_DIGS_SYNC_ENABLED = new ConfigBoolean("totalDigsSyncEnabled", false, "Sync Total Digs to Website.");
        public static final ConfigBoolean WEBSITE_SYNC_DEBUG = new ConfigBoolean("websiteSyncDebug", false, "Enable verbose website sync debug logging.");
        public static final ConfigInteger MAX_BLOCKS_PER_MINUTE = new ConfigInteger("maxBlocksPerMinute", 1200, 1, 1200, "Maximum accepted local valid block breaks per minute. Excess local counts are logged and ignored.");
        public static final ConfigBoolean ABBREVIATED_NUMBERS = new ConfigBoolean("abbreviatedNumbers", false, "Show shortened large numbers such as 10M instead of 10,000,000.");
        public static final ConfigInteger DAILY_GOAL = new ConfigInteger("dailyGoal", MIN_DAILY_GOAL, MIN_DAILY_GOAL, 1_000_000, "Daily goal target.");
        public static final ConfigBoolean GOAL_PICKAXE_ANIMATION = new ConfigBoolean("goalPickaxeAnimation", true, "Show a pickaxe animation at each main daily-goal milestone.");
        public static final ConfigBoolean SHARE_GOAL_MILESTONES = new ConfigBoolean("shareGoalMilestones", true, "Share goal milestones with linked MMM players on this server.");
        public static final ConfigBoolean RECEIVE_GOAL_MILESTONES = new ConfigBoolean("receiveGoalMilestones", true, "Show goal milestones from linked MMM players on this server.");
        public static final ConfigInteger HUD_X = new ConfigInteger("hudX", 4, 0, 820, "Mining HUD horizontal position.");
        public static final ConfigInteger HUD_Y = new ConfigInteger("hudY", 4, 0, 460, "Mining HUD vertical position.");
        public static final ConfigOptionList HUD_ALIGNMENT = new ConfigOptionList("hudAlignment", HudAlignment.TOP_LEFT, "Mining HUD alignment anchor.");
        public static final ConfigDouble HUD_SCALE = new ConfigDouble("hudScale", 1.0D, 0.25D, 1.75D, "Mining HUD scale.");
        public static final ConfigBoolean HUD_TEXT_BACKGROUND = new ConfigBoolean("hudTextBackground", false, "Draw small background boxes behind individual MMM HUD text lines.");
        public static final ConfigBoolean HUD_TITLE_VISIBLE = new ConfigBoolean("hudTitleVisible", true, "Show the MMM title and sync status.");
        public static final ConfigBoolean HUD_GLOBAL_TOTAL_VISIBLE = new ConfigBoolean("hudGlobalTotalVisible", true, "Show your combined website total.");
        public static final ConfigBoolean HUD_WORLD_TOTAL_VISIBLE = new ConfigBoolean("hudWorldTotalVisible", true, "Show the current source total.");
        public static final ConfigBoolean HUD_SESSION_TOTAL_VISIBLE = new ConfigBoolean("hudSessionTotalVisible", true, "Show blocks mined in the current session.");
        public static final ConfigBoolean HUD_DAILY_WEEK_VISIBLE = new ConfigBoolean("hudDailyWeekVisible", true, "Show today's and this week's blocks.");
        public static final ConfigBoolean HUD_RECORDS_VISIBLE = new ConfigBoolean("hudRecordsVisible", true, "Show daily and weekly personal records.");
        public static final ConfigBoolean HUD_FASTEST_100K_VISIBLE = new ConfigBoolean("hudFastest100kVisible", true, "Show your fastest 100k time.");
        public static final ConfigBoolean HUD_SESSION_TIME_VISIBLE = new ConfigBoolean("hudSessionTimeVisible", true, "Show the current session time.");
        public static final ConfigBoolean HUD_DAILY_RESET_VISIBLE = new ConfigBoolean("hudDailyResetVisible", true, "Show the countdown to the daily reset.");
        public static final ConfigBoolean HUD_TIMER_STATUS_VISIBLE = new ConfigBoolean("hudTimerStatusVisible", true, "Show timer status in the main HUD.");
        public static final ConfigBoolean HUD_DAILY_GOAL_BAR_VISIBLE = new ConfigBoolean("hudDailyGoalBarVisible", false, "Show daily-goal progress and a colored bar in the main HUD.");
        public static final ConfigBoolean ALWAYS_OVERRIDE_XP_BAR = new ConfigBoolean("alwaysOverrideXpBar", false, "Always replace the vanilla XP bar with daily-goal progress instead of only while Tab is held.");
        public static final ConfigBoolean SHOW_GOAL_PERCENT = new ConfigBoolean("showGoalPercent", true, "Show daily-goal percentage instead of the vanilla XP level.");
        public static final ConfigBoolean GOAL_PERCENT_DECIMALS = new ConfigBoolean("goalPercentDecimals", true, "Show decimal places in the daily-goal percentage.");
        public static final ConfigInteger GOAL_PERCENT_DECIMAL_PLACES = new ConfigInteger("goalPercentDecimalPlaces", 1, 1, 3, "Number of decimal places shown in the daily-goal percentage.");
        public static final ConfigBoolean TIMER_HUD_VISIBLE = new ConfigBoolean("timerHudVisible", false, "Show the MMM timer HUD module.");
        public static final ConfigBoolean HOURLY_STATS_VISIBLE = new ConfigBoolean("hourlyStatsVisible", true, "Show the hourly mining stats HUD module.");
        public static final ConfigBoolean BLOCKS_PER_MINUTE_VISIBLE = new ConfigBoolean("blocksPerMinuteVisible", true, "Show Blocks/min in the main MMM HUD speed line.");
        public static final ConfigBoolean BLOCK_STATS_VISIBLE = new ConfigBoolean("blockStatsVisible", true, "Show the block stats HUD module.");
        public static final ConfigBoolean BLOCK_STATS_BACKGROUND = new ConfigBoolean("blockStatsBackground", true, "Draw a background behind the block stats HUD module.");
        public static final ConfigBoolean BLOCK_STATS_STATIC = new ConfigBoolean("blockStatsStatic", false, "Keep block stats on one page instead of auto-paging.");
        public static final ConfigBoolean BLOCK_STATS_ICONS = new ConfigBoolean("blockStatsIcons", true, "Show block item icons in the block stats HUD module.");
        public static final ConfigBoolean TIMER_NOTIFICATIONS = new ConfigBoolean("timerNotifications", true, "Show timer-hour notifications.");
        public static final ConfigBoolean TIMER_CREDITS = new ConfigBoolean("timerCredits", true, "Show the timer-complete credits screen.");
        public static final ConfigInteger TIMER_HUD_X = new ConfigInteger("timerHudX", 520, 0, 820, "Timer HUD horizontal position.");
        public static final ConfigInteger TIMER_HUD_Y = new ConfigInteger("timerHudY", 24, 0, 460, "Timer HUD vertical position.");
        public static final ConfigDouble TIMER_HUD_SCALE = new ConfigDouble("timerHudScale", 1.0D, 0.25D, 3.0D, "Timer HUD scale.");
        public static final ConfigInteger HOURLY_STATS_X = new ConfigInteger("hourlyStatsX", 520, 0, 820, "Hourly stats HUD horizontal position.");
        public static final ConfigInteger HOURLY_STATS_Y = new ConfigInteger("hourlyStatsY", 92, 0, 460, "Hourly stats HUD vertical position.");
        public static final ConfigDouble HOURLY_STATS_SCALE = new ConfigDouble("hourlyStatsScale", 1.0D, 0.25D, 3.0D, "Hourly stats HUD scale.");
        public static final ConfigInteger BLOCK_STATS_X = new ConfigInteger("blockStatsX", 520, 0, 820, "Block stats HUD horizontal position.");
        public static final ConfigInteger BLOCK_STATS_Y = new ConfigInteger("blockStatsY", 190, 0, 460, "Block stats HUD vertical position.");
        public static final ConfigDouble BLOCK_STATS_SCALE = new ConfigDouble("blockStatsScale", 1.0D, 0.25D, 3.0D, "Block stats HUD scale.");
        public static final ConfigInteger TIMER_NOTIFICATION_X = new ConfigInteger("timerNotificationX", 325, 0, 820, "Timer notification horizontal position.");
        public static final ConfigInteger TIMER_NOTIFICATION_Y = new ConfigInteger("timerNotificationY", 44, 0, 460, "Timer notification vertical position.");
        public static final ConfigDouble TIMER_NOTIFICATION_SCALE = new ConfigDouble("timerNotificationScale", 1.0D, 0.25D, 3.0D, "Timer notification scale.");
        public static final ConfigColor HUD_TITLE_HEX_COLOR = new ConfigColor("hudTitleHexColor", DEFAULT_HUD_TITLE_HEX_COLOR, "Title color used by the MMM HUD.");
        public static final ConfigColor HUD_TEXT_HEX_COLOR = new ConfigColor("hudTextHexColor", DEFAULT_HUD_TEXT_HEX_COLOR, "Label/text color used by the MMM HUD.");
        public static final ConfigColor HUD_NUMBER_HEX_COLOR = new ConfigColor("hudNumberHexColor", DEFAULT_HUD_NUMBER_HEX_COLOR, "Number color used by MMM HUD and UI numeric values.");
        public static final ConfigColor HUD_INACTIVE_HEX_COLOR = new ConfigColor("hudInactiveHexColor", DEFAULT_HUD_INACTIVE_HEX_COLOR, "Inactive/paused text color used by the MMM HUD.");
        public static final ConfigColor MENU_HEX_COLOR = new ConfigColor("menuHexColor", DEFAULT_MENU_HEX_COLOR, "Accent color used by MMM menu screens.");
        public static final ConfigOptionList BPS_SMOOTHING = new ConfigOptionList("bpsSmoothing", BpsSmoothing.FAST, "Blocks/sec Smoothing");
        public static final ConfigBoolean SMALL_DIG_ITEMS = new ConfigBoolean("smallDigItems", false, "Render MMM breakdown block items smaller, like the Smoll Dig Items resource pack.");
        public static final ConfigDouble SMALL_DIG_ITEM_SCALE = new ConfigDouble("smallDigItemScale", 0.2D, 0.1D, 1.0D, "Size of tracked mining block items while Small Dig Items is enabled.");
        public static final ConfigBoolean NO_SWINGING_ANIMATION = new ConfigBoolean("noSwingingAnimation", false, "Disable the local first-person hand swing animation while mining.");
        public static final ConfigBoolean TRANSLUCENT_LAVA = new ConfigBoolean("translucentLava", false, "Make lava transparent enough to see through.");
        public static final ConfigInteger LAVA_OPACITY = new ConfigInteger("lavaOpacity", 45, 10, 100, "How visible lava is while Translucent Lava is enabled.");
        public static final ConfigBoolean SCOREBOARD_VISIBLE = new ConfigBoolean("scoreboardVisible", true, "Show the sidebar scoreboard.");
        public static final ConfigBoolean SCOREBOARD_SCORES_VISIBLE = new ConfigBoolean("scoreboardScoresVisible", true, "Show score values beside player names.");
        public static final ConfigBoolean SCOREBOARD_SCORE_COMMAS = new ConfigBoolean("scoreboardScoreCommas", true, "Format scores with thousands separators.");
        public static final ConfigBoolean SCOREBOARD_TAB_LIST_COMMAS = new ConfigBoolean("scoreboardTabListCommas", true, "Format player-list scores with thousands separators.");
        public static final ConfigBoolean TIER_NAME_TAGS = new ConfigBoolean("tierNameTags", true, "Show website totals before player names in chat, Tab, scoreboards, and nametags.");
        public static final ConfigBoolean SCOREBOARD_SCORE_ABBREVIATED = new ConfigBoolean("scoreboardScoreAbbreviated", false, "Shorten large scores with k, M, and B.");
        public static final ConfigOptionList SCOREBOARD_SORTING = new ConfigOptionList("scoreboardSorting", ScoreboardSorting.SCORE_DESCENDING, "Choose how sidebar rows are sorted.");
        public static final ConfigInteger SCOREBOARD_MAX_ENTRIES = new ConfigInteger("scoreboardMaxEntries", 15, 0, 100, "Maximum rows shown on one scoreboard page.");
        public static final ConfigOptionList SCOREBOARD_POSITION = new ConfigOptionList("scoreboardPosition", ScoreboardPosition.RIGHT, "Choose the sidebar anchor on screen.");
        public static final ConfigInteger SCOREBOARD_Y_OFFSET = new ConfigInteger("scoreboardYOffset", 0, -100, 100, "Move the scoreboard up or down.");
        public static final ConfigDouble SCOREBOARD_SCALE = new ConfigDouble("scoreboardScale", 1.0D, 0.5D, 2.0D, "Resize the complete scoreboard sidebar.");
        public static final ConfigDouble SCOREBOARD_BODY_OPACITY = new ConfigDouble("scoreboardBodyOpacity", 0.3D, 0.0D, 1.0D, "Change the row background opacity.");
        public static final ConfigDouble SCOREBOARD_TITLE_OPACITY = new ConfigDouble("scoreboardTitleOpacity", 0.4D, 0.0D, 1.0D, "Change the title background opacity.");
        public static final ConfigDouble SCOREBOARD_TEXT_OPACITY = new ConfigDouble("scoreboardTextOpacity", 1.0D, 0.0D, 1.0D, "Change player and score text opacity.");
        public static final ConfigDouble SCOREBOARD_TITLE_TEXT_OPACITY = new ConfigDouble("scoreboardTitleTextOpacity", 1.0D, 0.0D, 1.0D, "Change scoreboard title text opacity.");
        public static final ConfigBoolean SCOREBOARD_DEFAULT_TEAM_CHAT = new ConfigBoolean("scoreboardDefaultTeamChat", false, "Send normal chat to your team; prefix # for public chat.");
        public static final ConfigOptionList BLOCK_ESP_COLOR_MODE = new ConfigOptionList("blockEspColorMode", BlockEspColorMode.RAINBOW, "Block ESP color mode.");
        public static final ConfigColor BLOCK_ESP_HEX_COLOR = new ConfigColor("blockEspHexColor", DEFAULT_BLOCK_ESP_HEX_COLOR, "Block ESP custom color. Used when the color mode is Single Color.");
        public static final ConfigOptionList BLOCK_ESP_RENDER_MODE = new ConfigOptionList("blockEspRenderMode", BlockEspRenderMode.FULL_BLOCK, "Block ESP render mode.");
        public static final ConfigInteger BLOCK_ESP_OPACITY = new ConfigInteger("blockEspOpacity", 35, 0, 100, "Block ESP opacity percentage.");
        public static final ConfigDouble BLOCK_ESP_RAINBOW_SPEED = new ConfigDouble("blockEspRainbowSpeed", 1.0D, 0.1D, 10.0D, "Block ESP rainbow animation speed multiplier.");
        public static final ConfigColor GRAPH_LINE_HEX_COLOR = new ConfigColor("graphLineHexColor", DEFAULT_GRAPH_LINE_HEX_COLOR, "Speed graph line color.");
        public static final ConfigColor GRAPH_FILL_HEX_COLOR = new ConfigColor("graphFillHexColor", DEFAULT_GRAPH_FILL_HEX_COLOR, "Speed graph fill color.");
        public static final ConfigInteger GRAPH_FILL_OPACITY = new ConfigInteger("graphFillOpacity", 42, 0, 100, "Speed graph fill opacity percentage.");
        public static final ConfigColor GRAPH_GRID_HEX_COLOR = new ConfigColor("graphGridHexColor", DEFAULT_GRAPH_GRID_HEX_COLOR, "Speed graph grid line color.");
        public static final ConfigInteger GRAPH_GRID_OPACITY = new ConfigInteger("graphGridOpacity", 27, 0, 100, "Speed graph grid line opacity percentage.");
        public static final ConfigInteger GRAPH_BG_OPACITY = new ConfigInteger("graphBgOpacity", 75, 0, 100, "Speed graph background opacity percentage.");
        public static final ConfigInteger GRAPH_SCALE_STEP = new ConfigInteger("graphScaleStep", 100, 50, 1000, "Speed graph Y-axis grid interval (blocks/hr).");
        public static final ConfigStringList PERIMETER_OUTLINE_BLOCKS_LIST = new ConfigStringList("perimeterOutlineBlocksList", ImmutableList.of(), "Block types checked by the MMM Perimeter Wall Dig Helper feature.");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                WEBSITE_SYNC_ENABLED,
                TOTAL_DIGS_SYNC_ENABLED,
                WEBSITE_SYNC_DEBUG,
                ABBREVIATED_NUMBERS,
                DAILY_GOAL,
                GOAL_PICKAXE_ANIMATION,
                SHARE_GOAL_MILESTONES,
                RECEIVE_GOAL_MILESTONES,
                HUD_X,
                HUD_Y,
                HUD_ALIGNMENT,
                HUD_SCALE,
                HUD_TEXT_BACKGROUND,
                HUD_TITLE_VISIBLE,
                HUD_GLOBAL_TOTAL_VISIBLE,
                HUD_WORLD_TOTAL_VISIBLE,
                HUD_SESSION_TOTAL_VISIBLE,
                HUD_DAILY_WEEK_VISIBLE,
                HUD_RECORDS_VISIBLE,
                HUD_FASTEST_100K_VISIBLE,
                HUD_SESSION_TIME_VISIBLE,
                HUD_DAILY_RESET_VISIBLE,
                HUD_TIMER_STATUS_VISIBLE,
                HUD_DAILY_GOAL_BAR_VISIBLE,
                ALWAYS_OVERRIDE_XP_BAR,
                SHOW_GOAL_PERCENT,
                GOAL_PERCENT_DECIMALS,
                GOAL_PERCENT_DECIMAL_PLACES,
                TIMER_HUD_VISIBLE,
                HOURLY_STATS_VISIBLE,
                BLOCKS_PER_MINUTE_VISIBLE,
                BLOCK_STATS_VISIBLE,
                BLOCK_STATS_BACKGROUND,
                BLOCK_STATS_STATIC,
                BLOCK_STATS_ICONS,
                TIMER_NOTIFICATIONS,
                TIMER_CREDITS,
                TIMER_HUD_X,
                TIMER_HUD_Y,
                TIMER_HUD_SCALE,
                HOURLY_STATS_X,
                HOURLY_STATS_Y,
                HOURLY_STATS_SCALE,
                BLOCK_STATS_X,
                BLOCK_STATS_Y,
                BLOCK_STATS_SCALE,
                TIMER_NOTIFICATION_X,
                TIMER_NOTIFICATION_Y,
                TIMER_NOTIFICATION_SCALE,
                HUD_TITLE_HEX_COLOR,
                HUD_TEXT_HEX_COLOR,
                HUD_NUMBER_HEX_COLOR,
                HUD_INACTIVE_HEX_COLOR,
                MENU_HEX_COLOR,
                BPS_SMOOTHING,
                SMALL_DIG_ITEMS,
                SMALL_DIG_ITEM_SCALE,
                NO_SWINGING_ANIMATION,
                TRANSLUCENT_LAVA,
                LAVA_OPACITY,
                BLOCK_ESP_COLOR_MODE,
                BLOCK_ESP_HEX_COLOR,
                BLOCK_ESP_RENDER_MODE,
                BLOCK_ESP_OPACITY,
                BLOCK_ESP_RAINBOW_SPEED,
                GRAPH_LINE_HEX_COLOR,
                GRAPH_FILL_HEX_COLOR,
                GRAPH_FILL_OPACITY,
                GRAPH_BG_OPACITY,
                GRAPH_GRID_HEX_COLOR,
                GRAPH_GRID_OPACITY,
                GRAPH_SCALE_STEP
        );

        public static final ImmutableList<IConfigBase> SCOREBOARD_OPTIONS = ImmutableList.of(
                SCOREBOARD_VISIBLE,
                SCOREBOARD_SCORES_VISIBLE,
                SCOREBOARD_SCORE_COMMAS,
                SCOREBOARD_TAB_LIST_COMMAS,
                TIER_NAME_TAGS,
                SCOREBOARD_SCORE_ABBREVIATED,
                SCOREBOARD_SORTING,
                SCOREBOARD_MAX_ENTRIES,
                SCOREBOARD_POSITION,
                SCOREBOARD_Y_OFFSET,
                SCOREBOARD_SCALE,
                SCOREBOARD_BODY_OPACITY,
                SCOREBOARD_TITLE_OPACITY,
                SCOREBOARD_TEXT_OPACITY,
                SCOREBOARD_TITLE_TEXT_OPACITY,
                SCOREBOARD_DEFAULT_TEAM_CHAT
        );

        public static final ImmutableList<IConfigBase> PERSISTED_OPTIONS = ImmutableList.of(
                WEBSITE_SYNC_ENABLED,
                TOTAL_DIGS_SYNC_ENABLED,
                WEBSITE_SYNC_DEBUG,
                MAX_BLOCKS_PER_MINUTE,
                ABBREVIATED_NUMBERS,
                DAILY_GOAL,
                GOAL_PICKAXE_ANIMATION,
                SHARE_GOAL_MILESTONES,
                RECEIVE_GOAL_MILESTONES,
                HUD_X,
                HUD_Y,
                HUD_ALIGNMENT,
                HUD_SCALE,
                HUD_TEXT_BACKGROUND,
                HUD_TITLE_VISIBLE,
                HUD_GLOBAL_TOTAL_VISIBLE,
                HUD_WORLD_TOTAL_VISIBLE,
                HUD_SESSION_TOTAL_VISIBLE,
                HUD_DAILY_WEEK_VISIBLE,
                HUD_RECORDS_VISIBLE,
                HUD_FASTEST_100K_VISIBLE,
                HUD_SESSION_TIME_VISIBLE,
                HUD_DAILY_RESET_VISIBLE,
                HUD_TIMER_STATUS_VISIBLE,
                HUD_DAILY_GOAL_BAR_VISIBLE,
                ALWAYS_OVERRIDE_XP_BAR,
                SHOW_GOAL_PERCENT,
                GOAL_PERCENT_DECIMALS,
                GOAL_PERCENT_DECIMAL_PLACES,
                TIMER_HUD_VISIBLE,
                HOURLY_STATS_VISIBLE,
                BLOCKS_PER_MINUTE_VISIBLE,
                BLOCK_STATS_VISIBLE,
                BLOCK_STATS_BACKGROUND,
                BLOCK_STATS_STATIC,
                BLOCK_STATS_ICONS,
                TIMER_NOTIFICATIONS,
                TIMER_CREDITS,
                TIMER_HUD_X,
                TIMER_HUD_Y,
                TIMER_HUD_SCALE,
                HOURLY_STATS_X,
                HOURLY_STATS_Y,
                HOURLY_STATS_SCALE,
                BLOCK_STATS_X,
                BLOCK_STATS_Y,
                BLOCK_STATS_SCALE,
                TIMER_NOTIFICATION_X,
                TIMER_NOTIFICATION_Y,
                TIMER_NOTIFICATION_SCALE,
                HUD_TITLE_HEX_COLOR,
                HUD_TEXT_HEX_COLOR,
                HUD_NUMBER_HEX_COLOR,
                HUD_INACTIVE_HEX_COLOR,
                MENU_HEX_COLOR,
                BPS_SMOOTHING,
                SMALL_DIG_ITEMS,
                SMALL_DIG_ITEM_SCALE,
                NO_SWINGING_ANIMATION,
                TRANSLUCENT_LAVA,
                LAVA_OPACITY,
                SCOREBOARD_VISIBLE,
                SCOREBOARD_SCORES_VISIBLE,
                SCOREBOARD_SCORE_COMMAS,
                SCOREBOARD_TAB_LIST_COMMAS,
                TIER_NAME_TAGS,
                SCOREBOARD_SCORE_ABBREVIATED,
                SCOREBOARD_SORTING,
                SCOREBOARD_MAX_ENTRIES,
                SCOREBOARD_POSITION,
                SCOREBOARD_Y_OFFSET,
                SCOREBOARD_SCALE,
                SCOREBOARD_BODY_OPACITY,
                SCOREBOARD_TITLE_OPACITY,
                SCOREBOARD_TEXT_OPACITY,
                SCOREBOARD_TITLE_TEXT_OPACITY,
                SCOREBOARD_DEFAULT_TEAM_CHAT,
                BLOCK_ESP_COLOR_MODE,
                BLOCK_ESP_HEX_COLOR,
                BLOCK_ESP_RENDER_MODE,
                BLOCK_ESP_OPACITY,
                BLOCK_ESP_RAINBOW_SPEED,
                GRAPH_LINE_HEX_COLOR,
                GRAPH_FILL_HEX_COLOR,
                GRAPH_FILL_OPACITY,
                GRAPH_BG_OPACITY,
                GRAPH_GRID_HEX_COLOR,
                GRAPH_GRID_OPACITY,
                GRAPH_SCALE_STEP,
                PERIMETER_OUTLINE_BLOCKS_LIST
        );
    }

    public static final long DAILY_RESET_WEBSITE_SYNC_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    public static final long DEFAULT_WEBSITE_SYNC_INTERVAL_MS = DAILY_RESET_WEBSITE_SYNC_INTERVAL_MS;
    public static final long SUPPORTER_WEBSITE_SYNC_INTERVAL_MS = DAILY_RESET_WEBSITE_SYNC_INTERVAL_MS;
    public static final long SUPPORTER_PLUS_WEBSITE_SYNC_INTERVAL_MS = DAILY_RESET_WEBSITE_SYNC_INTERVAL_MS;
    public static final long MIN_WEBSITE_SYNC_INTERVAL_MS = DAILY_RESET_WEBSITE_SYNC_INTERVAL_MS;
    public static final long MAX_WEBSITE_SYNC_INTERVAL_MS = DAILY_RESET_WEBSITE_SYNC_INTERVAL_MS;
    public static long dailyProgress = 0L;
    public static long dailyGoalLastResetMs = System.currentTimeMillis();
    public static long dailyBlocksMined = 0L;
    public static String dailyBlocksDate = "";
    public static long weeklyBlocksMined = 0L;
    public static String weeklyBlocksWeek = "";
    public static long weeklyLastResetMs = 0L;
    public static long personalRecordDailyBlocks = 0L;
    public static long personalRecordWeeklyBlocks = 0L;
    public static long fastest100kMs = 0L;
    public static long fastest100kStartedAtMs = 0L;
    public static long fastest100kFinishedAtMs = 0L;
    public static String activeProjectId = "";
    public static String cloudSyncEndpoint = DEFAULT_CLOUD_SYNC_ENDPOINT;
    public static String cloudSyncSecret = "";
    public static String cloudClientId = "";
    public static String websiteLinkedMinecraftUuid = "";
    public static String websiteLinkedMinecraftUsername = "";
    public static String websiteSyncToken = "";
    public static long websiteLinkedAtMs = 0L;
    public static String websiteSyncTier = "free";
    public static long websiteSyncIntervalMs = DEFAULT_WEBSITE_SYNC_INTERVAL_MS;
    public static long websiteGlobalTotalBlocks = 0L;
    public static long websiteGlobalTotalUpdatedAtMs = 0L;
    public static long websiteLastSuccessfulSyncMs = 0L;
    private static final Map<String, Long> SOURCE_LAST_SUCCESSFUL_SYNC_MS = new LinkedHashMap<>();
    public static long totalBlocksMined = 0L;
    public static final List<ProjectEntry> PROJECTS = new ArrayList<>();
    public static final List<WorldStatsEntry> WORLD_STATS = new ArrayList<>();
    private static final Map<String, Set<String>> LEGACY_WORLD_ID_ALIASES = new LinkedHashMap<>();
    public static final String BLOCK_BREAKDOWN_SOURCE_MINECRAFT_STATS = "minecraft_stats";
    public static final String BLOCK_BREAKDOWN_SOURCE_LOCAL_OBSERVED = "local_observed";
    private static long settingsMigrationVersion;

    public static void onConfigLoaded()
    {
        boolean syncIdentityGenerated = false;
        boolean dailyGoalMigrated = false;
        boolean syncTimestampRecovered = false;

        if (PROJECTS.isEmpty())
        {
            PROJECTS.add(ProjectEntry.create("Main Project", 0L));
        }

        for (ProjectEntry project : PROJECTS)
        {
            if (project.id == null || project.id.isBlank())
            {
                project.id = UUID.randomUUID().toString();
            }
            if (project.name == null || project.name.isBlank())
            {
                project.name = "Project";
            }
            project.progress = Math.max(0L, project.progress);
        }

        if (activeProjectId == null || activeProjectId.isBlank() || getActiveProject() == null)
        {
            activeProjectId = PROJECTS.getFirst().id;
        }

        dailyProgress = Math.max(0L, dailyProgress);
        dailyGoalLastResetMs = Math.max(0L, dailyGoalLastResetMs);
        dailyBlocksMined = Math.max(0L, dailyBlocksMined);
        dailyBlocksDate = dailyBlocksDate == null ? "" : dailyBlocksDate.trim();
        weeklyBlocksMined = Math.max(0L, weeklyBlocksMined);
        weeklyBlocksWeek = weeklyBlocksWeek == null ? "" : weeklyBlocksWeek.trim();
        weeklyLastResetMs = Math.max(0L, weeklyLastResetMs);
        personalRecordDailyBlocks = Math.max(personalRecordDailyBlocks, dailyBlocksMined);
        personalRecordWeeklyBlocks = Math.max(personalRecordWeeklyBlocks, weeklyBlocksMined);
        fastest100kMs = Math.max(0L, fastest100kMs);
        fastest100kStartedAtMs = Math.max(0L, fastest100kStartedAtMs);
        fastest100kFinishedAtMs = Math.max(0L, fastest100kFinishedAtMs);
        totalBlocksMined = Math.max(0L, totalBlocksMined);
        boolean endpointMigrated = isLegacySupabaseSyncEndpoint(cloudSyncEndpoint);
        if (cloudSyncEndpoint == null || cloudSyncEndpoint.isBlank())
        {
            cloudSyncEndpoint = DEFAULT_CLOUD_SYNC_ENDPOINT;
        }
        else if (endpointMigrated)
        {
            cloudSyncEndpoint = DEFAULT_CLOUD_SYNC_ENDPOINT;
        }

        for (WorldStatsEntry entry : WORLD_STATS)
        {
            if (entry.worldId == null || entry.worldId.isBlank())
            {
                entry.worldId = "default";
            }
            if (entry.displayName == null || entry.displayName.isBlank())
            {
                entry.displayName = entry.worldId;
            }
            entry.kind = entry.kind == null || entry.kind.isBlank() ? "unknown" : entry.kind;
            entry.host = entry.host == null ? "" : entry.host;
            entry.totalBlocks = Math.max(0L, entry.totalBlocks);
            entry.scoreboardTotalBlocks = Math.max(0L, entry.scoreboardTotalBlocks);
            entry.scoreboardTotalUpdatedAtMs = Math.max(0L, entry.scoreboardTotalUpdatedAtMs);
            entry.pendingLocalBlocks = Math.max(0L, entry.pendingLocalBlocks);
            entry.lastSeenAt = Math.max(0L, entry.lastSeenAt);
            entry.blockBreakdown = sanitizeBlockBreakdown(entry.blockBreakdown);
            entry.blockBreakdownSource = sanitizeBlockBreakdownSource(entry.blockBreakdownSource);
            entry.blockBreakdownUpdatedAtMs = Math.max(0L, entry.blockBreakdownUpdatedAtMs);
        }
        boolean worldStatsMigrated = mergeCanonicalWorldStats();
        cloudSyncSecret = cloudSyncSecret == null ? "" : cloudSyncSecret.trim();
        if (cloudClientId == null || cloudClientId.isBlank())
        {
            cloudClientId = "mmm_" + UUID.randomUUID();
            syncIdentityGenerated = true;
        }
        websiteLinkedMinecraftUuid = websiteLinkedMinecraftUuid == null ? "" : websiteLinkedMinecraftUuid.trim().toLowerCase();
        websiteLinkedMinecraftUsername = websiteLinkedMinecraftUsername == null ? "" : websiteLinkedMinecraftUsername.trim();
        websiteSyncToken = websiteSyncToken == null ? "" : websiteSyncToken.trim();
        websiteLinkedAtMs = Math.max(0L, websiteLinkedAtMs);
        websiteSyncTier = normalizeWebsiteSyncTier(websiteSyncTier);
        websiteSyncIntervalMs = normalizeWebsiteSyncIntervalMs(websiteSyncIntervalMs);
        websiteGlobalTotalBlocks = Math.max(0L, websiteGlobalTotalBlocks);
        websiteGlobalTotalUpdatedAtMs = Math.max(0L, websiteGlobalTotalUpdatedAtMs);
        websiteLastSuccessfulSyncMs = Math.max(0L, websiteLastSuccessfulSyncMs);
        sanitizeSourceSyncTimestamps(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        if (websiteLastSuccessfulSyncMs > now)
        {
            MMM.LOGGER.warn("[MMM_SYNC] future success timestamp recovered previous={} replacement={} reason=system_clock_moved_back", websiteLastSuccessfulSyncMs, now);
            websiteLastSuccessfulSyncMs = now;
            syncTimestampRecovered = true;
        }
        if (Generic.DAILY_GOAL.getIntegerValue() < MIN_DAILY_GOAL)
        {
            Generic.DAILY_GOAL.setIntegerValue(MIN_DAILY_GOAL);
            dailyGoalMigrated = true;
        }
        PerimeterWallDigHelper.refreshFromConfig();

        Generic.BLOCK_ESP_HEX_COLOR.setValueFromString(normalizeBlockEspHexColor(Generic.BLOCK_ESP_HEX_COLOR.getStringValue()));
        Generic.HUD_TITLE_HEX_COLOR.setValueFromString(normalizeHexColor(Generic.HUD_TITLE_HEX_COLOR.getStringValue(), Generic.DEFAULT_HUD_TITLE_HEX_COLOR));
        Generic.HUD_TEXT_HEX_COLOR.setValueFromString(normalizeHexColor(Generic.HUD_TEXT_HEX_COLOR.getStringValue(), Generic.DEFAULT_HUD_TEXT_HEX_COLOR));
        Generic.HUD_NUMBER_HEX_COLOR.setValueFromString(normalizeHexColor(Generic.HUD_NUMBER_HEX_COLOR.getStringValue(), Generic.DEFAULT_HUD_NUMBER_HEX_COLOR));
        Generic.HUD_INACTIVE_HEX_COLOR.setValueFromString(normalizeHexColor(Generic.HUD_INACTIVE_HEX_COLOR.getStringValue(), Generic.DEFAULT_HUD_INACTIVE_HEX_COLOR));
        Generic.MENU_HEX_COLOR.setValueFromString(normalizeHexColor(Generic.MENU_HEX_COLOR.getStringValue(), Generic.DEFAULT_MENU_HEX_COLOR));
        Generic.BLOCK_ESP_OPACITY.setIntegerValue(Math.max(0, Math.min(100, Generic.BLOCK_ESP_OPACITY.getIntegerValue())));
        Generic.GRAPH_LINE_HEX_COLOR.setValueFromString(normalizeHexColor(Generic.GRAPH_LINE_HEX_COLOR.getStringValue(), Generic.DEFAULT_GRAPH_LINE_HEX_COLOR));
        Generic.GRAPH_FILL_HEX_COLOR.setValueFromString(normalizeHexColor(Generic.GRAPH_FILL_HEX_COLOR.getStringValue(), Generic.DEFAULT_GRAPH_FILL_HEX_COLOR));
        Generic.GRAPH_GRID_HEX_COLOR.setValueFromString(normalizeHexColor(Generic.GRAPH_GRID_HEX_COLOR.getStringValue(), Generic.DEFAULT_GRAPH_GRID_HEX_COLOR));
        Generic.GRAPH_FILL_OPACITY.setIntegerValue(Math.max(0, Math.min(100, Generic.GRAPH_FILL_OPACITY.getIntegerValue())));
        Generic.GRAPH_BG_OPACITY.setIntegerValue(Math.max(0, Math.min(100, Generic.GRAPH_BG_OPACITY.getIntegerValue())));
        Generic.GRAPH_GRID_OPACITY.setIntegerValue(Math.max(0, Math.min(100, Generic.GRAPH_GRID_OPACITY.getIntegerValue())));

        if (syncIdentityGenerated || dailyGoalMigrated || endpointMigrated || syncTimestampRecovered || worldStatsMigrated)
        {
            saveToFile();
        }
    }

    public static synchronized void loadFromFile()
    {
        File configFile = getPrimaryConfigFile();
        JsonObject primaryRoot = readJsonObject(configFile.toPath(), "primary config", true);
        long storedMigrationVersion = readMigrationVersion(primaryRoot);
        boolean migrationNeeded = storedMigrationVersion < CURRENT_SETTINGS_MIGRATION_VERSION;
        boolean importedLegacy = false;

        if (migrationNeeded)
        {
            for (LegacyConfigCandidate candidate : findMigrationCandidates(configFile.toPath()))
            {
                JsonObject beforeMerge = primaryRoot == null ? new JsonObject() : primaryRoot.deepCopy();
                primaryRoot = MmmConfigMigration.mergeMissingValues(primaryRoot, candidate.root());
                boolean candidateImported = primaryRoot.equals(beforeMerge) == false;
                importedLegacy |= candidateImported;
                if (candidateImported)
                {
                    try
                    {
                        Path backup = AtomicJsonStorage.createMigrationBackup(candidate.path());
                        MMM.LOGGER.info("[MMM] Imported missing settings/state from legacy config {} (backup: {})", candidate.path(), backup);
                    }
                    catch (Exception e)
                    {
                        MMM.LOGGER.warn("[MMM] Imported legacy config {} but could not create its backup: {}", candidate.path(), e.getMessage());
                    }
                }
                else
                {
                    MMM.LOGGER.info("[MMM] Skipped legacy config {} because it had no compatible values missing from MMM", candidate.path());
                }
            }
        }

        if (primaryRoot != null)
        {
            readSettings(primaryRoot);
            readCustomState(primaryRoot);
        }

        settingsMigrationVersion = CURRENT_SETTINGS_MIGRATION_VERSION;

        // Legacy instance files are migration inputs only. Once the shared state exists,
        // reading them again can restore counters that were intentionally reset.
        boolean sharedStateExists = hasReadableCrossVersionState();
        boolean importedLegacySharedState = sharedStateExists == false && importLegacyCrossVersionStateCandidates();
        boolean sharedStateLoaded = readCrossVersionState();
        onConfigLoaded();

        if (sharedStateLoaded == false || importedLegacySharedState)
        {
            writeCrossVersionState();
        }
        if (migrationNeeded || importedLegacy)
        {
            saveToFile();
        }
    }

    private static boolean readSettings(JsonObject root)
    {
        if (MmmConfigMigration.containsSettings(root) == false)
        {
            return false;
        }

        MmmConfigMigration.migrateLegacyFeatureToggleSections(root);
        ConfigUtils.readConfigBase(root, "Generic", Generic.PERSISTED_OPTIONS);
        ConfigUtils.readHotkeys(root, "GenericHotkeys", Hotkeys.HOTKEY_LIST);
        ConfigUtils.readHotkeyToggleOptions(root, MmmConfigMigration.HOTKEYS_SECTION, MmmConfigMigration.TOGGLES_SECTION, FeatureToggle.VALUES);
        return true;
    }

    private static List<LegacyConfigCandidate> findMigrationCandidates(Path primaryPath)
    {
        Path normalizedPrimary = primaryPath.toAbsolutePath().normalize();
        Set<Path> candidates = new LinkedHashSet<>();
        for (Path configDir : SharedStoragePaths.legacyConfigDirs())
        {
            for (String fileName : MIGRATION_CONFIG_FILE_NAMES)
            {
                Path candidate = configDir.resolve(fileName).toAbsolutePath().normalize();
                if (candidate.equals(normalizedPrimary) == false && Files.isRegularFile(candidate))
                {
                    candidates.add(candidate);
                }
            }
        }

        List<LegacyConfigCandidate> validCandidates = new ArrayList<>();
        candidates.stream()
                .sorted(Comparator.comparingLong(Configs::lastModifiedMillis).reversed())
                .forEach(path ->
                {
                    JsonObject root = readJsonObject(path, "legacy config", true);
                    if (root == null)
                    {
                        return;
                    }
                    if (MmmConfigMigration.containsSettings(root) || root.has("State"))
                    {
                        MMM.LOGGER.info("[MMM] Discovered compatible legacy config {}", path);
                        validCandidates.add(new LegacyConfigCandidate(path, root));
                    }
                    else
                    {
                        MMM.LOGGER.info("[MMM] Skipped legacy config {} because it contained no compatible MMM settings/state", path);
                    }
                });
        return validCandidates;
    }

    private static JsonObject readJsonObject(Path path, String context, boolean warnWhenMalformed)
    {
        if (path == null)
        {
            return null;
        }

        try
        {
            AtomicJsonStorage.ReadResult result = AtomicJsonStorage.readObjectWithBackup(path);
            if (result.value() != null)
            {
                if (result.recoveredFromBackup())
                {
                    MMM.LOGGER.warn("[MMM] Recovered {} from backup {} after the primary file was unavailable or malformed", context, result.source());
                    try
                    {
                        AtomicJsonStorage.write(path, result.value(), true);
                        MMM.LOGGER.info("[MMM] Repaired {} primary file {} from its valid backup", context, path);
                    }
                    catch (Exception repairFailure)
                    {
                        MMM.LOGGER.warn("[MMM] Loaded {} from backup but could not repair {}: {}", context, path, repairFailure.getMessage());
                    }
                }
                return result.value();
            }
        }
        catch (Exception e)
        {
            if (warnWhenMalformed || Files.exists(path) || Files.exists(AtomicJsonStorage.backupPath(path)))
            {
                MMM.LOGGER.warn("[MMM] Failed to load {} {}: {}", context, path, e.getMessage());
            }
        }
        return null;
    }

    private static long readMigrationVersion(JsonObject root)
    {
        if (root == null || root.has("State") == false || root.get("State").isJsonObject() == false)
        {
            return 0L;
        }
        return readLong(root.getAsJsonObject("State"), "settingsMigrationVersion", 0L, "config State");
    }

    private static long lastModifiedMillis(Path path)
    {
        try
        {
            return Files.getLastModifiedTime(path).toMillis();
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }

    private static boolean hasReadableCrossVersionState()
    {
        try
        {
            return AtomicJsonStorage.readObjectWithBackup(SharedStoragePaths.crossVersionStateFile()).value() != null;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private record LegacyConfigCandidate(Path path, JsonObject root)
    {
    }
    public static synchronized void saveToFile()
    {
        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "Generic", Generic.PERSISTED_OPTIONS);
        ConfigUtils.writeHotkeys(root, "GenericHotkeys", Hotkeys.HOTKEY_LIST);
        ConfigUtils.writeHotkeyToggleOptions(root, MmmConfigMigration.HOTKEYS_SECTION, MmmConfigMigration.TOGGLES_SECTION, FeatureToggle.VALUES);
        writeCustomState(root);

        try
        {
            AtomicJsonStorage.write(getPrimaryConfigFile().toPath(), root, true);
        }
        catch (Exception e)
        {
            MMM.LOGGER.error("[MMM] Failed to atomically save config {}: {}", getPrimaryConfigFile(), e.getMessage());
        }

        writeCrossVersionState();
    }

    public static List<BooleanHotkeyGuiWrapper> getWrappedToggles()
    {
        return FeatureToggle.VALUES.stream().map(toggle -> new BooleanHotkeyGuiWrapper(toggle.getName(), toggle, toggle.getKeybind())).toList();
    }

    public static List<Integer> getNotificationThresholds()
    {
        return List.of(25, 50, 75, 100);
    }

    public static ProjectEntry getActiveProject()
    {
        for (ProjectEntry project : PROJECTS)
        {
            if (project.id.equals(activeProjectId))
            {
                return project;
            }
        }
        return PROJECTS.isEmpty() ? null : PROJECTS.getFirst();
    }

    public static ProjectEntry createProject(String name)
    {
        ProjectEntry entry = ProjectEntry.create(name, 0L);
        PROJECTS.add(entry);
        if (activeProjectId == null || activeProjectId.isBlank())
        {
            activeProjectId = entry.id;
        }
        return entry;
    }

    public static boolean isBlockEspRainbow()
    {
        return Generic.BLOCK_ESP_COLOR_MODE.getOptionListValue() == BlockEspColorMode.RAINBOW;
    }

    public static boolean isBlockEspOutlineOnly()
    {
        return Generic.BLOCK_ESP_RENDER_MODE.getOptionListValue() == BlockEspRenderMode.OUTLINE_ONLY;
    }

    public static float getBlockEspOpacity()
    {
        return Generic.BLOCK_ESP_OPACITY.getIntegerValue() / 100.0F;
    }

    public static float getBlockEspRainbowSpeed()
    {
        return (float) Generic.BLOCK_ESP_RAINBOW_SPEED.getDoubleValue();
    }

    public static String normalizeBlockEspHexColor(String value)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("#"))
        {
            normalized = normalized.substring(1);
        }

        if (normalized.matches("(?i)[0-9a-f]{6}([0-9a-f]{2})?"))
        {
            return "#" + normalized.toUpperCase();
        }

        return Generic.DEFAULT_BLOCK_ESP_HEX_COLOR;
    }

    public static String normalizeHexColor(String value, String fallback)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("#"))
        {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("0x") || normalized.startsWith("0X"))
        {
            normalized = normalized.substring(2);
        }

        if (normalized.matches("(?i)[0-9a-f]{8}"))
        {
            return "#" + normalized.substring(2).toUpperCase();
        }

        if (normalized.matches("(?i)[0-9a-f]{6}"))
        {
            return "#" + normalized.toUpperCase();
        }

        return fallback;
    }

    public static int getHudNumberColor()
    {
        return parseOpaqueHexColor(Generic.HUD_NUMBER_HEX_COLOR.getStringValue(), Generic.DEFAULT_HUD_NUMBER_HEX_COLOR);
    }

    public static int getHudTitleColor()
    {
        return parseOpaqueHexColor(Generic.HUD_TITLE_HEX_COLOR.getStringValue(), Generic.DEFAULT_HUD_TITLE_HEX_COLOR);
    }

    public static int getHudTextColor()
    {
        return parseOpaqueHexColor(Generic.HUD_TEXT_HEX_COLOR.getStringValue(), Generic.DEFAULT_HUD_TEXT_HEX_COLOR);
    }

    public static int getHudInactiveColor()
    {
        return parseOpaqueHexColor(Generic.HUD_INACTIVE_HEX_COLOR.getStringValue(), Generic.DEFAULT_HUD_INACTIVE_HEX_COLOR);
    }

    public static int getMenuColor()
    {
        return parseOpaqueHexColor(Generic.MENU_HEX_COLOR.getStringValue(), Generic.DEFAULT_MENU_HEX_COLOR);
    }

    public static int getGraphLineColor()  { return parseOpaqueHexColor(Generic.GRAPH_LINE_HEX_COLOR.getStringValue(), Generic.DEFAULT_GRAPH_LINE_HEX_COLOR); }
    public static int getGraphFillColor()  { return parseOpaqueHexColor(Generic.GRAPH_FILL_HEX_COLOR.getStringValue(), Generic.DEFAULT_GRAPH_FILL_HEX_COLOR); }
    public static int getGraphGridColor()  { return parseOpaqueHexColor(Generic.GRAPH_GRID_HEX_COLOR.getStringValue(), Generic.DEFAULT_GRAPH_GRID_HEX_COLOR); }
    public static float getGraphFillOpacity() { return Generic.GRAPH_FILL_OPACITY.getIntegerValue() / 100.0F; }
    public static float getGraphBgOpacity() { return Generic.GRAPH_BG_OPACITY.getIntegerValue() / 100.0F; }
    public static float getGraphGridOpacity() { return Generic.GRAPH_GRID_OPACITY.getIntegerValue() / 100.0F; }
    public static float getGraphScaleStep() { return (float) Generic.GRAPH_SCALE_STEP.getIntegerValue(); }

    private static int parseOpaqueHexColor(String value, String fallback)
    {
        String hex = normalizeHexColor(value, fallback);
        long parsed = Long.parseLong(hex.substring(1), 16) & 0x00FFFFFFL;
        return (int) (0xFF000000L | parsed);
    }

    public static BpsSmoothing getBpsSmoothingMode()
    {
        return (BpsSmoothing) Generic.BPS_SMOOTHING.getOptionListValue();
    }

    @Override
    public void load()
    {
        loadFromFile();
    }

    @Override
    public void save()
    {
        saveToFile();
    }

    private static void readCustomState(JsonObject root)
    {
        if (root.has("State") && root.get("State").isJsonObject())
        {
            JsonObject state = root.getAsJsonObject("State");
            settingsMigrationVersion = readLong(state, "settingsMigrationVersion", settingsMigrationVersion, "config State");
            dailyProgress = readLong(state, "dailyProgress", dailyProgress, "config State");
            dailyGoalLastResetMs = readLong(state, "dailyGoalLastResetMs", dailyGoalLastResetMs, "config State");
            dailyBlocksMined = readLong(state, "dailyBlocksMined", dailyBlocksMined, "config State");
            dailyBlocksDate = readString(state, "dailyBlocksDate", dailyBlocksDate, "config State");
            weeklyBlocksMined = readLong(state, "weeklyBlocksMined", weeklyBlocksMined, "config State");
            weeklyBlocksWeek = readString(state, "weeklyBlocksWeek", weeklyBlocksWeek, "config State");
            weeklyLastResetMs = readLong(state, "weeklyLastResetMs", weeklyLastResetMs, "config State");
            personalRecordDailyBlocks = readLong(state, "personalRecordDailyBlocks", personalRecordDailyBlocks, "config State");
            personalRecordWeeklyBlocks = readLong(state, "personalRecordWeeklyBlocks", personalRecordWeeklyBlocks, "config State");
            fastest100kMs = readLong(state, "fastest100kMs", fastest100kMs, "config State");
            fastest100kStartedAtMs = readLong(state, "fastest100kStartedAtMs", fastest100kStartedAtMs, "config State");
            fastest100kFinishedAtMs = readLong(state, "fastest100kFinishedAtMs", fastest100kFinishedAtMs, "config State");
            activeProjectId = readString(state, "activeProjectId", activeProjectId, "config State");
            Generic.WEBSITE_SYNC_ENABLED.setBooleanValue(readBoolean(state, "cloudSyncEnabled", Generic.WEBSITE_SYNC_ENABLED.getBooleanValue(), "config State"));
            Generic.TOTAL_DIGS_SYNC_ENABLED.setBooleanValue(readBoolean(state, "totalDigsSyncEnabled", Generic.TOTAL_DIGS_SYNC_ENABLED.getBooleanValue(), "config State"));
            cloudSyncEndpoint = readString(state, "cloudSyncEndpoint", cloudSyncEndpoint, "config State");
            cloudSyncSecret = readString(state, "cloudSyncSecret", cloudSyncSecret, "config State");
            cloudClientId = readString(state, "cloudClientId", cloudClientId, "config State");
            websiteLinkedMinecraftUuid = readString(state, "websiteLinkedMinecraftUuid", websiteLinkedMinecraftUuid, "config State");
            websiteLinkedMinecraftUsername = readString(state, "websiteLinkedMinecraftUsername", websiteLinkedMinecraftUsername, "config State");
            websiteSyncToken = readString(state, "websiteSyncToken", websiteSyncToken, "config State");
            websiteLinkedAtMs = readLong(state, "websiteLinkedAtMs", websiteLinkedAtMs, "config State");
            websiteSyncTier = readString(state, "websiteSyncTier", websiteSyncTier, "config State");
            websiteSyncIntervalMs = readLong(state, "websiteSyncIntervalMs", websiteSyncIntervalMs, "config State");
            websiteGlobalTotalBlocks = readLong(state, "websiteGlobalTotalBlocks", websiteGlobalTotalBlocks, "config State");
            websiteGlobalTotalUpdatedAtMs = readLong(state, "websiteGlobalTotalUpdatedAtMs", websiteGlobalTotalUpdatedAtMs, "config State");
            websiteLastSuccessfulSyncMs = readLong(state, "websiteLastSuccessfulSyncMs", websiteLastSuccessfulSyncMs, "config State");
            readSourceSyncTimestamps(state);
            totalBlocksMined = readLong(state, "totalBlocksMined", totalBlocksMined, "config State");
            PROJECTS.clear();
            WORLD_STATS.clear();
            if (state.has("projects") && state.get("projects").isJsonArray())
            {
                int index = 0;
                for (JsonElement element : state.getAsJsonArray("projects"))
                {
                    if (element.isJsonObject())
                    {
                        JsonObject object = element.getAsJsonObject();
                        ProjectEntry project = new ProjectEntry();
                        String context = "config State projects[" + index + "]";
                        project.id = readString(object, "id", UUID.randomUUID().toString(), context);
                        project.name = readString(object, "name", "Project", context);
                        project.progress = readLong(object, "progress", 0L, context);
                        PROJECTS.add(project);
                    }
                    index++;
                }
            }
            if (state.has("worldStats") && state.get("worldStats").isJsonArray())
            {
                int index = 0;
                for (JsonElement element : state.getAsJsonArray("worldStats"))
                {
                    if (element.isJsonObject())
                    {
                        JsonObject object = element.getAsJsonObject();
                        String context = "config State worldStats[" + index + "]";
                        WorldStatsEntry entry = new WorldStatsEntry();
                        entry.worldId = readString(object, "worldId", "default", context);
                        entry.displayName = readString(object, "displayName", entry.worldId, context);
                        entry.kind = readString(object, "kind", "unknown", context);
                        entry.host = readString(object, "host", "", context);
                        entry.totalBlocks = readLong(object, "totalBlocks", 0L, context);
                        entry.scoreboardTotalBlocks = readLong(object, "scoreboardTotalBlocks", 0L, context);
                        entry.scoreboardTotalUpdatedAtMs = readLong(object, "scoreboardTotalUpdatedAtMs", 0L, context);
                        entry.pendingLocalBlocks = readLong(object, "pendingLocalBlocks", 0L, context);
                        entry.lastSeenAt = readLong(object, "lastSeenAt", 0L, context);
                        entry.blockBreakdown = readBlockBreakdown(object);
                        entry.blockBreakdownSource = readString(object, "blockBreakdownSource", "", context);
                        entry.blockBreakdownUpdatedAtMs = readLong(object, "blockBreakdownUpdatedAtMs", 0L, context);
                        WORLD_STATS.add(entry);
                    }
                    index++;
                }
            }
        }
    }

    private static boolean readCrossVersionState()
    {
        Path statePath = SharedStoragePaths.crossVersionStateFile();
        try
        {
            AtomicJsonStorage.ReadResult result = AtomicJsonStorage.readObjectWithBackup(statePath);
            if (result.value() == null)
            {
                return false;
            }
            if (result.recoveredFromBackup())
            {
                MMM.LOGGER.warn("[MMM] Recovered cross-version state from backup {}", result.source());
                try
                {
                    AtomicJsonStorage.write(statePath, result.value(), true);
                    MMM.LOGGER.info("[MMM] Repaired cross-version state primary file {}", statePath);
                }
                catch (Exception repairFailure)
                {
                    MMM.LOGGER.warn("[MMM] Loaded cross-version state from backup but could not repair {}: {}", statePath, repairFailure.getMessage());
                }
            }

            JsonObject root = result.value();
            JsonObject state = root.has("State") && root.get("State").isJsonObject() ? root.getAsJsonObject("State") : root;
            mergeCrossVersionState(state, "cross-version State");
            return true;
        }
        catch (Exception e)
        {
            if (Files.exists(statePath) || Files.exists(AtomicJsonStorage.backupPath(statePath)))
            {
                MMM.LOGGER.warn("[MMM] Failed to load cross-version state file {}: {}", statePath, e.getMessage());
            }
            return false;
        }
    }

    private static boolean importLegacyCrossVersionStateCandidates()
    {
        boolean imported = false;
        Path currentConfigPath = getPrimaryConfigFile().toPath().toAbsolutePath().normalize();
        Set<String> configFileNames = MIGRATION_CONFIG_FILE_NAMES;

        for (Path configDir : SharedStoragePaths.legacyConfigDirs())
        {
            for (String fileName : configFileNames)
            {
                Path candidate = configDir.resolve(fileName).toAbsolutePath().normalize();
                if (candidate.equals(currentConfigPath) || Files.isRegularFile(candidate) == false)
                {
                    continue;
                }

                JsonObject root = readJsonObject(candidate, "legacy shared state", false);
                if (root == null)
                {
                    continue;
                }
                JsonObject state = root.has("State") && root.get("State").isJsonObject() ? root.getAsJsonObject("State") : root;
                mergeCrossVersionState(state, "legacy config State " + candidate);
                imported = true;
            }
        }

        return imported;
    }

    private static void mergeCrossVersionState(JsonObject state, String context)
    {
        if (state.has("dailyGoal"))
        {
            Generic.DAILY_GOAL.setIntegerValue(clampDailyGoal(readLong(state, "dailyGoal", Generic.DAILY_GOAL.getIntegerValue(), context)));
        }

        long incomingDailyProgress = readLong(state, "dailyProgress", 0L, context);
        long incomingDailyBlocks = readLong(state, "dailyBlocksMined", 0L, context);
        String incomingDailyDate = normalizeStateKey(readString(state, "dailyBlocksDate", "", context));
        long incomingDailyResetMs = readLong(state, "dailyGoalLastResetMs", 0L, context);
        mergeDailyState(incomingDailyDate, Math.max(incomingDailyBlocks, incomingDailyProgress), incomingDailyResetMs);

        long incomingWeeklyBlocks = readLong(state, "weeklyBlocksMined", 0L, context);
        String incomingWeeklyWeek = normalizeStateKey(readString(state, "weeklyBlocksWeek", "", context));
        long incomingWeeklyResetMs = readLong(state, "weeklyLastResetMs", 0L, context);
        mergeWeeklyState(incomingWeeklyWeek, incomingWeeklyBlocks, incomingWeeklyResetMs, context);

        personalRecordDailyBlocks = Math.max(personalRecordDailyBlocks, readLong(state, "personalRecordDailyBlocks", 0L, context));
        personalRecordWeeklyBlocks = Math.max(personalRecordWeeklyBlocks, readLong(state, "personalRecordWeeklyBlocks", 0L, context));
        personalRecordDailyBlocks = Math.max(personalRecordDailyBlocks, dailyBlocksMined);
        personalRecordWeeklyBlocks = Math.max(personalRecordWeeklyBlocks, weeklyBlocksMined);
    }

    private static void mergeDailyState(String incomingDate, long incomingBlocks, long incomingResetMs)
    {
        long now = System.currentTimeMillis();
        String currentKey = PeriodKeys.currentDailyKey(now);
        PeriodKeys.Relation localRelation = PeriodKeys.dailyRelation(dailyBlocksDate, now);
        if (localRelation == PeriodKeys.Relation.OLDER)
        {
            personalRecordDailyBlocks = Math.max(personalRecordDailyBlocks, dailyBlocksMined);
            dailyBlocksDate = currentKey;
            dailyBlocksMined = 0L;
            dailyProgress = 0L;
            dailyGoalLastResetMs = now;
        }
        else if (localRelation == PeriodKeys.Relation.CURRENT)
        {
            dailyBlocksDate = PeriodKeys.normalizeDailyKey(dailyBlocksDate, now);
        }
        else
        {
            // Missing, malformed, or future keys can be caused by old builds or a
            // corrected system clock. Preserve progress and repair the marker.
            dailyBlocksDate = currentKey;
            if (dailyGoalLastResetMs <= 0L || dailyGoalLastResetMs > now)
            {
                dailyGoalLastResetMs = now;
            }
        }

        PeriodKeys.Relation incomingRelation = PeriodKeys.dailyRelation(incomingDate, now);
        if (incomingRelation == PeriodKeys.Relation.CURRENT
                || ((incomingRelation == PeriodKeys.Relation.MISSING || incomingRelation == PeriodKeys.Relation.INVALID)
                && incomingBlocks > 0L && dailyBlocksMined == 0L))
        {
            long mergedBlocks = Math.max(Math.max(dailyBlocksMined, dailyProgress), Math.max(0L, incomingBlocks));
            dailyBlocksMined = mergedBlocks;
            dailyProgress = mergedBlocks;
        }
        if (incomingResetMs > 0L && incomingResetMs <= now)
        {
            dailyGoalLastResetMs = Math.max(dailyGoalLastResetMs, incomingResetMs);
        }
    }

    private static void mergeWeeklyState(String incomingWeek, long incomingBlocks, long incomingResetMs, String context)
    {
        long now = System.currentTimeMillis();
        WeeklyProgressPolicy.Result local = WeeklyProgressPolicy.evaluate(
                weeklyBlocksMined,
                weeklyBlocksWeek,
                weeklyLastResetMs,
                now);
        applyWeeklyResult(local, context + " local");

        PeriodKeys.Relation incomingRelation = PeriodKeys.weeklyRelation(incomingWeek, now);
        if (incomingRelation == PeriodKeys.Relation.CURRENT
                || ((incomingRelation == PeriodKeys.Relation.MISSING || incomingRelation == PeriodKeys.Relation.INVALID)
                && incomingBlocks > 0L && weeklyBlocksMined == 0L))
        {
            weeklyBlocksMined = Math.max(weeklyBlocksMined, Math.max(0L, incomingBlocks));
            if (incomingResetMs > 0L && incomingResetMs <= now)
            {
                weeklyLastResetMs = Math.max(weeklyLastResetMs, incomingResetMs);
            }
        }
        personalRecordWeeklyBlocks = Math.max(personalRecordWeeklyBlocks, Math.max(0L, incomingBlocks));
    }

    private static void applyWeeklyResult(WeeklyProgressPolicy.Result result, String context)
    {
        long previousBlocks = weeklyBlocksMined;
        String previousKey = weeklyBlocksWeek == null ? "" : weeklyBlocksWeek;
        long previousResetMs = weeklyLastResetMs;
        if (result.reset())
        {
            personalRecordWeeklyBlocks = Math.max(personalRecordWeeklyBlocks, previousBlocks);
        }
        weeklyBlocksMined = result.blocks();
        weeklyBlocksWeek = result.periodKey();
        weeklyLastResetMs = result.lastResetAtMs();

        if (result.changed())
        {
            MMM.LOGGER.info(
                    "[MMM_PERIOD] weekly-state-change context={} previousBlocks={} newBlocks={} previousKey={} newKey={} previousResetAt={} nextResetAt={} reason={}",
                    context,
                    previousBlocks,
                    weeklyBlocksMined,
                    previousKey,
                    weeklyBlocksWeek,
                    formatTimestamp(previousResetMs),
                    formatTimestamp(result.nextResetAtMs()),
                    result.reason());
        }
    }

    private static String formatTimestamp(long timestampMs)
    {
        return timestampMs <= 0L ? "never" : java.time.Instant.ofEpochMilli(timestampMs).toString();
    }
    private static String normalizeStateKey(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static synchronized void writeCrossVersionState()
    {
        Path statePath = SharedStoragePaths.crossVersionStateFile();
        Path lockPath = statePath.resolveSibling(statePath.getFileName() + ".lock");

        try
        {
            Files.createDirectories(statePath.getParent());
            try (FileChannel lockChannel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                 FileLock stateLock = lockChannel.lock())
            {
                if (stateLock.isValid() == false)
                {
                    throw new IllegalStateException("Could not acquire the cross-version state lock.");
                }
                // Merge while holding the cross-process lock so another game instance
                // cannot replace a newer period value with a stale in-memory snapshot.
                int dailyGoal = Generic.DAILY_GOAL.getIntegerValue();
                readCrossVersionState();
                Generic.DAILY_GOAL.setIntegerValue(clampDailyGoal(dailyGoal));

                JsonObject state = new JsonObject();
                state.addProperty("dailyGoal", Generic.DAILY_GOAL.getIntegerValue());
                state.addProperty("dailyProgress", dailyProgress);
                state.addProperty("dailyGoalLastResetMs", dailyGoalLastResetMs);
                state.addProperty("dailyBlocksMined", dailyBlocksMined);
                state.addProperty("dailyBlocksDate", dailyBlocksDate == null ? "" : dailyBlocksDate);
                state.addProperty("weeklyBlocksMined", weeklyBlocksMined);
                state.addProperty("weeklyBlocksWeek", weeklyBlocksWeek == null ? "" : weeklyBlocksWeek);
                state.addProperty("weeklyLastResetMs", weeklyLastResetMs);
                state.addProperty("personalRecordDailyBlocks", personalRecordDailyBlocks);
                state.addProperty("personalRecordWeeklyBlocks", personalRecordWeeklyBlocks);

                JsonObject root = new JsonObject();
                root.add("State", state);
                AtomicJsonStorage.write(statePath, root, true);
            }
        }
        catch (Exception e)
        {
            MMM.LOGGER.error("[MMM] Failed to atomically save cross-version state {}: {}", statePath, e.getMessage());
        }
    }
    private static int clampDailyGoal(long value)
    {
        return (int) Math.max(MIN_DAILY_GOAL, Math.min(1_000_000L, value));
    }

    private static void writeCustomState(JsonObject root)
    {
        JsonObject state = new JsonObject();
        state.addProperty("settingsMigrationVersion", CURRENT_SETTINGS_MIGRATION_VERSION);
        state.addProperty("dailyProgress", dailyProgress);
        state.addProperty("dailyGoalLastResetMs", dailyGoalLastResetMs);
        state.addProperty("dailyBlocksMined", dailyBlocksMined);
        state.addProperty("dailyBlocksDate", dailyBlocksDate == null ? "" : dailyBlocksDate);
        state.addProperty("weeklyBlocksMined", weeklyBlocksMined);
        state.addProperty("weeklyBlocksWeek", weeklyBlocksWeek == null ? "" : weeklyBlocksWeek);
        state.addProperty("weeklyLastResetMs", weeklyLastResetMs);
        state.addProperty("personalRecordDailyBlocks", personalRecordDailyBlocks);
        state.addProperty("personalRecordWeeklyBlocks", personalRecordWeeklyBlocks);
        state.addProperty("fastest100kMs", fastest100kMs);
        state.addProperty("fastest100kStartedAtMs", fastest100kStartedAtMs);
        state.addProperty("fastest100kFinishedAtMs", fastest100kFinishedAtMs);
        state.addProperty("activeProjectId", activeProjectId == null ? "" : activeProjectId);
        state.addProperty("cloudSyncEnabled", Generic.WEBSITE_SYNC_ENABLED.getBooleanValue());
        state.addProperty("totalDigsSyncEnabled", Generic.TOTAL_DIGS_SYNC_ENABLED.getBooleanValue());
        state.addProperty("cloudSyncEndpoint", cloudSyncEndpoint == null ? DEFAULT_CLOUD_SYNC_ENDPOINT : cloudSyncEndpoint);
        state.addProperty("cloudSyncSecret", cloudSyncSecret == null ? "" : cloudSyncSecret);
        state.addProperty("cloudClientId", cloudClientId == null ? "" : cloudClientId);
        state.addProperty("websiteLinkedMinecraftUuid", websiteLinkedMinecraftUuid == null ? "" : websiteLinkedMinecraftUuid);
        state.addProperty("websiteLinkedMinecraftUsername", websiteLinkedMinecraftUsername == null ? "" : websiteLinkedMinecraftUsername);
        state.addProperty("websiteSyncToken", websiteSyncToken == null ? "" : websiteSyncToken);
        state.addProperty("websiteLinkedAtMs", websiteLinkedAtMs);
        state.addProperty("websiteSyncTier", normalizeWebsiteSyncTier(websiteSyncTier));
        state.addProperty("websiteSyncIntervalMs", normalizeWebsiteSyncIntervalMs(websiteSyncIntervalMs));
        state.addProperty("websiteGlobalTotalBlocks", websiteGlobalTotalBlocks);
        state.addProperty("websiteGlobalTotalUpdatedAtMs", websiteGlobalTotalUpdatedAtMs);
        state.addProperty("websiteLastSuccessfulSyncMs", websiteLastSuccessfulSyncMs);
        JsonObject sourceSyncTimestamps = new JsonObject();
        SOURCE_LAST_SUCCESSFUL_SYNC_MS.forEach(sourceSyncTimestamps::addProperty);
        state.add("sourceLastSuccessfulSyncMs", sourceSyncTimestamps);
        state.addProperty("totalBlocksMined", totalBlocksMined);

        JsonArray projects = new JsonArray();
        for (ProjectEntry project : PROJECTS)
        {
            JsonObject object = new JsonObject();
            object.addProperty("id", project.id);
            object.addProperty("name", project.name);
            object.addProperty("progress", project.progress);
            projects.add(object);
        }
        state.add("projects", projects);

        JsonArray worldStats = new JsonArray();
        for (WorldStatsEntry entry : WORLD_STATS)
        {
            JsonObject object = new JsonObject();
            object.addProperty("worldId", entry.worldId);
            object.addProperty("displayName", entry.displayName);
            object.addProperty("kind", entry.kind);
            object.addProperty("host", entry.host);
            object.addProperty("totalBlocks", entry.totalBlocks);
            object.addProperty("scoreboardTotalBlocks", entry.scoreboardTotalBlocks);
            object.addProperty("scoreboardTotalUpdatedAtMs", entry.scoreboardTotalUpdatedAtMs);
            object.addProperty("pendingLocalBlocks", entry.pendingLocalBlocks);
            object.addProperty("lastSeenAt", entry.lastSeenAt);
            object.addProperty("blockBreakdownSource", sanitizeBlockBreakdownSource(entry.blockBreakdownSource));
            object.addProperty("blockBreakdownUpdatedAtMs", entry.blockBreakdownUpdatedAtMs);
            object.add("blockBreakdown", writeBlockBreakdown(entry.blockBreakdown));
            worldStats.add(object);
        }
        state.add("worldStats", worldStats);
        root.add("State", state);
    }

    public static WorldStatsEntry getOrCreateWorldStats(String worldId, String displayName, String kind, String host)
    {
        String suppliedWorldId = worldId == null || worldId.isBlank() ? "default" : worldId.trim();
        String normalizedWorldId = WorldIdentity.canonicalWorldId(suppliedWorldId, kind, host);
        if (suppliedWorldId.equals(normalizedWorldId) == false)
        {
            LEGACY_WORLD_ID_ALIASES
                    .computeIfAbsent(normalizedWorldId, ignored -> new LinkedHashSet<>())
                    .add(suppliedWorldId);
        }
        for (WorldStatsEntry entry : WORLD_STATS)
        {
            if (normalizedWorldId.equals(entry.worldId))
            {
                entry.displayName = displayName == null || displayName.isBlank() ? entry.displayName : displayName;
                entry.kind = kind == null || kind.isBlank() ? entry.kind : kind;
                entry.host = host == null ? "" : host;
                if (entry.blockBreakdown == null)
                {
                    entry.blockBreakdown = new LinkedHashMap<>();
                }
                entry.blockBreakdownSource = sanitizeBlockBreakdownSource(entry.blockBreakdownSource);
                return entry;
            }
        }

        WorldStatsEntry entry = new WorldStatsEntry();
        entry.worldId = normalizedWorldId;
        entry.displayName = displayName == null || displayName.isBlank() ? normalizedWorldId : displayName;
        entry.kind = kind == null || kind.isBlank() ? "unknown" : kind;
        entry.host = host == null ? "" : host;
        entry.blockBreakdown = new LinkedHashMap<>();
        entry.blockBreakdownSource = "";
        WORLD_STATS.add(entry);
        return entry;
    }

    public static Set<String> getLegacyWorldIds(String canonicalWorldId)
    {
        if (canonicalWorldId == null || canonicalWorldId.isBlank())
        {
            return Set.of();
        }

        Set<String> aliases = LEGACY_WORLD_ID_ALIASES.get(canonicalWorldId.trim());
        return aliases == null ? Set.of() : Set.copyOf(aliases);
    }

    static boolean mergeCanonicalWorldStats()
    {
        LEGACY_WORLD_ID_ALIASES.clear();
        Map<String, WorldStatsEntry> merged = new LinkedHashMap<>();
        boolean changed = false;

        for (WorldStatsEntry entry : WORLD_STATS)
        {
            String originalWorldId = entry.worldId == null || entry.worldId.isBlank()
                    ? "default"
                    : entry.worldId.trim();
            String canonicalWorldId = WorldIdentity.canonicalWorldId(originalWorldId, entry.kind, entry.host);
            if (canonicalWorldId.equals(originalWorldId) == false)
            {
                LEGACY_WORLD_ID_ALIASES
                        .computeIfAbsent(canonicalWorldId, ignored -> new LinkedHashSet<>())
                        .add(originalWorldId);
                changed = true;
            }

            WorldStatsEntry existing = merged.get(canonicalWorldId);
            if (existing == null)
            {
                entry.worldId = canonicalWorldId;
                merged.put(canonicalWorldId, entry);
                continue;
            }

            if (existing != entry)
            {
                LEGACY_WORLD_ID_ALIASES
                        .computeIfAbsent(canonicalWorldId, ignored -> new LinkedHashSet<>())
                        .add(originalWorldId);
                mergeWorldStatsEntry(existing, entry);
                changed = true;
            }
        }

        if (changed)
        {
            WORLD_STATS.clear();
            WORLD_STATS.addAll(merged.values());
        }
        return changed;
    }

    private static void mergeWorldStatsEntry(WorldStatsEntry target, WorldStatsEntry candidate)
    {
        boolean candidateIsNewer = candidate.lastSeenAt >= target.lastSeenAt;
        if (candidateIsNewer && candidate.displayName != null && candidate.displayName.isBlank() == false)
        {
            target.displayName = candidate.displayName;
        }
        if (candidateIsNewer && candidate.kind != null && candidate.kind.isBlank() == false)
        {
            target.kind = candidate.kind;
        }
        if ((candidateIsNewer || target.host == null || target.host.isBlank())
                && candidate.host != null && candidate.host.isBlank() == false)
        {
            target.host = candidate.host;
        }

        // These are cumulative snapshots of the same server. Summing duplicates would inflate totals.
        target.totalBlocks = Math.max(target.totalBlocks, candidate.totalBlocks);
        target.scoreboardTotalBlocks = Math.max(target.scoreboardTotalBlocks, candidate.scoreboardTotalBlocks);
        target.scoreboardTotalUpdatedAtMs = Math.max(target.scoreboardTotalUpdatedAtMs, candidate.scoreboardTotalUpdatedAtMs);
        target.pendingLocalBlocks = Math.max(target.pendingLocalBlocks, candidate.pendingLocalBlocks);
        target.lastSeenAt = Math.max(target.lastSeenAt, candidate.lastSeenAt);

        if (target.blockBreakdown == null)
        {
            target.blockBreakdown = new LinkedHashMap<>();
        }
        if (candidate.blockBreakdown != null)
        {
            for (Map.Entry<String, Long> block : candidate.blockBreakdown.entrySet())
            {
                target.blockBreakdown.merge(block.getKey(), Math.max(0L, block.getValue()), Math::max);
            }
        }
        if (candidate.blockBreakdownUpdatedAtMs >= target.blockBreakdownUpdatedAtMs)
        {
            target.blockBreakdownSource = candidate.blockBreakdownSource;
        }
        target.blockBreakdownUpdatedAtMs = Math.max(target.blockBreakdownUpdatedAtMs, candidate.blockBreakdownUpdatedAtMs);
        target.blockBreakdownSource = sanitizeBlockBreakdownSource(target.blockBreakdownSource);
    }

    private static Map<String, Long> readBlockBreakdown(JsonObject object)
    {
        if (object.has("blockBreakdown") == false || object.get("blockBreakdown").isJsonObject() == false)
        {
            return new LinkedHashMap<>();
        }

        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("blockBreakdown").entrySet())
        {
            if (entry.getKey() == null || entry.getKey().isBlank())
            {
                continue;
            }
            try
            {
                long count = entry.getValue().getAsLong();
                if (count > 0L)
                {
                    breakdown.put(entry.getKey(), count);
                }
            }
            catch (Exception e)
            {
                MMM.LOGGER.warn("[MMM] Failed to parse config State blockBreakdown field '{}' in {}: {}", entry.getKey(), getPrimaryConfigFile(), e.getMessage());
            }
        }
        return breakdown;
    }

    private static long readLong(JsonObject object, String field, long fallback, String context)
    {
        if (object == null || object.has(field) == false)
        {
            return fallback;
        }

        try
        {
            return object.get(field).getAsLong();
        }
        catch (Exception e)
        {
            MMM.LOGGER.warn("[MMM] Failed to parse {} field '{}' in {}: {}", context, field, getPrimaryConfigFile(), e.getMessage());
            return fallback;
        }
    }

    private static String readString(JsonObject object, String field, String fallback, String context)
    {
        if (object == null || object.has(field) == false)
        {
            return fallback;
        }

        try
        {
            return object.get(field).getAsString();
        }
        catch (Exception e)
        {
            MMM.LOGGER.warn("[MMM] Failed to parse {} field '{}' in {}: {}", context, field, getPrimaryConfigFile(), e.getMessage());
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject object, String field, boolean fallback, String context)
    {
        if (object == null || object.has(field) == false)
        {
            return fallback;
        }

        try
        {
            return object.get(field).getAsBoolean();
        }
        catch (Exception e)
        {
            MMM.LOGGER.warn("[MMM] Failed to parse {} field '{}' in {}: {}", context, field, getPrimaryConfigFile(), e.getMessage());
            return fallback;
        }
    }

    private static JsonObject writeBlockBreakdown(Map<String, Long> breakdown)
    {
        JsonObject object = new JsonObject();
        if (breakdown == null)
        {
            return object;
        }

        breakdown.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().isBlank() == false && entry.getValue() != null && entry.getValue() > 0L)
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> object.addProperty(entry.getKey(), entry.getValue()));
        return object;
    }

    public static Map<String, Long> sanitizeBlockBreakdown(Map<String, Long> breakdown)
    {
        return BlockBreakdownCatalog.sanitize(breakdown);
    }

    public static String sanitizeBlockBreakdownSource(String source)
    {
        if (BLOCK_BREAKDOWN_SOURCE_MINECRAFT_STATS.equals(source))
        {
            return BLOCK_BREAKDOWN_SOURCE_MINECRAFT_STATS;
        }
        if (BLOCK_BREAKDOWN_SOURCE_LOCAL_OBSERVED.equals(source))
        {
            return BLOCK_BREAKDOWN_SOURCE_LOCAL_OBSERVED;
        }
        return "";
    }

    public static String normalizeWebsiteSyncTier(String tier)
    {
        String normalized = tier == null ? "" : tier.trim().toLowerCase();
        return switch (normalized)
        {
            case "supporter", "supporter_plus", "owner" -> normalized;
            default -> "free";
        };
    }

    public static long normalizeWebsiteSyncIntervalMs(long intervalMs)
    {
        // Version 1.0.16 uses one daily cadence for every account tier. The persisted
        // value remains for backward compatibility, but cannot shorten the schedule.
        return DEFAULT_WEBSITE_SYNC_INTERVAL_MS;
    }

    public static synchronized long getSourceLastSuccessfulSyncMs(String sourceKey)
    {
        String normalized = normalizeSourceSyncKey(sourceKey);
        return normalized.isBlank() ? 0L : Math.max(0L, SOURCE_LAST_SUCCESSFUL_SYNC_MS.getOrDefault(normalized, 0L));
    }

    public static synchronized void recordSourceSuccessfulSync(String sourceKey, long timestampMs)
    {
        String normalized = normalizeSourceSyncKey(sourceKey);
        if (normalized.isBlank() || timestampMs <= 0L)
        {
            return;
        }

        long boundedTimestamp = Math.min(timestampMs, System.currentTimeMillis());
        SOURCE_LAST_SUCCESSFUL_SYNC_MS.put(normalized, boundedTimestamp);
        websiteLastSuccessfulSyncMs = Math.max(websiteLastSuccessfulSyncMs, boundedTimestamp);
    }

    public static synchronized void clearSourceSyncCooldowns()
    {
        SOURCE_LAST_SUCCESSFUL_SYNC_MS.clear();
    }

    private static void readSourceSyncTimestamps(JsonObject state)
    {
        SOURCE_LAST_SUCCESSFUL_SYNC_MS.clear();
        if (state.has("sourceLastSuccessfulSyncMs") == false || state.get("sourceLastSuccessfulSyncMs").isJsonObject() == false)
        {
            return;
        }

        JsonObject timestamps = state.getAsJsonObject("sourceLastSuccessfulSyncMs");
        for (Map.Entry<String, JsonElement> entry : timestamps.entrySet())
        {
            String sourceKey = normalizeSourceSyncKey(entry.getKey());
            try
            {
                long timestampMs = entry.getValue().getAsLong();
                if (sourceKey.isBlank() == false && timestampMs > 0L)
                {
                    SOURCE_LAST_SUCCESSFUL_SYNC_MS.put(sourceKey, timestampMs);
                }
            }
            catch (RuntimeException ignored)
            {
            }
        }
    }

    private static synchronized void sanitizeSourceSyncTimestamps(long now)
    {
        SOURCE_LAST_SUCCESSFUL_SYNC_MS.replaceAll((sourceKey, timestampMs) -> Math.max(0L, Math.min(now, timestampMs == null ? 0L : timestampMs)));
        SOURCE_LAST_SUCCESSFUL_SYNC_MS.entrySet().removeIf(entry -> entry.getKey().isBlank() || entry.getValue() <= 0L);
    }

    private static String normalizeSourceSyncKey(String sourceKey)
    {
        if (sourceKey == null)
        {
            return "";
        }
        String normalized = sourceKey.trim().toLowerCase(Locale.ROOT);
        return normalized.substring(0, Math.min(256, normalized.length()));
    }
    private static boolean isLegacySupabaseSyncEndpoint(String endpoint)
    {
        if (endpoint == null)
        {
            return false;
        }

        String normalized = endpoint.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("supabase.co/functions/v1/mmm-sync")
                || normalized.contains("jmspoiryzfilppiovhmf.supabase.co");
    }

    private static File getPrimaryConfigFile()
    {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME).toFile();
    }

    public static class ProjectEntry
    {
        public String id;
        public String name;
        public long progress;

        public static ProjectEntry create(String name, long progress)
        {
            ProjectEntry entry = new ProjectEntry();
            entry.id = UUID.randomUUID().toString();
            entry.name = name;
            entry.progress = progress;
            return entry;
        }
    }

    public static class WorldStatsEntry
    {
        public String worldId;
        public String displayName;
        public String kind;
        public String host;
        public long totalBlocks;
        public long scoreboardTotalBlocks;
        public long scoreboardTotalUpdatedAtMs;
        public long pendingLocalBlocks;
        public long lastSeenAt;
        public Map<String, Long> blockBreakdown = new LinkedHashMap<>();
        public long blockBreakdownUpdatedAtMs;
        public String blockBreakdownSource = "";
    }

    public enum BlockEspColorMode implements IConfigOptionListEntry
    {
        RAINBOW("rainbow", "Rainbow"),
        SINGLE_COLOR("single_color", "Single Color");

        private final String value;
        private final String displayName;

        BlockEspColorMode(String value, String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return this.value;
        }

        @Override
        public String getDisplayName()
        {
            return this.displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward)
        {
            return values()[(this.ordinal() + (forward ? 1 : values().length - 1)) % values().length];
        }

        @Override
        public IConfigOptionListEntry fromString(String value)
        {
            for (BlockEspColorMode mode : values())
            {
                if (mode.value.equalsIgnoreCase(value) || mode.displayName.equalsIgnoreCase(value))
                {
                    return mode;
                }
            }

            return RAINBOW;
        }
    }

    public enum BlockEspRenderMode implements IConfigOptionListEntry
    {
        FULL_BLOCK("full_block", "Full Block"),
        OUTLINE_ONLY("outline_only", "Outline Only");

        private final String value;
        private final String displayName;

        BlockEspRenderMode(String value, String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return this.value;
        }

        @Override
        public String getDisplayName()
        {
            return this.displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward)
        {
            return values()[(this.ordinal() + (forward ? 1 : values().length - 1)) % values().length];
        }

        @Override
        public IConfigOptionListEntry fromString(String value)
        {
            for (BlockEspRenderMode mode : values())
            {
                if (mode.value.equalsIgnoreCase(value) || mode.displayName.equalsIgnoreCase(value))
                {
                    return mode;
                }
            }

            return FULL_BLOCK;
        }
    }

    public enum HudAlignment implements IConfigOptionListEntry
    {
        TOP_LEFT("top_left", "Top Left"),
        TOP_RIGHT("top_right", "Top Right"),
        BOTTOM_LEFT("bottom_left", "Bottom Left"),
        BOTTOM_RIGHT("bottom_right", "Bottom Right");

        private final String value;
        private final String displayName;

        HudAlignment(String value, String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return this.value;
        }

        @Override
        public String getDisplayName()
        {
            return this.displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward)
        {
            return values()[(this.ordinal() + (forward ? 1 : values().length - 1)) % values().length];
        }

        @Override
        public IConfigOptionListEntry fromString(String value)
        {
            for (HudAlignment alignment : values())
            {
                if (alignment.value.equalsIgnoreCase(value) || alignment.displayName.equalsIgnoreCase(value))
                {
                    return alignment;
                }
            }

            return TOP_LEFT;
        }
    }

    public enum BpsSmoothing implements IConfigOptionListEntry
    {
        UNSTABLE("unstable", "Unstable", 20, 20),
        FAST("fast", "Fast", 60, 40),
        STABLE("stable", "Stable", 100, 20);

        private final String value;
        private final String displayName;
        private final int windowTicks;
        private final int preferredMinimumTicks;

        BpsSmoothing(String value, String displayName, int windowTicks, int preferredMinimumTicks)
        {
            this.value = value;
            this.displayName = displayName;
            this.windowTicks = windowTicks;
            this.preferredMinimumTicks = preferredMinimumTicks;
        }

        public int getWindowTicks()
        {
            return this.windowTicks;
        }

        public int getPreferredMinimumTicks()
        {
            return this.preferredMinimumTicks;
        }

        @Override
        public String getStringValue()
        {
            return this.value;
        }

        @Override
        public String getDisplayName()
        {
            return this.displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward)
        {
            return values()[(this.ordinal() + (forward ? 1 : values().length - 1)) % values().length];
        }

        @Override
        public IConfigOptionListEntry fromString(String value)
        {
            for (BpsSmoothing mode : values())
            {
                if (mode.value.equalsIgnoreCase(value) || mode.displayName.equalsIgnoreCase(value))
                {
                    return mode;
                }
            }

            return FAST;
        }
    }

    public enum ScoreboardSorting implements IConfigOptionListEntry
    {
        SCORE_DESCENDING("score_descending", "Score: High to Low"),
        SCORE_ASCENDING("score_ascending", "Score: Low to High"),
        NAME_DESCENDING("name_descending", "Name: Z to A"),
        NAME_ASCENDING("name_ascending", "Name: A to Z");

        private final String value;
        private final String displayName;

        ScoreboardSorting(String value, String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        @Override
        public String getStringValue()
        {
            return this.value;
        }

        @Override
        public String getDisplayName()
        {
            return this.displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward)
        {
            return values()[(this.ordinal() + (forward ? 1 : values().length - 1)) % values().length];
        }

        @Override
        public IConfigOptionListEntry fromString(String value)
        {
            for (ScoreboardSorting sorting : values())
            {
                if (sorting.value.equalsIgnoreCase(value) || sorting.displayName.equalsIgnoreCase(value))
                {
                    return sorting;
                }
            }
            return SCORE_DESCENDING;
        }
    }

    public enum ScoreboardPosition implements IConfigOptionListEntry
    {
        LEFT("left", "Left"),
        LEFT_UPPER("left_upper", "Left Upper"),
        LEFT_LOWER("left_lower", "Left Lower"),
        RIGHT("right", "Right"),
        RIGHT_UPPER("right_upper", "Right Upper"),
        RIGHT_LOWER("right_lower", "Right Lower");

        private final String value;
        private final String displayName;

        ScoreboardPosition(String value, String displayName)
        {
            this.value = value;
            this.displayName = displayName;
        }

        public boolean isLeft()
        {
            return this == LEFT || this == LEFT_UPPER || this == LEFT_LOWER;
        }

        @Override
        public String getStringValue()
        {
            return this.value;
        }

        @Override
        public String getDisplayName()
        {
            return this.displayName;
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward)
        {
            return values()[(this.ordinal() + (forward ? 1 : values().length - 1)) % values().length];
        }

        @Override
        public IConfigOptionListEntry fromString(String value)
        {
            for (ScoreboardPosition position : values())
            {
                if (position.value.equalsIgnoreCase(value) || position.displayName.equalsIgnoreCase(value))
                {
                    return position;
                }
            }
            return RIGHT;
        }
    }

}
