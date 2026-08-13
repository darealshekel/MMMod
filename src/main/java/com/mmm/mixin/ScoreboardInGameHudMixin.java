package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.scoreboard.ScoreboardHudRenderer;
import com.mmm.scoreboard.ScoreboardMoveScreen;

import net.minecraft.client.MinecraftClient;
import com.mmm.compat.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ScoreboardInGameHudMixin
{
    @Shadow @Final private MinecraftClient client;

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mmm$renderConfiguredScoreboard(MatrixStack matrices, ScoreboardObjective objective, CallbackInfo ci)
    {
        ci.cancel();
        if (!Configs.Generic.SCOREBOARD_VISIBLE.getBooleanValue()
                || this.client.currentScreen instanceof ScoreboardMoveScreen)
        {
            return;
        }
        ScoreboardHudRenderer.render(new DrawContext(this.client, matrices), this.client, objective);
    }
}
