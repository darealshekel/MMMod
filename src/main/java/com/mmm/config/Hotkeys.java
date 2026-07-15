package com.mmm.config;

import java.util.List;

import com.google.common.collect.ImmutableList;

import fi.dy.masa.malilib.config.options.ConfigHotkey;

public final class Hotkeys
{
    public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey("openConfigGui", "X,V", "Open the MMM config GUI");
    public static final ConfigHotkey OPEN_SUMMARY = new ConfigHotkey("openSummary", "LEFT_ALT,S", "Open the current session summary");
    public static final ConfigHotkey OPEN_HISTORY = new ConfigHotkey("openHistory", "LEFT_ALT,H", "Open the saved session history");
    public static final ConfigHotkey PAUSE_SESSION = new ConfigHotkey("pauseSession", "LEFT_ALT,P", "Pause or resume the current mining session");
    public static final ConfigHotkey TOGGLE_SESSION = new ConfigHotkey("toggleSession", "LEFT_ALT,T", "Start or end the current mining session");
    public static final ConfigHotkey EXPORT_HISTORY = new ConfigHotkey("exportHistory", "", "Export session history for the current world/server");
    public static final ConfigHotkey SCOREBOARD_PAGE_UP = new ConfigHotkey("scoreboardPageUp", "PAGE_UP", "Show the previous scoreboard page");
    public static final ConfigHotkey SCOREBOARD_PAGE_DOWN = new ConfigHotkey("scoreboardPageDown", "PAGE_DOWN", "Show the next scoreboard page");
    public static final ConfigHotkey TOGGLE_SCOREBOARD = new ConfigHotkey("toggleScoreboard", "", "Hide or show the sidebar scoreboard");
    public static final ConfigHotkey OPEN_SCOREBOARD = new ConfigHotkey("openScoreboard", "", "Open MMM scoreboard settings");
    public static final ConfigHotkey EXPORT_SCOREBOARD = new ConfigHotkey("exportScoreboard", "BACKSLASH", "Open recorded scoreboard exports");
    public static final ConfigHotkey EDIT_SCOREBOARD = new ConfigHotkey("editScoreboard", "C", "Edit the current scoreboard with operator permission");
    public static final ConfigHotkey TOGGLE_SCORE_COMMAS = new ConfigHotkey("toggleScoreCommas", "", "Toggle thousands separators in scoreboard scores");
    public static final ConfigHotkey TOGGLE_SCORE_ABBREVIATION = new ConfigHotkey("toggleScoreAbbreviation", "", "Toggle abbreviated scoreboard scores");
    public static final ConfigHotkey TOGGLE_NO_SWINGING_ANIMATION = new ConfigHotkey("toggleNoSwingingAnimation", "", "Toggle the static first-person tool setting");

    public static final List<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
            OPEN_CONFIG_GUI,
            OPEN_SUMMARY,
            OPEN_HISTORY,
            PAUSE_SESSION,
            TOGGLE_SESSION,
            EXPORT_HISTORY,
            SCOREBOARD_PAGE_UP,
            SCOREBOARD_PAGE_DOWN,
            TOGGLE_SCOREBOARD,
            OPEN_SCOREBOARD,
            EXPORT_SCOREBOARD,
            EDIT_SCOREBOARD,
            TOGGLE_SCORE_COMMAS,
            TOGGLE_SCORE_ABBREVIATION,
            TOGGLE_NO_SWINGING_ANIMATION
    );

    private Hotkeys()
    {
    }
}
