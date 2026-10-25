package com.mmm.mixin;

import com.mmm.feature.FlatDigger;
import com.mmm.feature.PerimeterWallDigHelper;
import com.mmm.timer.MmmBlockBreakDetector;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin
{
    @org.spongepowered.asm.mixin.Unique private MmmBlockBreakDetector.BreakAttempt mmm$breakAttempt;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void mmm$captureBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        mmm$breakAttempt = MmmBlockBreakDetector.beginBreak(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void mmm$finishBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MmmBlockBreakDetector.completeBreak(mmm$breakAttempt, cir.getReturnValueZ());
        mmm$breakAttempt = null;
    }

    @Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void mmm$blockAttackBelowFeet(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir)
    {
        if (FlatDigger.shouldBlock(pos) || PerimeterWallDigHelper.isPositionDisallowed(pos))
        {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }


    }

    @Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void mmm$blockProgressBelowFeet(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir)
    {
        if (FlatDigger.shouldBlock(pos) || PerimeterWallDigHelper.isPositionDisallowed(pos))
        {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
