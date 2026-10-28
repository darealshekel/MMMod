package com.mmm.feature;

import com.mmm.config.Configs;
import com.mmm.util.BlockBreakdownCatalog;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3f;

public final class SmallDigItemRenderer
{
    private static final ThreadLocal<Float> ACTIVE_SCALE = ThreadLocal.withInitial(() -> 1.0F);

    private SmallDigItemRenderer()
    {
    }

    public static float getScale(ItemStack stack, ItemDisplayContext displayContext)
    {
        if (!Configs.Generic.SMALL_DIG_ITEMS.getBooleanValue() || stack == null || stack.isEmpty())
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
        return displayContext == ItemDisplayContext.GROUND ? groundScale : handScale;
    }

    public static void begin(ItemStack stack, ItemDisplayContext displayContext)
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

    public static ItemTransform applyActiveScale(ItemTransform transform)
    {
        float scale = getActiveScale();
        if (transform == null || scale >= 1.0F)
        {
            return transform;
        }

        Vector3f scaled = new Vector3f(scale, scale, scale);
        return new ItemTransform(transform.rotation(), transform.translation(), scaled);
    }

    private static boolean isScaledContext(ItemDisplayContext displayContext)
    {
        return displayContext == ItemDisplayContext.GROUND ||
                displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ||
                displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
                displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ||
                displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
