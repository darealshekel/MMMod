package com.mmm.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mmm.config.value.IConfigBase;
import com.mmm.hotkey.MmmHotkey;
import java.util.List;

public final class MmmConfigIO
{
    private MmmConfigIO()
    {
    }

    public static void readConfigBase(JsonObject root, String sectionName, List<? extends IConfigBase> configs)
    {
        if (root == null || !root.has(sectionName) || !root.get(sectionName).isJsonObject())
        {
            return;
        }

        JsonObject section = root.getAsJsonObject(sectionName);
        for (IConfigBase config : configs)
        {
            JsonElement value = section.get(config.getName());
            if (value == null)
            {
                continue;
            }

            try
            {
                config.setValueFromJsonElement(value);
            }
            catch (RuntimeException ignored)
            {
                // Keep the previous value when one legacy field is malformed.
            }
        }
    }

    public static void writeConfigBase(JsonObject root, String sectionName, List<? extends IConfigBase> configs)
    {
        JsonObject section = new JsonObject();
        for (IConfigBase config : configs)
        {
            section.add(config.getName(), config.getAsJsonElement());
        }
        root.add(sectionName, section);
    }

    public static void readHotkeys(JsonObject root, String sectionName, List<MmmHotkey> hotkeys)
    {
        if (root == null || !root.has(sectionName) || !root.get(sectionName).isJsonObject())
        {
            return;
        }

        JsonObject section = root.getAsJsonObject(sectionName);
        for (MmmHotkey hotkey : hotkeys)
        {
            JsonElement value = section.get(hotkey.getName());
            if (value != null && value.isJsonPrimitive())
            {
                hotkey.setStorageString(value.getAsString());
            }
        }
    }

    public static void writeHotkeys(JsonObject root, String sectionName, List<MmmHotkey> hotkeys)
    {
        JsonObject section = new JsonObject();
        for (MmmHotkey hotkey : hotkeys)
        {
            section.addProperty(hotkey.getName(), hotkey.getStorageString());
        }
        root.add(sectionName, section);
    }

    public static void readHotkeyToggleOptions(
            JsonObject root,
            String hotkeysSectionName,
            String togglesSectionName,
            List<FeatureToggle> toggles)
    {
        JsonObject values = root != null && root.has(togglesSectionName) && root.get(togglesSectionName).isJsonObject()
                ? root.getAsJsonObject(togglesSectionName) : null;
        JsonObject hotkeys = root != null && root.has(hotkeysSectionName) && root.get(hotkeysSectionName).isJsonObject()
                ? root.getAsJsonObject(hotkeysSectionName) : null;

        for (FeatureToggle toggle : toggles)
        {
            if (values != null)
            {
                JsonElement value = values.get(toggle.getName());
                if (value != null)
                {
                    try
                    {
                        toggle.setValueFromJsonElement(value);
                    }
                    catch (RuntimeException ignored)
                    {
                    }
                }
            }
            if (hotkeys != null)
            {
                JsonElement value = hotkeys.get(toggle.getName());
                if (value != null && value.isJsonPrimitive())
                {
                    toggle.getHotkey().setStorageString(value.getAsString());
                }
            }
        }
    }

    public static void writeHotkeyToggleOptions(
            JsonObject root,
            String hotkeysSectionName,
            String togglesSectionName,
            List<FeatureToggle> toggles)
    {
        JsonObject values = new JsonObject();
        JsonObject hotkeys = new JsonObject();
        for (FeatureToggle toggle : toggles)
        {
            values.add(toggle.getName(), toggle.getAsJsonElement());
            hotkeys.addProperty(toggle.getName(), toggle.getHotkey().getStorageString());
        }
        root.add(togglesSectionName, values);
        root.add(hotkeysSectionName, hotkeys);
    }
}