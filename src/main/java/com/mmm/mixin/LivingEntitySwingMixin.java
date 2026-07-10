package com.mmm.mixin;

import com.mmm.config.Configs;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin
{
    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void mmm$disableMiningToolSwing(Hand hand, CallbackInfo ci)
    {
        mmm$cancelLocalMiningToolSwing(hand, ci);
    }

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void mmm$disableMiningToolSwing(Hand hand, boolean fromServerPlayer, CallbackInfo ci)
    {
        mmm$cancelLocalMiningToolSwing(hand, ci);
    }

    @Unique
    private void mmm$cancelLocalMiningToolSwing(Hand hand, CallbackInfo ci)
    {
        if (Configs.Generic.NO_SWINGING_ANIMATION.getBooleanValue() == false || ((Object) this instanceof ClientPlayerEntity) == false)
        {
            return;
        }

        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (mmm$isMiningToolStack(player.getStackInHand(hand)))
        {
            ci.cancel();
        }
    }

    @Unique
    private boolean mmm$isMiningToolStack(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }

        Item item = stack.getItem();
        return item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE || item == Items.GOLDEN_PICKAXE || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE
                || item == Items.WOODEN_SHOVEL || item == Items.STONE_SHOVEL || item == Items.IRON_SHOVEL || item == Items.GOLDEN_SHOVEL || item == Items.DIAMOND_SHOVEL || item == Items.NETHERITE_SHOVEL
                || item == Items.WOODEN_AXE || item == Items.STONE_AXE || item == Items.IRON_AXE || item == Items.GOLDEN_AXE || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
    }
}
