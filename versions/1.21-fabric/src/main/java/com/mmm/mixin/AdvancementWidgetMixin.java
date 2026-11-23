package com.mmm.mixin;

import com.mmm.advancement.MmmAdvancementWidgetProgress;
import com.mmm.advancement.AchievementBadges;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.advancement.AdvancementWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin implements MmmAdvancementWidgetProgress
{
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private int width;
    @Shadow @Final private PlacedAdvancement advancement;
    @Shadow @Final @Mutable private List<OrderedText> description;
    @Unique private AchievementBadges.Badge mmm$badge;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mmm$selectBadge(CallbackInfo ci)
    {
        this.mmm$badge = AchievementBadges.forAdvancement(this.advancement.getAdvancementEntry().id());
    }

    @WrapOperation(method = {"renderWidgets", "drawTooltip"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawItemWithoutEntity(Lnet/minecraft/item/ItemStack;II)V"))
    private void mmm$drawBadge(DrawContext context, ItemStack stack, int x, int y, Operation<Void> original)
    {
        if (this.mmm$badge == null) original.call(context, stack, x, y);
        else AchievementBadges.draw(context, this.mmm$badge, x - 4, y - 4, 24);
    }

    @Override
    public void mmm$setProgressDescription(String descriptionText, String progressText)
    {
        Text text = Text.literal(descriptionText)
                .formatted(Formatting.GRAY)
                .append(Text.literal("\n" + progressText).formatted(Formatting.RED));
        this.description = this.client.textRenderer.wrapLines(text, Math.max(72, this.width - 10));
    }
}
