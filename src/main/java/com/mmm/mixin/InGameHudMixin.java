package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.hud.GoalProgressTexture;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin
{
    @Shadow @Final private MinecraftClient client;
    @Unique private static final Identifier mmm$iconsTexture = new Identifier("textures/gui/icons.png");

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z")
    )
    private boolean mmm$keepPlayerListOpen(KeyBinding keyBinding)
    {
        return keyBinding.isPressed() || FeatureToggle.MMM_TOGGLE_TAB.getBooleanValue();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void mmm$renderDailyGoalExperienceBar(DrawContext context, int x, CallbackInfo ci)
    {
        MiningStats.GoalProgress progress = mmm$getVisibleGoalProgress();
        if (progress == null)
        {
            return;
        }

        int y = context.getScaledWindowHeight() - 29;
        double ratio = progress.target() <= 0L ? 0.0D : progress.current() / (double) progress.target();
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        // Match vanilla's 183-step crop so the goal bar has the exact XP-bar silhouette.
        int filledWidth = (int) (ratio * 183.0D);
        if (progress.current() > 0L && filledWidth == 0)
        {
            filledWidth = 1;
        }

        RenderSystem.enableBlend();
        context.drawTexture(mmm$iconsTexture, x, y, 0, 64, 182, 5);
        if (filledWidth > 0)
        {
            int color = UiFormat.getGoalProgressColor(progress);
            Identifier texture = GoalProgressTexture.get(color);
            if (texture != null)
            {
                context.drawTexture(texture, x, y, 0.0F, 0.0F, Math.min(filledWidth, 182), 5, 182, 5);
            }
            else
            {
                mmm$drawColoredVanillaProgress(context, x, y, filledWidth, color);
            }
        }
        RenderSystem.disableBlend();

        if (Configs.Generic.SHOW_GOAL_PERCENT.getBooleanValue()
                && this.client.player != null && this.client.interactionManager != null
                && this.client.player.getJumpingMount() == null && this.client.interactionManager.hasExperienceBar())
        {
            String percent = UiFormat.formatGoalPercent(progress);
            int textX = (context.getScaledWindowWidth() - this.client.textRenderer.getWidth(percent)) / 2;
            int textY = context.getScaledWindowHeight() - 35;
            int goalColor = UiFormat.getGoalProgressColor(progress);

            context.drawText(this.client.textRenderer, percent, textX + 1, textY, 0x000000, false);
            context.drawText(this.client.textRenderer, percent, textX - 1, textY, 0x000000, false);
            context.drawText(this.client.textRenderer, percent, textX, textY + 1, 0x000000, false);
            context.drawText(this.client.textRenderer, percent, textX, textY - 1, 0x000000, false);
            context.drawText(this.client.textRenderer, Text.literal(percent), textX, textY, goalColor, false);
        }
        ci.cancel();
    }

    @Unique
    private static void mmm$drawColoredVanillaProgress(DrawContext context, int x, int y, int filledWidth, int color)
    {
        int middleEnd = x + Math.min(filledWidth, 182);
        if (middleEnd > x)
        {
            context.fill(x, y + 1, middleEnd, y + 4, color);
        }

        // The vanilla sprite's first and last rows are inset by one pixel at both ends.
        int edgeEnd = x + Math.min(filledWidth, 181);
        if (edgeEnd > x + 1)
        {
            context.fill(x + 1, y, edgeEnd, y + 1, color);
            context.fill(x + 1, y + 4, edgeEnd, y + 5, color);
        }
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
