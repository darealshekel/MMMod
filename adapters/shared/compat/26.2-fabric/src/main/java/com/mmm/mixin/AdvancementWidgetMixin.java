package com.mmm.mixin;

import com.mmm.advancement.MmmAdvancementWidgetProgress;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin implements MmmAdvancementWidgetProgress
{
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private int width;
    @Shadow @Final @Mutable private List<FormattedCharSequence> description;

    @Override
    public void mmm$setProgressDescription(String descriptionText, String progressText)
    {
        Component text = Component.literal(descriptionText)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("\n" + progressText).withStyle(ChatFormatting.RED));
        this.description = this.minecraft.font.split(text, Math.max(72, this.width - 10));
    }
}
