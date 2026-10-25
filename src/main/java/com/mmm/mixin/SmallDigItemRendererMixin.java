package com.mmm.mixin;

import com.mmm.feature.SmallDigItemRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public class SmallDigItemRendererMixin
{
    @Inject(
            method = "appendItemLayers(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V",
            at = @At("HEAD")
    )
    private void mmm$beginSmallDigItemRender(ItemStackRenderState state, ItemStack stack, ItemDisplayContext displayContext, Level world, ItemOwner context, int seed, CallbackInfo ci)
    {
        SmallDigItemRenderer.begin(stack, displayContext);
    }

    @Inject(
            method = "appendItemLayers(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V",
            at = @At("RETURN")
    )
    private void mmm$endSmallDigItemRender(ItemStackRenderState state, ItemStack stack, ItemDisplayContext displayContext, Level world, ItemOwner context, int seed, CallbackInfo ci)
    {
        SmallDigItemRenderer.end();
    }
}
