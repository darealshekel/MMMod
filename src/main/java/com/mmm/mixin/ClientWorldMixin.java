package com.mmm.mixin;

import com.mmm.timer.MmmBlockBreakDetector;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientWorldMixin {
    @Inject(method = "syncBlockState", at = @At("TAIL"))
    private void mmm$confirmBreak(BlockPos pos, BlockState state, Vec3 playerPos, CallbackInfo ci) {
        MmmBlockBreakDetector.acknowledged((ClientLevel) (Object) this, pos, state);
    }
}
