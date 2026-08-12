package com.mmm.mixin;

import com.mmm.feature.SmallDigItemRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public class SmallDigItemRenderStateMixin
{
    @Shadow
    ItemTransform itemTransform;

    @Inject(
            method = "setItemTransform(Lnet/minecraft/client/resources/model/cuboid/ItemTransform;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mmm$setSmallDigItemTransform(ItemTransform transform, CallbackInfo ci)
    {
        ItemTransform scaledTransform = SmallDigItemRenderer.applyActiveScale(transform);
        if (scaledTransform != transform)
        {
            this.itemTransform = scaledTransform;
            ci.cancel();
        }
    }
}
