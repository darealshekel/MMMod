package com.mmm.mixin;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.social.ActiveDiggerManager;
import com.mmm.tracker.MiningStats;
import com.mmm.tags.TierTagManager;
import com.mmm.util.UiFormat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.MinecraftClient;
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
    @Unique private boolean mmm$footerDecorated;

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void mmm$applyTierNameTag(PlayerListEntry entry, CallbackInfoReturnable<Text> cir)
    {
        Text original = cir.getReturnValue();
        MutableText tierDecorated = TierTagManager.decorateName(entry.getProfile().getName(), original);
        Text displayed = tierDecorated == null ? original : tierDecorated;
        MutableText activeDecorated = ActiveDiggerManager.decorateName(entry.getProfile().getName(), displayed);
        if (activeDecorated != null)
        {
            cir.setReturnValue(activeDecorated);
        }
        else if (tierDecorated != null)
        {
            cir.setReturnValue(tierDecorated);
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
        MutableText extraFooter = Text.empty();
        boolean hasExtraFooter = false;
        List<String> localPlayerNames = new ArrayList<>();
        if (MinecraftClient.getInstance().getNetworkHandler() != null)
        {
            for (PlayerListEntry entry : MinecraftClient.getInstance().getNetworkHandler().getPlayerList())
            {
                localPlayerNames.add(entry.getProfile().getName());
            }
        }
        List<String> remoteDiggers = ActiveDiggerManager.visibleRemoteDiggers(localPlayerNames);
        if (!remoteDiggers.isEmpty())
        {
            extraFooter.append(Text.literal("Active Diggers").formatted(Formatting.GRAY));
            for (int index = 0; index < remoteDiggers.size(); index++)
            {
                if (index % 4 == 0)
                {
                    extraFooter.append(Text.literal("\n"));
                }
                else
                {
                    extraFooter.append(Text.literal("   "));
                }
                extraFooter.append(Text.literal("\u26CF").styled(style -> style.withColor(0xE00000)))
                        .append(Text.literal(" " + remoteDiggers.get(index)).formatted(Formatting.WHITE));
            }
            hasExtraFooter = true;
        }

        if (FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()
                && FeatureToggle.MMM_DAILY_GOAL.getBooleanValue())
        {
            MiningStats.GoalProgress progress = MiningStats.getDailyGoalProgress();
            if (progress.enabled())
            {
                String value = String.format(Locale.US, "%,d/%,d", Math.max(0L, progress.current()), Math.max(0L, progress.target()));
                int goalColor = UiFormat.getGoalProgressColor(progress) & 0x00FFFFFF;
                MutableText goalLine = Text.literal("Daily Goal: ").formatted(Formatting.GRAY)
                        .append(Text.literal(value).formatted(Formatting.WHITE))
                        .append(Text.literal("  " + UiFormat.formatGoalPercent(progress))
                                .styled(style -> style.withColor(goalColor)));
                if (hasExtraFooter)
                {
                    extraFooter.append(Text.literal("\n"));
                }
                extraFooter.append(goalLine);
                hasExtraFooter = true;
            }
        }

        if (!hasExtraFooter)
        {
            return;
        }

        this.mmm$originalFooter = this.footer;
        this.footer = this.mmm$originalFooter == null
                ? extraFooter
                : Text.empty().append(this.mmm$originalFooter).append(Text.literal("\n")).append(extraFooter);
        this.mmm$footerDecorated = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mmm$restorePlayerListHeader(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci)
    {
        if (this.mmm$footerDecorated)
        {
            this.footer = this.mmm$originalFooter;
            this.mmm$originalFooter = null;
            this.mmm$footerDecorated = false;
        }
    }
}
