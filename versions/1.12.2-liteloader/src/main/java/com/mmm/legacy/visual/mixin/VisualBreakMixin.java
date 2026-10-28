package com.mmm.legacy.visual.mixin;

import com.mmm.feature.BreakingIndicatorRenderer;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class VisualBreakMixin {
    @Unique private BlockState mmm$visualBefore;
    @Unique private ClientWorld mmm$visualWorld;
    @Inject(method = "finishMiningBlock", at = @At("HEAD"))
    private void mmm$captureInstantBreak(BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        mmm$visualWorld = Minecraft.getInstance().world;
        mmm$visualBefore = mmm$visualWorld == null ? null : mmm$visualWorld.getBlockState(pos);
    }
    @Inject(method = "finishMiningBlock", at = @At("RETURN"))
    private void mmm$flashInstantBreak(BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        Minecraft client = Minecraft.getInstance();
        if (ci.getReturnValueZ() && client.world == mmm$visualWorld)
            BreakingIndicatorRenderer.completed(client, pos, mmm$visualBefore);
        mmm$visualBefore = null;
        mmm$visualWorld = null;
    }
}

