package com.mmm.config;

import com.google.common.collect.ImmutableList;
import com.mmm.hotkey.MmmHotkey;
import java.util.List;

public final class Hotkeys
{
    public static final MmmHotkey OPEN_CONFIG_GUI = new MmmHotkey("openConfigGui", "X,V", "Open the MMM config GUI");
    public static final MmmHotkey OPEN_SUMMARY = new MmmHotkey("openSummary", "LEFT_ALT,S", "Open the current session summary");
    public static final MmmHotkey OPEN_HISTORY = new MmmHotkey("openHistory", "LEFT_ALT,H", "Open the saved session history");
    public static final MmmHotkey PAUSE_SESSION = new MmmHotkey("pauseSession", "LEFT_ALT,P", "Pause or resume the current mining session");
    public static final MmmHotkey TOGGLE_SESSION = new MmmHotkey("toggleSession", "LEFT_ALT,T", "Start or end the current mining session");
    public static final MmmHotkey EXPORT_HISTORY = new MmmHotkey("exportHistory", "", "Export session history for the current world/server");
    public static final MmmHotkey SCOREBOARD_PAGE_UP = new MmmHotkey("scoreboardPageUp", "PAGE_UP", "Show the previous scoreboard page");
    public static final MmmHotkey SCOREBOARD_PAGE_DOWN = new MmmHotkey("scoreboardPageDown", "PAGE_DOWN", "Show the next scoreboard page");
    public static final MmmHotkey TOGGLE_SCOREBOARD = new MmmHotkey("toggleScoreboard", "", "Hide or show the sidebar scoreboard");
    public static final MmmHotkey OPEN_SCOREBOARD = new MmmHotkey("openScoreboard", "", "Open MMM scoreboard settings");
    public static final MmmHotkey EXPORT_SCOREBOARD = new MmmHotkey("exportScoreboard", "BACKSLASH", "Open recorded scoreboard exports");
    public static final MmmHotkey EDIT_SCOREBOARD = new MmmHotkey("editScoreboard", "C", "Edit the current scoreboard with operator permission");
    public static final MmmHotkey TOGGLE_SCORE_COMMAS = new MmmHotkey("toggleScoreCommas", "", "Toggle thousands separators in scoreboard scores");
    public static final MmmHotkey TOGGLE_SCORE_ABBREVIATION = new MmmHotkey("toggleScoreAbbreviation", "", "Toggle abbreviated scoreboard scores");
    public static final MmmHotkey TOGGLE_NO_SWINGING_ANIMATION = new MmmHotkey("toggleNoSwingingAnimation", "", "Toggle the static first-person tool setting");

    public static final List<MmmHotkey> HOTKEY_LIST = ImmutableList.of(
            OPEN_CONFIG_GUI, OPEN_SUMMARY, OPEN_HISTORY, PAUSE_SESSION, TOGGLE_SESSION,
            EXPORT_HISTORY, SCOREBOARD_PAGE_UP, SCOREBOARD_PAGE_DOWN, TOGGLE_SCOREBOARD,
            OPEN_SCOREBOARD, EXPORT_SCOREBOARD, EDIT_SCOREBOARD, TOGGLE_SCORE_COMMAS,
            TOGGLE_SCORE_ABBREVIATION, TOGGLE_NO_SWINGING_ANIMATION);

    private Hotkeys()
    {
    }
}