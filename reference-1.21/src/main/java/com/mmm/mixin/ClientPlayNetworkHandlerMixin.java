package com.mmm.mixin;

import com.mmm.tracker.BlockBreakdownTracker;
import com.mmm.scoreboard.ScoreboardState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin
{
    @Inject(method = "onStatistics", at = @At("RETURN"))
    private void mmm$captureBlockBreakdown(StatisticsS2CPacket packet, CallbackInfo ci)
    {
        BlockBreakdownTracker.captureVanillaStats(MinecraftClient.getInstance(), System.currentTimeMillis());
    }

    @Inject(method = "onScoreboardObjectiveUpdate", at = @At("HEAD"))
    private void mmm$resetScoreboardPageOnObjective(ScoreboardObjectiveUpdateS2CPacket packet, CallbackInfo ci)
    {
        ScoreboardState.resetPage();
    }

    @Inject(method = "onScoreboardDisplay", at = @At("HEAD"))
    private void mmm$resetScoreboardPageOnDisplay(ScoreboardDisplayS2CPacket packet, CallbackInfo ci)
    {
        ScoreboardState.resetPage();
    }
}
