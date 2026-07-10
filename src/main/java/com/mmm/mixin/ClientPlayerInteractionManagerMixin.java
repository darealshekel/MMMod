package com.mmm.mixin;

import com.mmm.tweak.FlatDigger;
import com.mmm.tweak.PerimeterWallDigHelper;
import com.mmm.timer.MmmBlockBreakDetector;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin
{
    @Inject(method = "attackBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void mmm$blockAttackBelowFeet(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir)
    {
        if (FlatDigger.shouldBlock(pos) || PerimeterWallDigHelper.isPositionDisallowed(pos))
        {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        MmmBlockBreakDetector.trackAttack(pos);
    }

    @Inject(method = "updateBlockBreakingProgress(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void mmm$blockProgressBelowFeet(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir)
    {
        if (FlatDigger.shouldBlock(pos) || PerimeterWallDigHelper.isPositionDisallowed(pos))
        {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
