package com.mmm.mixin;

import com.mmm.tweak.SmallDigItemRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemRenderer.class)
public class SmallDigItemRendererMixin
{
    @WrapMethod(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;I)V")
    private void mmm$renderSmallDigItem(ItemStack stack, ModelTransformationMode renderMode, int light,
                                        int overlay, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        World world, int seed, Operation<Void> original)
    {
        float scale = SmallDigItemRenderer.getScale(stack, renderMode);
        if (scale >= 1.0F)
        {
            original.call(stack, renderMode, light, overlay, matrices, vertexConsumers, world, seed);
            return;
        }

        matrices.push();
        try
        {
            matrices.scale(scale, scale, scale);
            original.call(stack, renderMode, light, overlay, matrices, vertexConsumers, world, seed);
        }
        finally
        {
            matrices.pop();
        }
    }
}
