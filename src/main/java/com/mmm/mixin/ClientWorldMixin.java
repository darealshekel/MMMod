package com.mmm.mixin;

import com.mmm.timer.MmmBlockBreakDetector;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "processPendingUpdate", at = @At("TAIL"))
    private void mmm$confirmBreak(BlockPos pos, BlockState state, Vec3d playerPos, CallbackInfo ci) {
        MmmBlockBreakDetector.acknowledged((ClientWorld) (Object) this, pos, state);
    }
}
