package com.mmm.config;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import com.mmm.feature.BlockEspRenderer;
import com.mmm.feature.PerimeterWallDigHelper;
import com.mmm.feature.TranslucentLavaRenderer;
import com.mmm.hud.SessionHistoryScreen;
import com.mmm.hud.SummaryScreen;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.hotkey.MmmHotkey;
import com.mmm.scoreboard.ScoreboardEditScreen;
import com.mmm.scoreboard.ScoreboardRecordsScreen;
import com.mmm.scoreboard.ScoreboardScreen;
import com.mmm.scoreboard.ScoreboardService;
import com.mmm.tracker.MiningStats;
import com.mmm.ui.MmmSettingsScreen;
import com.mmm.util.MmmMessages;

public final class Callbacks
{
    private Callbacks()
    {
    }

    public static void init()
    {
        for (FeatureToggle toggle : FeatureToggle.values())
        {
            toggle.getHotkey().setCallback(() -> {
                toggle.toggleBooleanValue();
                Configs.saveToFile();
                MmmMessages.actionbar("%s: %s", toggle.getPrettyName(), toggle.getBooleanValue() ? "ON" : "OFF");
            });
            toggle.setValueChangeCallback(config -> Configs.saveToFile());
        }

        Hotkeys.OPEN_CONFIG_GUI.setCallback(() -> withClient(client -> client.gui.setScreen(new MmmSettingsScreen(client.gui.screen(), true))));
        Hotkeys.OPEN_SUMMARY.setCallback(() -> withClient(client -> client.gui.setScreen(new SummaryScreen(MiningStats.getCurrentSession(), client.gui.screen()))));
        Hotkeys.OPEN_HISTORY.setCallback(() -> withClient(client -> client.gui.setScreen(new SessionHistoryScreen(client.gui.screen()))));
        Hotkeys.PAUSE_SESSION.setCallback(Callbacks::pauseSession);
        Hotkeys.TOGGLE_SESSION.setCallback(Callbacks::startOrEndSession);
        Hotkeys.EXPORT_HISTORY.setCallback(Callbacks::exportHistory);
        Hotkeys.SCOREBOARD_PAGE_UP.setCallback(() -> MmmMessages.actionbar(ScoreboardService.pageUp() ? "Previous scoreboard page" : "Already on the first scoreboard page"));
        Hotkeys.SCOREBOARD_PAGE_DOWN.setCallback(() -> MmmMessages.actionbar(ScoreboardService.pageDown() ? "Next scoreboard page" : "Already on the last scoreboard page"));
        Hotkeys.TOGGLE_SCOREBOARD.setCallback(() -> toggleAndSave(Configs.Generic.SCOREBOARD_VISIBLE, "Scoreboard shown", "Scoreboard hidden"));
        Hotkeys.OPEN_SCOREBOARD.setCallback(() -> withClient(client -> client.gui.setScreen(new ScoreboardScreen(client.gui.screen()))));
        Hotkeys.EXPORT_SCOREBOARD.setCallback(() -> withClient(client -> client.gui.setScreen(new ScoreboardRecordsScreen(client.gui.screen()))));
        Hotkeys.EDIT_SCOREBOARD.setCallback(Callbacks::editScoreboard);
        Hotkeys.TOGGLE_SCORE_COMMAS.setCallback(() -> toggleAndSave(Configs.Generic.SCOREBOARD_SCORE_COMMAS, "Score commas enabled", "Score commas disabled"));
        Hotkeys.TOGGLE_SCORE_ABBREVIATION.setCallback(() -> toggleAndSave(Configs.Generic.SCOREBOARD_SCORE_ABBREVIATED, "Short scores enabled", "Short scores disabled"));
        Hotkeys.TOGGLE_NO_SWINGING_ANIMATION.setCallback(() -> toggleAndSave(Configs.Generic.NO_SWINGING_ANIMATION, "No Swing Animation enabled", "No Swing Animation disabled"));

        Configs.Generic.PERIMETER_OUTLINE_BLOCKS_LIST.setValueChangeCallback(config -> {
            PerimeterWallDigHelper.setOutlineBlocks(config.getStrings());
            Configs.saveToFile();
        });
        PerimeterWallDigHelper.refreshFromConfig();
        Configs.Generic.BLOCK_ESP_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeBlockEspHexColor(config.getStringValue())));
        Configs.Generic.HUD_TITLE_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_HUD_TITLE_HEX_COLOR)));
        Configs.Generic.HUD_TEXT_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_HUD_TEXT_HEX_COLOR)));
        Configs.Generic.HUD_NUMBER_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_HUD_NUMBER_HEX_COLOR)));
        Configs.Generic.HUD_INACTIVE_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_HUD_INACTIVE_HEX_COLOR)));
        Configs.Generic.HUD_BACKGROUND_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_HUD_BACKGROUND_HEX_COLOR)));
        Configs.Generic.MENU_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_MENU_HEX_COLOR)));
        Configs.Generic.BREAKING_INDICATOR_START_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_BREAKING_INDICATOR_START_HEX_COLOR)));
        Configs.Generic.BREAKING_INDICATOR_END_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_BREAKING_INDICATOR_END_HEX_COLOR)));
        Configs.Generic.GRAPH_LINE_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_GRAPH_LINE_HEX_COLOR)));
        Configs.Generic.GRAPH_FILL_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_GRAPH_FILL_HEX_COLOR)));
        Configs.Generic.GRAPH_GRID_HEX_COLOR.setValueChangeCallback(config -> normalizeAndSave(config, Configs.normalizeHexColor(config.getStringValue(), Configs.Generic.DEFAULT_GRAPH_GRID_HEX_COLOR)));
        Configs.Generic.BPS_SMOOTHING.setValueChangeCallback(config -> {
            MiningStats.onBpsSmoothingChanged();
            Configs.saveToFile();
        });
        Configs.Generic.TRANSLUCENT_LAVA.setValueChangeCallback(config -> reloadLavaAndSave());
        Configs.Generic.LAVA_OPACITY.setValueChangeCallback(config -> reloadLavaAndSave());
        Configs.Generic.BLOCK_ESP_COLOR_MODE.setValueChangeCallback(config -> Configs.saveToFile());
        Configs.Generic.BLOCK_ESP_RENDER_MODE.setValueChangeCallback(config -> Configs.saveToFile());
        Configs.Generic.BLOCK_ESP_OPACITY.setValueChangeCallback(config -> Configs.saveToFile());
        Configs.Generic.BLOCK_ESP_RAINBOW_SPEED.setValueChangeCallback(config -> Configs.saveToFile());
        Configs.Generic.GRAPH_FILL_OPACITY.setValueChangeCallback(config -> Configs.saveToFile());
        Configs.Generic.GRAPH_GRID_OPACITY.setValueChangeCallback(config -> Configs.saveToFile());
        Configs.Generic.GRAPH_SCALE_STEP.setValueChangeCallback(config -> Configs.saveToFile());
        BlockEspRenderer.refreshConfig();
    }

    private static void startOrEndSession()
    {
        if (MiningStats.isSessionActive() == false)
        {
            MiningStats.startNewSession();
            MmmMessages.actionbar("Mining session started");
            return;
        }

        SessionData finished = MiningStats.finaliseSession();
        if (SessionHistory.isQualifyingSession(finished))
        {
            MmmMessages.actionbar("Mining session ended and saved");
        }
        else
        {
            MmmMessages.actionbar("Mining session ended - 10,000 blocks required to save");
        }
    }
    private static void pauseSession()
    {
        if (!MiningStats.isSessionActive())
        {
            MmmMessages.actionbar("No active mining session");
            return;
        }
        MmmMessages.actionbar(MiningStats.togglePauseSession() ? "Mining session paused" : "Mining session resumed");
    }

    private static void exportHistory()
    {
        try
        {
            Path exported = com.mmm.storage.SessionHistory.exportToFile();
            MmmMessages.actionbar("Exported mining history to %s", exported.getFileName());
        }
        catch (IOException exception)
        {
            MmmMessages.error("Failed to export mining history");
        }
    }

    private static void editScoreboard()
    {
        withClient(client -> ScoreboardService.getSidebarObjective(client).ifPresentOrElse(
                objective -> client.gui.setScreen(new ScoreboardEditScreen(client.gui.screen(), objective)),
                () -> MmmMessages.actionbar("No scoreboard is available to edit")));
    }

    private static void toggleAndSave(com.mmm.config.value.IConfigBoolean config, String enabled, String disabled)
    {
        config.toggleBooleanValue();
        Configs.saveToFile();
        MmmMessages.actionbar(config.getBooleanValue() ? enabled : disabled);
    }

    private static void normalizeAndSave(com.mmm.config.value.ConfigColor config, String value)
    {
        config.setValueFromString(value);
        Configs.saveToFile();
    }

    private static void reloadLavaAndSave()
    {
        TranslucentLavaRenderer.requestReload();
        Configs.saveToFile();
    }

    private static void withClient(java.util.function.Consumer<Minecraft> action)
    {
        Minecraft client = Minecraft.getInstance();
        if (client != null)
        {
            action.accept(client);
        }
    }
}