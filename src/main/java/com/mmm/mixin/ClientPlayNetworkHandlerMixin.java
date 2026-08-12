package com.mmm.mixin;

import com.mmm.tracker.BlockBreakdownTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import com.mmm.scoreboard.ScoreboardState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin
{
    @Inject(method = "handleAwardStats", at = @At("RETURN"))
    private void mmm$captureBlockBreakdown(ClientboundAwardStatsPacket packet, CallbackInfo ci)
    {
        BlockBreakdownTracker.captureVanillaStats(Minecraft.getInstance(), System.currentTimeMillis());
    }

    @Inject(method = "handleAddObjective", at = @At("HEAD"))
    private void mmm$resetScoreboardPageOnObjective(ClientboundSetObjectivePacket packet, CallbackInfo ci)
    {
        ScoreboardState.resetPage();
    }

    @Inject(method = "handleSetDisplayObjective", at = @At("HEAD"))
    private void mmm$resetScoreboardPageOnDisplay(ClientboundSetDisplayObjectivePacket packet, CallbackInfo ci)
    {
        ScoreboardState.resetPage();
    }
}
