package com.mmm.mixin;

import com.mmm.feature.SmallDigItemRenderer;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class SmallDigItemEntityRendererMixin
{
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("HEAD")
    )
    private void mmm$beginSmallDigItemEntityUpdate(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci)
    {
        SmallDigItemRenderer.begin(entity.getItem(), ItemDisplayContext.GROUND);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void mmm$endSmallDigItemEntityUpdate(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci)
    {
        SmallDigItemRenderer.end();
    }
}
