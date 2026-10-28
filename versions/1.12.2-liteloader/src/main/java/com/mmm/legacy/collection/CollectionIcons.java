package com.mmm.legacy.collection;

import com.mmm.legacy.LegacyBlockItems;
import com.mmm.tracker.CollectionProgress;
import com.mmm.util.BlockBreakdownCatalog;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;

public final class CollectionIcons {
    private CollectionIcons() {}
    public static boolean valid(String id) {
        return id != null && BlockBreakdownCatalog.isValid(id) && !unavailable(id);
    }
    public static String name(String id) {
        String custom = CollectionProgress.customName(id);
        return custom != null ? custom : LegacyBlockItems.name(CollectionProgress.iconId(id));
    }
    public static boolean unavailable(String id) { return LegacyBlockItems.icon(CollectionProgress.iconId(id)).isEmpty(); }
    public static ItemStack block(String id) {
        ItemStack stack = LegacyBlockItems.icon(CollectionProgress.iconId(id));
        // A labelled barrier means an unavailable block, never an invented legacy block statistic.
        return stack.isEmpty() ? new ItemStack(net.minecraft.block.Blocks.BARRIER) : stack;
    }
    public static ItemStack item(String id) {
        if (!id.contains(":")) id = "minecraft:" + id;
        ItemStack block = LegacyBlockItems.icon(id);
        if (!block.isEmpty()) return block;
        if (id.equals("minecraft:lime_dye")) return new ItemStack(Items.DYE, 1, 10);
        Item item = Item.REGISTRY.get(new Identifier(id));
        return item == null ? new ItemStack(net.minecraft.block.Blocks.BARRIER) : new ItemStack(item);
    }
    public static ItemStack pane(boolean complete) {
        return new ItemStack(net.minecraft.block.Blocks.STAINED_GLASS_PANE, 1, complete ? 5 : 14);
    }
}
