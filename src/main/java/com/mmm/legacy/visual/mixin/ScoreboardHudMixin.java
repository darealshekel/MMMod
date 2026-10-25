package com.mmm.legacy.visual.mixin;

import com.mmm.config.Configs;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.scoreboard.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class ScoreboardHudMixin {
    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void mmm$sidebar(ScoreboardObjective objective, Window window, CallbackInfo ci) {
        ci.cancel();
        Minecraft client = Minecraft.getInstance();
        if (Configs.Generic.SCOREBOARD_VISIBLE.getBooleanValue() && ScoreboardService.activeSidebar() == null
                && !(client.screen instanceof ScoreboardMoveScreen))
            ScoreboardHudRenderer.render(new LegacyDrawContext(), client, objective);
    }
    @Inject(method = "render", at = @At("RETURN"))
    private void mmm$replacementSidebar(float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.world != null && !client.options.hideGui && Configs.Generic.SCOREBOARD_VISIBLE.getBooleanValue()
                && ScoreboardService.activeSidebar() != null && !(client.screen instanceof ScoreboardMoveScreen))
            ScoreboardHudRenderer.render(new LegacyDrawContext(), client, null);
    }
}

