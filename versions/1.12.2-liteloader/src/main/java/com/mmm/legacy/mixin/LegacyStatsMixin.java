package com.mmm.legacy.mixin;

import com.mmm.tracker.BlockBreakdownTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.StatsS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class LegacyStatsMixin {
    @Inject(method = "handleStats", at = @At("RETURN"))
    private void mmm$captureStats(StatsS2CPacket packet, CallbackInfo ci) {
        BlockBreakdownTracker.captureVanillaStats(Minecraft.getInstance(), System.currentTimeMillis());
    }
}
