package com.mmm.legacy.achievement.mixin;

import com.mmm.advancement.MmmAdvancementWidgetProgress;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.menu.advancements.AdvancementWidget;
import net.minecraft.text.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin implements MmmAdvancementWidgetProgress {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private int width;
    @Shadow @Final @Mutable private List<String> description;
    @Unique private String mmm$lastDescription;

    @Override public void mmm$setProgressDescription(String description, String progress) {
        String text = Formatting.GRAY + description + "\n" + Formatting.RED + progress;
        if (!text.equals(mmm$lastDescription)) {
            this.description = minecraft.textRenderer.split(text, Math.max(72, width - 10));
            mmm$lastDescription = text;
        }
    }
}
