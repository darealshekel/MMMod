package com.mmm.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigBoolean;
import fi.dy.masa.malilib.config.IConfigNotifiable;
import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBooleanConfigWithMessage;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.interfaces.IValueChangeCallback;
import fi.dy.masa.malilib.util.StringUtils;

public enum FeatureToggle implements IHotkeyTogglable, IConfigNotifiable<IConfigBoolean>
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
    private final String comment;
    private final boolean defaultValue;
    private boolean value;
    private final IKeybind keybind;
    private IValueChangeCallback<IConfigBoolean> callback;

    FeatureToggle(String name, boolean defaultValue, String defaultHotkey, String comment)
    {
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.comment = comment;
        this.keybind = KeybindMulti.fromStorageString(defaultHotkey, KeybindSettings.DEFAULT);
        this.keybind.setCallback(new KeyCallbackToggleBooleanConfigWithMessage(this));
    }

    @Override
    public ConfigType getType()
    {
        return ConfigType.HOTKEY;
    }

    @Override
    public String getName()
    {
        return this.name;
    }

    @Override
    public String getPrettyName()
    {
        return StringUtils.splitCamelCase(this.name.substring(3));
    }

    String getLegacyConfigName()
    {
        return "tweak" + this.name.substring(3);
    }

    @Override
    public String getConfigGuiDisplayName()
    {
        return this.getPrettyName();
    }

    @Override
    public String getComment()
    {
        return this.comment;
    }

    @Override
    public String getTranslatedName()
    {
        return this.name;
    }

    @Override
    public void setPrettyName(String s)
    {
    }

    @Override
    public void setTranslatedName(String s)
    {
    }

    @Override
    public void setComment(String s)
    {
    }

    @Override
    public String getStringValue()
    {
        return String.valueOf(this.value);
    }

    @Override
    public String getDefaultStringValue()
    {
        return String.valueOf(this.defaultValue);
    }

    @Override
    public void setValueFromString(String value)
    {
        this.setBooleanValue(Boolean.parseBoolean(value));
    }

    @Override
    public void onValueChanged()
    {
        if (this.callback != null)
        {
            this.callback.onValueChanged(this);
        }
    }

    @Override
    public void setValueChangeCallback(IValueChangeCallback<IConfigBoolean> callback)
    {
        this.callback = callback;
    }

    @Override
    public IKeybind getKeybind()
    {
        return this.keybind;
    }

    @Override
    public boolean getBooleanValue()
    {
        return this.value;
    }

    @Override
    public boolean getDefaultBooleanValue()
    {
        return this.defaultValue;
    }

    @Override
    public void setBooleanValue(boolean value)
    {
        boolean old = this.value;
        this.value = value;
        if (old != value)
        {
            this.onValueChanged();
        }
    }

    @Override
    public boolean isModified()
    {
        return this.value != this.defaultValue;
    }

    @Override
    public boolean isModified(String newValue)
    {
        return Boolean.parseBoolean(newValue) != this.defaultValue;
    }

    @Override
    public void resetToDefault()
    {
        this.value = this.defaultValue;
    }

    @Override
    public JsonElement getAsJsonElement()
    {
        return new JsonPrimitive(this.value);
    }

    @Override
    public void setValueFromJsonElement(JsonElement element)
    {
        if (element != null && element.isJsonPrimitive())
        {
            this.value = element.getAsBoolean();
        }
    }
}
