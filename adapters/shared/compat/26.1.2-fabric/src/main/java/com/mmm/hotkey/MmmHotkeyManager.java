package com.mmm.hotkey;

import com.mmm.config.FeatureToggle;
import com.mmm.config.Hotkeys;
import net.minecraft.client.Minecraft;

public final class MmmHotkeyManager
{
    private MmmHotkeyManager()
    {
    }

    public static void tick(Minecraft client)
    {
        for (MmmHotkey hotkey : Hotkeys.HOTKEY_LIST)
        {
            hotkey.tick(client);
        }
        for (FeatureToggle toggle : FeatureToggle.values())
        {
            toggle.getHotkey().tick(client);
        }
    }

    public static void onKeyEvent(Minecraft client, int keyCode, int scanCode, int action)
    {
        for (MmmHotkey hotkey : Hotkeys.HOTKEY_LIST)
        {
            hotkey.onKeyEvent(client, keyCode, scanCode, action);
        }
        for (FeatureToggle toggle : FeatureToggle.values())
        {
            toggle.getHotkey().onKeyEvent(client, keyCode, scanCode, action);
        }
    }
}