package com.mmm.legacy.mixin;

import com.mmm.timer.MmmBlockBreakDetector;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class LegacyMiningMixin {
    @Unique private MmmBlockBreakDetector.BreakAttempt mmm$attempt;

    @Inject(method = "finishMiningBlock", at = @At("HEAD"))
    private void mmm$capture(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        mmm$attempt = MmmBlockBreakDetector.beginBreak(pos);
    }

    @Inject(method = "finishMiningBlock", at = @At("RETURN"))
    private void mmm$completed(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MmmBlockBreakDetector.completeBreak(mmm$attempt, cir.getReturnValueZ());
        mmm$attempt = null;
    }
}
