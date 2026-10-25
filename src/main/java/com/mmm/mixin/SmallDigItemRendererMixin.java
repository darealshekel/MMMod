package com.mmm.mixin;

import com.mmm.feature.SmallDigItemRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemRenderer.class)
public class SmallDigItemRendererMixin
{
    @WrapMethod(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V")
    private void mmm$renderSmallDigItem(ItemStack stack, ModelTransformationMode renderMode, boolean leftHanded,
                                        MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                        int overlay, BakedModel model, Operation<Void> original)
    {
        float scale = SmallDigItemRenderer.getScale(stack, renderMode);
        if (scale >= 1.0F)
        {
            original.call(stack, renderMode, leftHanded, matrices, vertexConsumers, light, overlay, model);
            return;
        }

        matrices.push();
        try
        {
            matrices.scale(scale, scale, scale);
            original.call(stack, renderMode, leftHanded, matrices, vertexConsumers, light, overlay, model);
        }
        finally
        {
            matrices.pop();
        }
    }
}
