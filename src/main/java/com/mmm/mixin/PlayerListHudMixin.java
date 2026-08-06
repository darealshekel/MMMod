package com.mmm.mixin;

import java.util.Locale;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.tags.TierTagManager;
import com.mmm.util.UiFormat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin
{
    @Shadow private Text footer;

    @Unique private Text mmm$originalFooter;
    @Unique private boolean mmm$goalFooterAdded;

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void mmm$applyTierNameTag(PlayerListEntry entry, CallbackInfoReturnable<Text> cir)
    {
        MutableText decorated = TierTagManager.decorateName(entry.getProfile().name(), cir.getReturnValue());
        if (decorated != null)
        {
            cir.setReturnValue(decorated);
        }
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/scoreboard/ReadableScoreboardScore;getFormattedScore(Lnet/minecraft/scoreboard/ReadableScoreboardScore;Lnet/minecraft/scoreboard/number/NumberFormat;)Lnet/minecraft/text/MutableText;"))
    private MutableText mmm$formatTabListScore(ReadableScoreboardScore score, NumberFormat numberFormat)
    {
        if (score == null)
        {
            return Text.empty();
        }

        MutableText vanilla = ReadableScoreboardScore.getFormattedScore(score, numberFormat);
        if (!Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.getBooleanValue()
                || !vanilla.getString().equals(Integer.toString(score.getScore())))
        {
            return vanilla;
        }
        return Text.literal(String.format(Locale.US, "%,d", score.getScore())).setStyle(vanilla.getStyle());
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void mmm$renderTransparentTabBackground(DrawContext context, int x1, int y1, int x2, int y2, int color)
    {
        if (!Configs.Generic.TRANSPARENT_TAB.getBooleanValue())
        {
            context.fill(x1, y1, x2, y2, color);
        }
    }
    @Inject(method = "render", at = @At("HEAD"))
    private void mmm$addDailyGoalToPlayerList(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci)
    {
        if (!FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()
                || !FeatureToggle.MMM_DAILY_GOAL.getBooleanValue())
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
