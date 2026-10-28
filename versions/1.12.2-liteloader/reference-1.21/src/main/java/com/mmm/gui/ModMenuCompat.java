package com.mmm.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.mmm.ui.MmmSettingsScreen;

public class ModMenuCompat implements ModMenuApi
{
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory()
    {
        return MmmSettingsScreen::new;
    }
}
