package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class InGameHudMixin
{
    @Shadow @Final private Minecraft minecraft;

    @Redirect(
            method = "extractTabList",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z")
    )
    private boolean mmm$keepPlayerListOpen(KeyMapping keyBinding)
    {
        return keyBinding.isDown() || FeatureToggle.MMM_TOGGLE_TAB.getBooleanValue();
    }

    @Redirect(
            method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V")
    )
    private void mmm$renderDailyGoalPercent(GuiGraphicsExtractor context, Font textRenderer, int experienceLevel)
    {
        MiningStats.GoalProgress progress = mmm$getVisibleGoalProgress();
        if (progress == null || !Configs.Generic.SHOW_GOAL_PERCENT.getBooleanValue()
                || this.minecraft.player == null || this.minecraft.gameMode == null
                || this.minecraft.player.jumpableVehicle() != null || !this.minecraft.gameMode.hasExperience())
        {
            ContextualBarRenderer.extractExperienceLevel(context, textRenderer, experienceLevel);
            return;
        }

        String percent = UiFormat.formatGoalPercent(progress);
        int x = (context.guiWidth() - textRenderer.width(percent)) / 2;
        int y = context.guiHeight() - 35;
        int goalColor = UiFormat.getGoalProgressColor(progress);

        context.text(textRenderer, percent, x + 1, y, 0x000000, false);
        context.text(textRenderer, percent, x - 1, y, 0x000000, false);
        context.text(textRenderer, percent, x, y + 1, 0x000000, false);
        context.text(textRenderer, percent, x, y - 1, 0x000000, false);
        context.text(textRenderer, Component.literal(percent), x, y, goalColor, false);
    }

    @Unique
    private static MiningStats.GoalProgress mmm$getVisibleGoalProgress()
    {
        Minecraft client = Minecraft.getInstance();
        if (!FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()
                || !FeatureToggle.MMM_DAILY_GOAL.getBooleanValue()
                || !FeatureToggle.MMM_HUD_GOAL_PROGRESS.getBooleanValue()
                || client == null
                || client.options == null
                || (!Configs.Generic.ALWAYS_OVERRIDE_XP_BAR.getBooleanValue() && !client.options.keyPlayerList.isDown()))
        {
            return null;
        }
        MiningStats.GoalProgress progress = MiningStats.getDailyGoalProgress();
        return progress.enabled() ? progress : null;
    }
}
