package com.mmm.mixin;

import java.util.Locale;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.util.UiFormat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin
{
    @Shadow private Text footer;

    @Unique private Text mmm$originalFooter;
    @Unique private boolean mmm$goalFooterAdded;

    @Inject(method = "render", at = @At("HEAD"))
    private void mmm$addDailyGoalToPlayerList(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci)
    {
        if (!FeatureToggle.TWEAK_MINING_TRACKER.getBooleanValue()
                || !FeatureToggle.TWEAK_DAILY_GOAL.getBooleanValue())
        {
            return;
        }

        MiningStats.GoalProgress progress = MiningStats.getDailyGoalProgress();
        if (!progress.enabled())
        {
            return;
        }

        String value = String.format(Locale.US, "%,d/%,d", Math.max(0L, progress.current()), Math.max(0L, progress.target()));
        int goalColor = UiFormat.getGoalProgressColor(progress) & 0x00FFFFFF;
        MutableText goalLine = Text.literal("Daily Goal: ").formatted(Formatting.GRAY)
                .append(Text.literal(value).formatted(Formatting.WHITE))
                .append(Text.literal("  " + UiFormat.formatGoalPercent(progress))
                        .styled(style -> style.withColor(goalColor)));

        this.mmm$originalFooter = this.footer;
        this.footer = this.mmm$originalFooter == null
                ? goalLine
                : Text.empty().append(this.mmm$originalFooter).append(Text.literal("\n")).append(goalLine);
        this.mmm$goalFooterAdded = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mmm$restorePlayerListHeader(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci)
    {
        if (this.mmm$goalFooterAdded)
        {
            this.footer = this.mmm$originalFooter;
            this.mmm$originalFooter = null;
            this.mmm$goalFooterAdded = false;
        }
    }
}
