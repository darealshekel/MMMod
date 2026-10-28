package com.mmm.legacy.mixin;

import com.mmm.timer.MmmBlockBreakDetector;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class LegacyBreakAckMixin {
    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void mmm$block(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        MmmBlockBreakDetector.acknowledged(packet.getPos(), packet.getBlockState());
    }

    @Inject(method = "handleBlocksUpdate", at = @At("TAIL"))
    private void mmm$blocks(BlocksUpdateS2CPacket packet, CallbackInfo ci) {
        for (BlocksUpdateS2CPacket.BlockUpdate update : packet.getUpdates())
            MmmBlockBreakDetector.acknowledged(update.getBlockPos(), update.getBlockState());
    }
}
