package com.mmm.mixin;

import com.mmm.ui.MmmUi;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PressableWidget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PressableWidget.class)
public abstract class MmmPressableWidgetMixin
{
    @Inject(method = "renderButton", at = @At("HEAD"), cancellable = true)
    private void mmm$renderMmmButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client == null ? null : client.currentScreen;
        if (screen == null || !screen.getClass().getName().startsWith("com.mmm."))
        {
            return;
        }

        PressableWidget button = (PressableWidget) (Object) this;
        boolean highlighted = button.isHovered() || button.isFocused();
        int fill = button.active ? (highlighted ? MmmUi.accentHover() : MmmUi.INSET) : MmmUi.CARD;
        int border = button.active && highlighted ? MmmUi.accent() : MmmUi.BORDER_SOFT;
        int textColor = button.active ? (highlighted ? MmmUi.TEXT : MmmUi.MUTED) : MmmUi.INACTIVE;
        MmmUi.card(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), fill, border);
        button.drawMessage(context, client.textRenderer, textColor);
        ci.cancel();
    }
}
