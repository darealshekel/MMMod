package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.scoreboard.ScoreboardHudRenderer;
import com.mmm.scoreboard.ScoreboardMoveScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class ScoreboardInGameHudMixin
{
    @Shadow @Final private Minecraft minecraft;

    @Inject(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mmm$renderConfiguredScoreboard(GuiGraphicsExtractor context, Objective objective, CallbackInfo ci)
    {
        ci.cancel();
        if (!Configs.Generic.SCOREBOARD_VISIBLE.getBooleanValue()
                || this.minecraft.screen instanceof ScoreboardMoveScreen)
        {
            return;
        }
        ScoreboardHudRenderer.render(context, this.minecraft, objective);
    }
}
