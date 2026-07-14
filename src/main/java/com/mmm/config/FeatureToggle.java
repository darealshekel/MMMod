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
    TWEAK_MINING_TRACKER("tweakMiningTracker", true, "", "Turns all MMM mining tracking on or off."),
    TWEAK_DAILY_GOAL("tweakDailyGoal", true, "", "Tracks your daily mining goal."),
    TWEAK_BLOCK_ESP("tweakBlockEsp", false, "", "Highlights the block under your crosshair."),
    TWEAK_FLAT_DIGGER("tweakFlatDigger", false, "", "Stops you from digging below your feet."),
    TWEAK_PERIMETER_WALL_DIG_HELPER("tweakPerimeterWallDigHelper", false, "", "Protects the configured perimeter floor blocks."),
    TWEAK_NOTIFICATIONS("tweakGoalNotifications", true, "", "Shows a message when you reach a goal milestone."),
    TWEAK_SOUND_ALERTS("tweakGoalSoundAlerts", true, "", "Plays a sound at your chosen milestone."),
    TWEAK_SUMMARY_ON_EXIT("tweakSummaryOnExit", true, "", "Opens your session summary when you leave."),
    TWEAK_CARRY_GOAL_PROGRESS("tweakCarryGoalProgress", true, "", "Keeps today's progress between sessions."),
    TWEAK_HUD("tweakMiningHud", true, "", "Shows the main mining HUD."),
    TWEAK_HUD_PROJECT("tweakMiningHudProject", true, "", "Shows your active project."),
    TWEAK_HUD_TOTAL_MINED("tweakMiningHudTotalMined", true, "", "Enables the mining total lines."),
    TWEAK_HUD_GOAL_PROGRESS("tweakMiningHudGoalProgress", true, "", "Replaces the XP bar with daily-goal progress while Tab is held."),
    TWEAK_HUD_BLOCKS_PER_HOUR("tweakMiningHudBlocksPerHour", true, "", "Shows Blocks/hr and Blocks/sec."),
    TWEAK_HUD_ETA("tweakMiningHudEta", true, "", "Shows the estimated time to your goal."),
    TWEAK_HUD_BOUNDING_BOX("tweakMiningHudBoundingBox", false, "", "Adds one background behind the main HUD."),
    TWEAK_HUD_SPEED_GRAPH("tweakMiningHudSpeedGraph", true, "", "Shows your live mining-speed graph."),
    TWEAK_TOGGLE_TAB("tweakToggleTab", false, "", "Keeps the player list open without holding Tab.");

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
        return StringUtils.splitCamelCase(this.name.substring(5));
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
