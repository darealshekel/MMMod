package com.mmm.legacy.visual.mixin;

import com.mmm.feature.SmallDigItemRenderer;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.resource.model.ModelTransformations.Type;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemRenderer.class)
public abstract class SmallDigItemMixin {
    @Redirect(method = "renderItemInHand(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;Lnet/minecraft/client/resource/model/ModelTransformations$Type;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemRenderer;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;)V"))
    private void mmm$scaleTrackedBlock(ItemRenderer renderer, ItemStack stack, BakedModel model,
                                      ItemStack original, BakedModel originalModel, Type type, boolean left) {
        float scale = SmallDigItemRenderer.getScale(stack, type);
        if (scale == 1) { renderer.renderItem(stack, model); return; }
        GL11.glPushMatrix();
        try {
            GL11.glScalef(scale, scale, scale);
            renderer.renderItem(stack, model);
        } finally { GL11.glPopMatrix(); }
    }
}

