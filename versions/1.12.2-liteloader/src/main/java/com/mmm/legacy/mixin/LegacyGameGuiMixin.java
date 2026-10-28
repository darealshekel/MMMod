package com.mmm.legacy.mixin;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.hud.*;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.timer.MmmTimerState;
import com.mmm.timer.TimerHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class LegacyGameGuiMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;isPressed()Z"))
    private boolean mmm$toggleTab(net.minecraft.client.options.KeyBinding key) {
        return key.isPressed() || FeatureToggle.MMM_TOGGLE_TAB.getBooleanValue();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mmm$hud(float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || !FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()) return;
        LegacyDrawContext context = new LegacyDrawContext();
        MiningHudRenderer.render(context, client);
        TimerHudRenderer.render(context, client);
    }
}
