package com.mmm.legacy.mixin;

import com.mmm.config.FeatureToggle;
import net.minecraft.client.gui.GameGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameGui.class)
public abstract class LegacyGameGuiMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;isPressed()Z"))
    private boolean mmm$toggleTab(net.minecraft.client.options.KeyBinding key) {
        return key.isPressed() || FeatureToggle.MMM_TOGGLE_TAB.getBooleanValue();
    }
}
