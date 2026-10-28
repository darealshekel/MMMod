package com.mmm.legacy.visual.mixin;

import com.mmm.feature.TranslucentLavaRenderer;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.block.BlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
public abstract class LavaBlockMixin {
    @Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
    private void mmm$translucentLayer(CallbackInfoReturnable<BlockLayer> ci) {
        if (TranslucentLavaRenderer.isEnabled()
                && ((LiquidBlock)(Object)this).defaultState().getMaterial() == Material.LAVA)
            ci.setReturnValue(BlockLayer.TRANSLUCENT);
    }
}

