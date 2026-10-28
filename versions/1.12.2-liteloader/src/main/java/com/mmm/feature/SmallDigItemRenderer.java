package com.mmm.feature;

import com.mmm.config.Configs;
import com.mmm.util.BlockBreakdownCatalog;
import net.minecraft.client.resource.model.ModelTransformations.Type;
import net.minecraft.item.*;

public final class SmallDigItemRenderer {
    private SmallDigItemRenderer() {}
    public static boolean isMiningTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ToolItem || item instanceof HoeItem || item instanceof ShearsItem;
    }
    public static float getScale(ItemStack stack, Type context) {
        if (!Configs.Generic.SMALL_DIG_ITEMS.getBooleanValue() || stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof BlockItem block) || !isScaledContext(context)) return 1;
        var state = block.getBlock().getStateFromMetadata(stack.getMetadata());
        if (!BlockBreakdownCatalog.isValid(state)) return 1;
        float scale = (float) Math.max(0.1, Math.min(1, Configs.Generic.SMALL_DIG_ITEM_SCALE.getDoubleValue()));
        return context == Type.GROUND ? Math.max(0.05F, scale * 0.5F) : scale;
    }
    private static boolean isScaledContext(Type type) {
        return type == Type.GROUND || type == Type.FIRST_PERSON_LEFT_HAND || type == Type.FIRST_PERSON_RIGHT_HAND
                || type == Type.THIRD_PERSON_LEFT_HAND || type == Type.THIRD_PERSON_RIGHT_HAND;
    }
}

