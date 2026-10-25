package com.mmm.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mmm.config.value.IConfigBoolean;
import com.mmm.config.value.ValueChangeCallback;
import com.mmm.hotkey.MmmHotkey;

public enum FeatureToggle implements IConfigBoolean
{
    MMM_MINING_TRACKER("mmmMiningTracker", true, "", "Turns all MMM mining tracking on or off."),
    MMM_DAILY_GOAL("mmmDailyGoal", true, "", "Tracks your daily mining goal."),
    MMM_BLOCK_ESP("mmmBlockEsp", false, "", "Highlights the block under your crosshair."),
    MMM_FLAT_DIGGER("mmmFlatDigger", false, "", "Stops you from digging below your feet."),
    MMM_PERIMETER_WALL_DIG_HELPER("mmmPerimeterWallDigHelper", false, "", "Protects the configured perimeter floor blocks."),
    MMM_NOTIFICATIONS("mmmGoalNotifications", true, "", "Shows a message when you reach a goal milestone."),
    MMM_SOUND_ALERTS("mmmGoalSoundAlerts", true, "", "Plays a sound at your chosen milestone."),
    MMM_SUMMARY_ON_EXIT("mmmSummaryOnExit", true, "", "Opens your session summary when you leave."),
    MMM_CARRY_GOAL_PROGRESS("mmmCarryGoalProgress", true, "", "Keeps today's progress between sessions."),
    MMM_HUD("mmmMiningHud", true, "", "Shows the main mining HUD."),
    MMM_HUD_PROJECT("mmmMiningHudProject", true, "", "Shows your active project."),
    MMM_HUD_TOTAL_MINED("mmmMiningHudTotalMined", true, "", "Enables the mining total lines."),
    MMM_HUD_GOAL_PROGRESS("mmmMiningHudGoalProgress", true, "", "Replaces the XP bar with daily-goal progress while Tab is held."),
    MMM_HUD_BLOCKS_PER_HOUR("mmmMiningHudBlocksPerHour", true, "", "Shows Blocks/hr and Blocks/sec."),
    MMM_HUD_ETA("mmmMiningHudEta", true, "", "Shows the estimated time to your goal."),
    MMM_HUD_BOUNDING_BOX("mmmMiningHudBoundingBox", false, "", "Adds one background behind the main HUD."),
    MMM_HUD_SPEED_GRAPH("mmmMiningHudSpeedGraph", true, "", "Shows your live mining-speed graph."),
    MMM_TOGGLE_TAB("mmmToggleTab", false, "", "Keeps the player list open without holding Tab.");

    public static final ImmutableList<FeatureToggle> VALUES = ImmutableList.copyOf(values());

    private final String name;
    private final String prettyName;
    private final String comment;
    private final boolean defaultValue;
    private final MmmHotkey hotkey;
    private boolean value;
    private ValueChangeCallback<FeatureToggle> callback;

    FeatureToggle(String name, boolean defaultValue, String defaultHotkey, String comment)
    {
        this.name = name;
        this.prettyName = splitCamelCase(name.substring(3));
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.comment = comment;
        this.hotkey = new MmmHotkey(name, defaultHotkey, comment);
        this.hotkey.setCallback(this::toggleBooleanValue);
    }

    @Override public String getName() { return this.name; }
    @Override public String getPrettyName() { return this.prettyName; }
    @Override public String getComment() { return this.comment; }

    String getLegacyConfigName()
    {
        return "tweak" + this.name.substring(3);
    }

    public String getConfigGuiDisplayName() { return this.prettyName; }
    public String getTranslatedName() { return this.name; }
    public MmmHotkey getHotkey() { return this.hotkey; }
    public MmmHotkey getKeybind() { return this.hotkey; }

    @Override public String getStringValue() { return Boolean.toString(this.value); }
    @Override public String getDefaultStringValue() { return Boolean.toString(this.defaultValue); }
    @Override public void setValueFromString(String value) { setBooleanValue(Boolean.parseBoolean(value)); }
    @Override public boolean getBooleanValue() { return this.value; }
    @Override public boolean getDefaultBooleanValue() { return this.defaultValue; }

    @Override
    public void setBooleanValue(boolean value)
    {
        if (this.value != value)
        {
            this.value = value;
            if (this.callback != null)
            {
                this.callback.onValueChanged(this);
            }
        }
    }

    public void setValueChangeCallback(ValueChangeCallback<FeatureToggle> callback)
    {
        this.callback = callback;
    }

    @Override public boolean isModified() { return this.value != this.defaultValue; }
    @Override public boolean isModified(String value) { return Boolean.parseBoolean(value) != this.defaultValue; }
    @Override public void resetToDefault() { setBooleanValue(this.defaultValue); }
    @Override public JsonElement getAsJsonElement() { return new JsonPrimitive(this.value); }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element != null && element.isJsonPrimitive())
        {
            setBooleanValue(element.getAsBoolean());
        }
    }

    private static String splitCamelCase(String value)
    {
        String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}