package com.mmm.legacy.visual.mixin;

import com.mmm.config.Configs;
import com.mmm.feature.SmallDigItemRenderer;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemInHandRenderer.class)
public abstract class NoSwingMixin {
    @Redirect(method = "renderInFirstPerson", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/ItemInHandRenderer;renderHandInFirstPerson(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/item/ItemStack;F)V"))
    private void mmm$steadyTool(ItemInHandRenderer renderer, ClientPlayerEntity player, float partialTick, float pitch,
                               InteractionHand hand, float swing, ItemStack stack, float equip) {
        boolean steady = Configs.Generic.NO_SWINGING_ANIMATION.getBooleanValue() && SmallDigItemRenderer.isMiningTool(stack);
        renderer.renderHandInFirstPerson(player, partialTick, pitch, hand, steady ? 0 : swing, stack, steady ? 0 : equip);
    }
}
