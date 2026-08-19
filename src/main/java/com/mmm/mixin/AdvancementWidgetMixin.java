package com.mmm.mixin;

import com.mmm.advancement.MmmAdvancementWidgetProgress;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin implements MmmAdvancementWidgetProgress
{
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private int width;
    @Shadow @Final @Mutable private List<OrderedText> description;

    @Override
    public void mmm$setProgressDescription(String descriptionText, String progressText)
    {
        Text text = Text.literal(descriptionText)
                .formatted(Formatting.GRAY)
                .append(Text.literal("\n" + progressText).formatted(Formatting.RED));
        this.description = this.client.textRenderer.wrapLines(text, Math.max(72, this.width - 10));
    }
}
