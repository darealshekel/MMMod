package com.mmm.mixin;

import java.util.Locale;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.tags.TierTagManager;
import com.mmm.util.UiFormat;

import com.mmm.compat.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
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
        MutableText decorated = TierTagManager.decorateName(entry.getProfile().getName(), cir.getReturnValue());
        if (decorated != null)
        {
            cir.setReturnValue(decorated);
        }
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/PlayerListHud;fill(Lnet/minecraft/client/util/math/MatrixStack;IIIII)V"))
    private void mmm$renderTransparentTabBackground(MatrixStack matrices, int x1, int y1, int x2, int y2, int color)
    {
        if (!Configs.Generic.TRANSPARENT_TAB.getBooleanValue())
        {
            DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
        }
    }
    @Inject(method = "render", at = @At("HEAD"))
    private void mmm$addDailyGoalToPlayerList(MatrixStack matrices, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci)
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
        MutableText goalLine = new net.minecraft.text.LiteralText("Daily Goal: ").formatted(Formatting.GRAY)
                .append(new net.minecraft.text.LiteralText(value).formatted(Formatting.WHITE))
                .append(new net.minecraft.text.LiteralText("  " + UiFormat.formatGoalPercent(progress))
                        .styled(style -> style.withColor(goalColor)));

        this.mmm$originalFooter = this.footer;
        this.footer = this.mmm$originalFooter == null
                ? goalLine
                : new net.minecraft.text.LiteralText("").append(this.mmm$originalFooter).append(new net.minecraft.text.LiteralText("\n")).append(goalLine);
        this.mmm$goalFooterAdded = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mmm$restorePlayerListHeader(MatrixStack matrices, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci)
    {
        if (this.mmm$goalFooterAdded)
        {
            this.footer = this.mmm$originalFooter;
            this.mmm$originalFooter = null;
            this.mmm$goalFooterAdded = false;
        }
    }
}
