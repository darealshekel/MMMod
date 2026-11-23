package com.mmm.legacy.collection;

import com.mmm.legacy.LegacyDrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Use the resource pack's vanilla book for both the inventory entry and the archive. */
public final class RedBookIcon {
    private static final ItemStack BOOK = new ItemStack(Items.BOOK);
    private RedBookIcon() {}
    public static void draw(LegacyDrawContext context, int x, int y) { context.drawItem(BOOK, x, y); }
}
