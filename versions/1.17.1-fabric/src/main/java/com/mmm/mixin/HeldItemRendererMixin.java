package com.mmm.mixin;

import com.mmm.config.Configs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin
{
    @Shadow
    private ItemStack mainHand;

    @Shadow
    private ItemStack offHand;

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private float mmm$staticMiningToolSwingProgress(float swingProgress)
    {
        return mmm$shouldKeepMiningToolStatic() ? 0.0F : swingProgress;
    }

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float mmm$staticMiningToolEquipProgress(float equipProgress)
    {
        return mmm$shouldKeepMiningToolStatic() ? 0.0F : equipProgress;
    }

    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void mmm$disableMiningToolSwing(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci)
    {
        if (Configs.Generic.NO_SWINGING_ANIMATION.getBooleanValue() && mmm$isMiningToolArm(arm))
        {
            ci.cancel();
        }
    }

    @Unique
    private boolean mmm$isMiningToolArm(Arm arm)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null)
        {
            return mmm$isMiningToolStack(this.mainHand) || mmm$isMiningToolStack(this.offHand);
        }

        ItemStack stack = client.player.getMainArm() == arm ? this.mainHand : this.offHand;
        return mmm$isMiningToolStack(stack);
    }

    @Unique
    private boolean mmm$shouldKeepMiningToolStatic()
    {
        return Configs.Generic.NO_SWINGING_ANIMATION.getBooleanValue()
                && (mmm$isMiningToolStack(this.mainHand) || mmm$isMiningToolStack(this.offHand));
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
