package com.mmm.mixin;

import com.mmm.timer.MmmBlockBreakDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientWorldMixin {
    @Inject(method = "onPlayerActionResponse", at = @At("TAIL"))
    private void mmm$confirmBreak(PlayerActionResponseS2CPacket packet, CallbackInfo ci) {
        // A START response for a slow break is not its final result.
        if (packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                || packet.getBlockState().isAir() || !packet.isApproved()) {
            MmmBlockBreakDetector.acknowledged(MinecraftClient.getInstance().world,
                    packet.getBlockPos(), packet.getBlockState(), packet.isApproved());
        }
    }
}
