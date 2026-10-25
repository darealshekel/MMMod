package com.mmm.mixin;

import com.mmm.config.Configs;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin
{
    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float mmm$staticMiningToolSwingProgress(float swingProgress)
    {
        return mmm$shouldKeepMiningToolStatic() ? 0.0F : swingProgress;
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float mmm$staticMiningToolEquipProgress(float equipProgress)
    {
        return mmm$shouldKeepMiningToolStatic() ? 0.0F : equipProgress;
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void mmm$disableMiningToolSwing(PoseStack matrices, HumanoidArm arm, float swingProgress, CallbackInfo ci)
    {
        if (Configs.Generic.NO_SWINGING_ANIMATION.getBooleanValue() && mmm$isMiningToolArm(arm))
        {
            ci.cancel();
        }
    }

    @Unique
    private boolean mmm$isMiningToolArm(HumanoidArm arm)
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null)
        {
            return mmm$isMiningToolStack(this.mainHandItem) || mmm$isMiningToolStack(this.offHandItem);
        }

        ItemStack stack = client.player.getMainArm() == arm ? this.mainHandItem : this.offHandItem;
        return mmm$isMiningToolStack(stack);
    }

    @Unique
    private boolean mmm$shouldKeepMiningToolStatic()
    {
        return Configs.Generic.NO_SWINGING_ANIMATION.getBooleanValue()
                && (mmm$isMiningToolStack(this.mainHandItem) || mmm$isMiningToolStack(this.offHandItem));
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
