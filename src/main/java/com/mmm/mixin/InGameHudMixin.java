package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.bar.Bar;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

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
                    target = "Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V")
    )
    private void mmm$renderDailyGoalPercent(DrawContext context, TextRenderer textRenderer, int experienceLevel)
    {
        MiningStats.GoalProgress progress = mmm$getVisibleGoalProgress();
        if (progress == null || !Configs.Generic.SHOW_GOAL_PERCENT.getBooleanValue()
                || this.client.player == null || this.client.interactionManager == null
                || this.client.player.getJumpingMount() != null || !this.client.interactionManager.hasExperienceBar())
        {
            Bar.drawExperienceLevel(context, textRenderer, experienceLevel);
            return;
        }

        String percent = UiFormat.formatGoalPercent(progress);
        int x = (context.getScaledWindowWidth() - textRenderer.getWidth(percent)) / 2;
        int y = context.getScaledWindowHeight() - 35;
        int goalColor = UiFormat.getGoalProgressColor(progress);

        context.drawText(textRenderer, percent, x + 1, y, 0x000000, false);
        context.drawText(textRenderer, percent, x - 1, y, 0x000000, false);
        context.drawText(textRenderer, percent, x, y + 1, 0x000000, false);
        context.drawText(textRenderer, percent, x, y - 1, 0x000000, false);
        context.drawText(textRenderer, Text.literal(percent), x, y, goalColor, false);
    }

    @Unique
    private static MiningStats.GoalProgress mmm$getVisibleGoalProgress()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()
                || !FeatureToggle.MMM_DAILY_GOAL.getBooleanValue()
                || !FeatureToggle.MMM_HUD_GOAL_PROGRESS.getBooleanValue()
                || client == null
                || client.options == null
                || (!Configs.Generic.ALWAYS_OVERRIDE_XP_BAR.getBooleanValue() && !client.options.playerListKey.isPressed()))
        {
            return null;
        }
        MiningStats.GoalProgress progress = MiningStats.getDailyGoalProgress();
        return progress.enabled() ? progress : null;
    }
}
