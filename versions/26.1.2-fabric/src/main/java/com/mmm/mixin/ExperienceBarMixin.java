package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.hud.GoalProgressTexture;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBarRenderer.class)
public abstract class ExperienceBarMixin
{
    @Unique private static final Identifier mmm$experienceBarBackground =
            Identifier.withDefaultNamespace("hud/experience_bar_background");

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void mmm$renderDailyGoalExperienceBar(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci)
    {
        MiningStats.GoalProgress progress = mmm$getVisibleGoalProgress();
        if (progress == null)
        {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        int x = (client.getWindow().getGuiScaledWidth() - 182) / 2;
        int y = client.getWindow().getGuiScaledHeight() - 29;
        double ratio = progress.target() <= 0L ? 0.0D : progress.current() / (double) progress.target();
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        int filledWidth = (int) (ratio * 183.0D);
        if (progress.current() > 0L && filledWidth == 0)
        {
            filledWidth = 1;
        }

        context.blitSprite(RenderPipelines.GUI_TEXTURED, mmm$experienceBarBackground, x, y, 182, 5);
        if (filledWidth > 0)
        {
            int color = UiFormat.getGoalProgressColor(progress);
            Identifier texture = GoalProgressTexture.get(color);
            if (texture != null)
            {
                context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                        Math.min(filledWidth, 182), 5, 182, 5);
            }
            else
            {
                mmm$drawColoredVanillaProgress(context, x, y, filledWidth, color);
            }
        }
        ci.cancel();
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
    @Unique
    private static void mmm$drawColoredVanillaProgress(GuiGraphicsExtractor context, int x, int y, int filledWidth, int color)
    {
        int middleEnd = x + Math.min(filledWidth, 182);
        if (middleEnd > x)
        {
            context.fill(x, y + 1, middleEnd, y + 4, color);
        }

        int edgeEnd = x + Math.min(filledWidth, 181);
        if (edgeEnd > x + 1)
        {
            context.fill(x + 1, y, edgeEnd, y + 1, color);
            context.fill(x + 1, y + 4, edgeEnd, y + 5, color);
        }
    }
}
