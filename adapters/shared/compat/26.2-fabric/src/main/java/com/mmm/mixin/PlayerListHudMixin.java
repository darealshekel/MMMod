package com.mmm.mixin;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import java.util.ArrayList;
import java.util.List;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.social.ActiveDiggerManager;
import com.mmm.tracker.MiningStats;
import com.mmm.tags.TierTagManager;
import com.mmm.util.UiFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin
{
    @Shadow private Component footer;

    @Unique private Component mmm$originalFooter;
    @Unique private boolean mmm$footerDecorated;

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void mmm$applyTierNameTag(PlayerInfo entry, CallbackInfoReturnable<Component> cir)
    {
        Component original = cir.getReturnValue();
        MutableComponent tierDecorated = TierTagManager.decorateName(entry.getProfile().name(), original);
        Component displayed = tierDecorated == null ? original : tierDecorated;
        MutableComponent activeDecorated = ActiveDiggerManager.decorateName(entry.getProfile().name(), displayed);
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
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/ReadOnlyScoreInfo;formatValue(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent mmm$formatTabListScore(ReadOnlyScoreInfo score, NumberFormat numberFormat)
    {
        if (score == null)
        {
            return Component.empty();
        }

        MutableComponent vanilla = score.formatValue(numberFormat);
        if (!Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.getBooleanValue()
                || !vanilla.getString().equals(Integer.toString(score.value())))
        {
            return vanilla;
        }
        return Component.literal(String.format(Locale.US, "%,d", score.value())).setStyle(vanilla.getStyle());
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void mmm$renderTransparentTabBackground(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color)
    {
        if (!Configs.Generic.TRANSPARENT_TAB.getBooleanValue())
        {
            context.fill(x1, y1, x2, y2, color);
        }
    }
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void mmm$addDailyGoalToPlayerList(GuiGraphicsExtractor context, int scaledWindowWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci)
    {
        MutableComponent extraFooter = Component.empty();
        boolean hasExtraFooter = false;
        List<String> localPlayerNames = new ArrayList<>();
        if (Minecraft.getInstance().getConnection() != null)
        {
            for (PlayerInfo entry : Minecraft.getInstance().getConnection().getOnlinePlayers())
            {
                localPlayerNames.add(entry.getProfile().name());
            }
        }
        List<String> remoteDiggers = ActiveDiggerManager.visibleRemoteDiggers(localPlayerNames);
        if (!remoteDiggers.isEmpty())
        {
            extraFooter.append(Component.literal("Active Diggers").withStyle(ChatFormatting.GRAY));
            for (int index = 0; index < remoteDiggers.size(); index++)
            {
                if (index % 4 == 0)
                {
                    extraFooter.append(Component.literal("\n"));
                }
                else
                {
                    extraFooter.append(Component.literal("   "));
                }
                extraFooter.append(Component.literal("\u26CF").withStyle(style -> style.withColor(0xE00000)))
                        .append(Component.literal(" " + remoteDiggers.get(index)).withStyle(ChatFormatting.WHITE));
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
                MutableComponent goalLine = Component.literal("Daily Goal: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(value).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  " + UiFormat.formatGoalPercent(progress))
                                .withStyle(style -> style.withColor(goalColor)));
                if (hasExtraFooter)
                {
                    extraFooter.append(Component.literal("\n"));
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
                : Component.empty().append(this.mmm$originalFooter).append(Component.literal("\n")).append(extraFooter);
        this.mmm$footerDecorated = true;
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void mmm$restorePlayerListHeader(GuiGraphicsExtractor context, int scaledWindowWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci)
    {
        if (this.mmm$footerDecorated)
        {
            this.footer = this.mmm$originalFooter;
            this.mmm$originalFooter = null;
            this.mmm$footerDecorated = false;
        }
    }
}
