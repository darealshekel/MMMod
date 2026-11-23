package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin
{
    @Shadow @Final private MinecraftClient client;

    @Redirect(
            method = "renderPlayerList",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z")
    )
    private boolean mmm$keepPlayerListOpen(KeyBinding keyBinding)
    {
        return keyBinding.isPressed() || FeatureToggle.MMM_TOGGLE_TAB.getBooleanValue();
    }

    @Redirect(
            method = "renderMainHud",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasExperienceBar()Z")
    )
    private boolean mmm$renderDailyGoalPercent(ClientPlayerInteractionManager interactionManager,
                                              DrawContext context, RenderTickCounter tickCounter)
    {
        boolean hasExperienceBar = interactionManager.hasExperienceBar();
        MiningStats.GoalProgress progress = mmm$getVisibleGoalProgress();
        if (!hasExperienceBar || progress == null || !Configs.Generic.SHOW_GOAL_PERCENT.getBooleanValue()
                || this.client.player == null || this.client.player.getJumpingMount() != null)
        {
            return hasExperienceBar;
        }

        String percent = UiFormat.formatGoalPercent(progress);
        int x = (context.getScaledWindowWidth() - this.client.textRenderer.getWidth(percent)) / 2;
        int y = context.getScaledWindowHeight() - 35;
        int goalColor = UiFormat.getGoalProgressColor(progress);

        context.drawText(this.client.textRenderer, percent, x + 1, y, 0xFF000000, false);
        context.drawText(this.client.textRenderer, percent, x - 1, y, 0xFF000000, false);
        context.drawText(this.client.textRenderer, percent, x, y + 1, 0xFF000000, false);
        context.drawText(this.client.textRenderer, percent, x, y - 1, 0xFF000000, false);
        context.drawText(this.client.textRenderer, percent, x, y, goalColor, false);

        // This label-only gate precedes vanilla's experienceLevel > 0 check in 1.21.11.
        return false;
    }

    @Unique
    private MiningStats.GoalProgress mmm$getVisibleGoalProgress()
    {
        if (!FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()
                || !FeatureToggle.MMM_DAILY_GOAL.getBooleanValue()
                || !FeatureToggle.MMM_HUD_GOAL_PROGRESS.getBooleanValue()
                || this.client.options == null
                || (!Configs.Generic.ALWAYS_OVERRIDE_XP_BAR.getBooleanValue()
                    && !this.client.options.playerListKey.isPressed()))
        {
            return null;
        }
        MiningStats.GoalProgress progress = MiningStats.getDailyGoalProgress();
        return progress.enabled() ? progress : null;
    }
}
