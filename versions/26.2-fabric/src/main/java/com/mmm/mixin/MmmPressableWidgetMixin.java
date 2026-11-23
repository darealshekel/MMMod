package com.mmm.mixin;

import com.mmm.ui.BoardEditorStyle;
import com.mmm.ui.MmmUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class MmmPressableWidgetMixin
{
    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void mmm$renderMmmButton(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        Minecraft client = Minecraft.getInstance();
        Screen screen = client == null ? null : client.gui.screen();
        if (screen instanceof com.mmm.ui.MmmSettingsScreen || screen instanceof com.mmm.ui.CollectionBookScreen
                || screen instanceof com.mmm.ui.WebsiteLinkScreen) return;
        if (screen == null || !screen.getClass().getName().startsWith("com.mmm."))
        {
            return;
        }

        AbstractButton button = (AbstractButton) (Object) this;
        if (button instanceof com.mmm.ui.CollectionSlotButton || button instanceof com.mmm.ui.InventoryCollectionButton)
        {
            return;
        }
        boolean highlighted = button.isHovered() || button.isFocused();
        int fill = button.active ? (highlighted ? BoardEditorStyle.LINE : BoardEditorStyle.TILE) : MmmUi.CARD;
        int border = button.active && highlighted ? (MmmUi.accent() == MmmUi.RED ? BoardEditorStyle.ROSE : MmmUi.accent()) : BoardEditorStyle.LINE;
        int textColor = button.active ? (highlighted ? MmmUi.TEXT : MmmUi.MUTED) : MmmUi.INACTIVE;
        MmmUi.card(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), fill, border);
        context.centeredText(client.font, button.getMessage(),
                button.getX() + button.getWidth() / 2,
                button.getY() + (button.getHeight() - 8) / 2,
                textColor);
        ci.cancel();
    }
}
