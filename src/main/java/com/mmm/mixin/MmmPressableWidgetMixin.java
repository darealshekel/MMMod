package com.mmm.mixin;

import com.mmm.ui.MmmUi;

import net.minecraft.client.MinecraftClient;
import com.mmm.compat.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClickableWidget.class)
public abstract class MmmPressableWidgetMixin
{
    @Inject(method = "renderButton", at = @At("HEAD"), cancellable = true)
    private void mmm$renderMmmButton(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client == null ? null : client.currentScreen;
        if (screen == null || !screen.getClass().getName().startsWith("com.mmm."))
        {
            return;
        }

        if (!((Object) this instanceof PressableWidget button))
        {
            return;
        }
        DrawContext context = new DrawContext(client, matrices);
        boolean highlighted = button.isHovered() || button.isFocused();
        int fill = button.active ? (highlighted ? MmmUi.accentHover() : MmmUi.INSET) : MmmUi.CARD;
        int border = button.active && highlighted ? MmmUi.accent() : MmmUi.BORDER_SOFT;
        int textColor = button.active ? (highlighted ? MmmUi.TEXT : MmmUi.MUTED) : MmmUi.INACTIVE;
        MmmUi.card(context, button.x, button.y, button.getWidth(), button.getHeight(), fill, border);
        context.drawCenteredTextWithShadow(client.textRenderer, button.getMessage(),
                button.x + button.getWidth() / 2, button.y + (button.getHeight() - 8) / 2, textColor);
        ci.cancel();
    }
}
