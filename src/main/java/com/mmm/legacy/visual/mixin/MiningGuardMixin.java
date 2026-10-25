package com.mmm.legacy.visual.mixin;

import com.mmm.feature.FlatDigger;
import com.mmm.feature.PerimeterWallDigHelper;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MiningGuardMixin {
    @Shadow public abstract void stopMiningBlock();

    @Inject(method = "startMiningBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private void mmm$guardMiningStart(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> ci) {
        if (FlatDigger.shouldBlock(pos) || PerimeterWallDigHelper.isPositionDisallowed(pos)) {
            stopMiningBlock();
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "tickBlockMining(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private void mmm$guardMiningProgress(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> ci) {
        if (FlatDigger.shouldBlock(pos) || PerimeterWallDigHelper.isPositionDisallowed(pos)) {
            // Releasing sneak or enabling a guard mid-break must abort the old server-side dig too.
            stopMiningBlock();
            ci.setReturnValue(true);
        }
    }
}
