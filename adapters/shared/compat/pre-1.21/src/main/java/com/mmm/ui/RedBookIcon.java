package com.mmm.ui;

import com.mmm.ui.BoardPlatform.Canvas;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class RedBookIcon {
    private static final ItemStack BOOK = new ItemStack(Items.BOOK);
    private RedBookIcon() {}
    public static void draw(Canvas c, int x, int y) {
        c.raw().drawItem(BOOK, x, y);
    }
}
