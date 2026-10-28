package com.mmm.legacy;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

public final class LegacyBlockItems {
    private static final Map<String, ItemStack> ITEMS = new HashMap<>();
    private static boolean initialized;

    private LegacyBlockItems() {}

    public static void initialize() {
        if (initialized) return;
        for (Block block : Block.REGISTRY) {
            for (var state : block.stateDefinition().all()) {
                String id = LegacyBlockIds.canonicalId(state);
                int metadata = block.getMetadataFromState(state);
                String registryId = Block.REGISTRY.getKey(block).toString();
                if (registryId.equals("minecraft:lit_redstone_ore")) continue;
                if (registryId.equals("minecraft:log") || registryId.equals("minecraft:log2")
                        || registryId.equals("minecraft:leaves") || registryId.equals("minecraft:leaves2")) metadata &= 3;
                if (registryId.equals("minecraft:brown_mushroom_block") || registryId.equals("minecraft:red_mushroom_block")) metadata = 0;
                ItemStack icon = new ItemStack(block, 1, metadata);
                if (!icon.isEmpty()) ITEMS.putIfAbsent(id, icon);
            }
        }
        initialized = true;
    }

    public static ItemStack icon(String id) {
        initialize();
        ItemStack stack = ITEMS.get(id);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public static String name(String id) {
        ItemStack stack = icon(id);
        return stack.isEmpty() ? id.replace("minecraft:", "").replace('_', ' ') : stack.getHoverName();
    }
}
