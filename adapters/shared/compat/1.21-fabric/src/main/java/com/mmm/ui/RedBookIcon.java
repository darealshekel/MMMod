package com.mmm.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class RedBookIcon {
    private static final ItemStack BOOK = new ItemStack(Items.BOOK);
    private RedBookIcon() {}
    public static void draw(DrawContext c, int x, int y) {
        c.drawItem(BOOK, x, y);
    }
}
