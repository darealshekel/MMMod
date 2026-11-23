package com.mmm.mixin;

import com.mmm.ui.MmmUi;
import com.mmm.ui.CollectionBookScreen;
import com.mmm.ui.MmmSettingsScreen;
import com.mmm.ui.WebsiteLinkScreen;

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
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void mmm$renderMmmButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client == null ? null : client.currentScreen;
        // In 1.21.11 renderWidget is final; the board widgets paint their own drawIcon.
        if (screen == null || screen instanceof CollectionBookScreen || screen instanceof MmmSettingsScreen
                || screen instanceof WebsiteLinkScreen || !screen.getClass().getName().startsWith("com.mmm."))
        {
            return;
        }

        PressableWidget button = (PressableWidget) (Object) this;
        boolean highlighted = button.isHovered() || button.isFocused();
        MmmUi.boardControl(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), button.active && highlighted);
        int textColor = button.active ? (highlighted ? com.mmm.ui.BoardEditorStyle.ROSE : com.mmm.ui.BoardEditorStyle.TEXT)
                : MmmUi.INACTIVE;
        context.drawCenteredTextWithShadow(client.textRenderer, MmmUi.truncate(client.textRenderer, button.getMessage().getString(), button.getWidth() - 6),
                button.getX() + button.getWidth() / 2,
                button.getY() + (button.getHeight() - 8) / 2,
                textColor);
        ci.cancel();
    }
}
