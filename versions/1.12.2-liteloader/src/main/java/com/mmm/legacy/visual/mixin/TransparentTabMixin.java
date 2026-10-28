package com.mmm.legacy.visual.mixin;

import com.mmm.config.Configs;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.overlay.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(PlayerTabOverlay.class)
public abstract class TransparentTabMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/overlay/PlayerTabOverlay;fill(IIIII)V"))
    private void mmm$tabBackground(int left, int top, int right, int bottom, int color) {
        if (!Configs.Generic.TRANSPARENT_TAB.getBooleanValue()) GuiElement.fill(left, top, right, bottom, color);
    }
}

