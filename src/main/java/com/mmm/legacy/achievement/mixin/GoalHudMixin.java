package com.mmm.legacy.achievement.mixin;

import com.mmm.legacy.achievement.LegacyGoalHud;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class GoalHudMixin {
    @Inject(method = "renderXpBar", at = @At("HEAD"), cancellable = true)
    private void mmm$goalBar(Window window, int x, CallbackInfo ci) {
        if (LegacyGoalHud.render(window, x)) ci.cancel();
    }
}
