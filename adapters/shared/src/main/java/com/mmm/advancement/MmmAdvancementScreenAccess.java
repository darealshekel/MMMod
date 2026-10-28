package com.mmm.advancement;

import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;

public interface MmmAdvancementScreenAccess {
    Map<AdvancementHolder, AdvancementTab> mmm$tabs();
}
