package com.mmm.feature;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.mmm.config.Configs;
import com.mmm.util.BlockBreakdownCatalog;

import net.minecraft.block.Block;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

import org.joml.Vector3f;

public final class SmallDigItemRenderer
{
    private static final ThreadLocal<float[]> ACTIVE_SCALE = ThreadLocal.withInitial(() -> new float[] { 1.0F });
    private static final Map<Transformation, CachedTransformation> TRANSFORM_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

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
        ACTIVE_SCALE.get()[0] = scale;
    }

    public static void end()
    {
        ACTIVE_SCALE.get()[0] = 1.0F;
    }

    public static float getActiveScale()
    {
        return ACTIVE_SCALE.get()[0];
    }

    public static Transformation applyActiveScale(Transformation transform)
    {
        float scale = getActiveScale();
        if (transform == null || scale >= 1.0F)
        {
            return transform;
        }

        CachedTransformation cached = TRANSFORM_CACHE.get(transform);
        if (cached != null && Float.compare(cached.scale(), scale) == 0)
        {
            return cached.transformation();
        }

        Transformation scaled = new Transformation(
                transform.rotation(),
                transform.translation(),
                new Vector3f(scale, scale, scale));
        TRANSFORM_CACHE.put(transform, new CachedTransformation(scale, scaled));
        return scaled;
    }

    private static boolean isScaledContext(ItemDisplayContext displayContext)
    {
        return displayContext == ItemDisplayContext.GROUND ||
                displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ||
                displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
                displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ||
                displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private record CachedTransformation(float scale, Transformation transformation) {}
}
