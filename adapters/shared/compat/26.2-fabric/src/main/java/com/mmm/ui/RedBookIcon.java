package com.mmm.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RedBookIcon {
    private RedBookIcon() {}
    public static void draw(GuiGraphicsExtractor c, int x, int y) {
        BoardItemIcon.drawTexture(c, BoardItemIcon.BOOK_TEXTURE, x, y);
    }
}
