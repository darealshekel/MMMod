package com.mmm.feature;

import com.mmm.config.Configs;
import com.mmm.util.BlockBreakdownCatalog;

import net.minecraft.block.Block;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ShearsItem;

import net.minecraft.util.math.Vec3f;

public final class SmallDigItemRenderer
{
    private static final ThreadLocal<Float> ACTIVE_SCALE = ThreadLocal.withInitial(() -> 1.0F);

    private SmallDigItemRenderer()
    {
    }

    public static float getScale(ItemStack stack, ModelTransformation.Mode displayContext)
    {
        if (!Configs.Generic.SMALL_DIG_ITEMS.getBooleanValue() || stack == null || stack.isEmpty())
        {
            return 1.0F;
        }

        // Keep tools on the vanilla transform so their glint remains resolution-independent.
        if (stack.getItem() instanceof MiningToolItem || stack.getItem() instanceof ShearsItem)
        {
            return 1.0F;
        }

        if (!(stack.getItem() instanceof BlockItem blockItem) || !isScaledContext(displayContext))
        {
            return 1.0F;
        }

        Block block = blockItem.getBlock();
        if (!BlockBreakdownCatalog.isValid(block))
        {
            return 1.0F;
        }

        float handScale = (float) Configs.Generic.SMALL_DIG_ITEM_SCALE.getDoubleValue();
        float groundScale = Math.max(0.05F, handScale * 0.5F);
        return displayContext == ModelTransformation.Mode.GROUND ? groundScale : handScale;
    }

    public static void begin(ItemStack stack, ModelTransformation.Mode displayContext)
    {
        begin(getScale(stack, displayContext));
    }

    public static void begin(float scale)
    {
        ACTIVE_SCALE.set(scale);
    }

    public static void end()
    {
        ACTIVE_SCALE.set(1.0F);
    }

    public static float getActiveScale()
    {
        return ACTIVE_SCALE.get();
    }

    public static Transformation applyActiveScale(Transformation transform)
    {
        float scale = getActiveScale();
        if (transform == null || scale >= 1.0F)
        {
            return transform;
        }

        Vec3f scaled = new Vec3f(scale, scale, scale);
        return new Transformation(transform.rotation, transform.translation, scaled);
    }

    private static boolean isScaledContext(ModelTransformation.Mode displayContext)
    {
        return displayContext == ModelTransformation.Mode.GROUND ||
                displayContext == ModelTransformation.Mode.FIRST_PERSON_LEFT_HAND ||
                displayContext == ModelTransformation.Mode.FIRST_PERSON_RIGHT_HAND ||
                displayContext == ModelTransformation.Mode.THIRD_PERSON_LEFT_HAND ||
                displayContext == ModelTransformation.Mode.THIRD_PERSON_RIGHT_HAND;
    }
}
