package com.mmm.ui;

import net.minecraft.client.gui.DrawContext;

public final class RedBookIcon {
    private RedBookIcon() {}
    public static void draw(DrawContext c, int x, int y) {
        c.fill(x + 3, y + 1, x + 12, y + 3, 0xFF480C13);
        c.fill(x + 2, y + 3, x + 14, y + 15, 0xFF480C13);
        c.fill(x + 1, y + 5, x + 3, y + 13, 0xFF480C13);
        c.fill(x + 3, y + 3, x + 12, y + 12, 0xFFCC2633);
        c.fill(x + 4, y + 3, x + 11, y + 4, 0xFFFF6970);
        c.fill(x + 3, y + 4, x + 4, y + 12, 0xFFF14951);
        c.fill(x + 2, y + 5, x + 3, y + 13, 0xFF921B28);
        c.fill(x + 4, y + 12, x + 13, y + 14, 0xFFF0E4C2);
        c.fill(x + 5, y + 13, x + 12, y + 14, 0xFFBDB396);
        c.fill(x + 3, y + 14, x + 13, y + 15, 0xFFCF2937);
        c.fill(x + 11, y + 7, x + 14, y + 10, 0xFF5C3012);
        c.fill(x + 11, y + 7, x + 13, y + 9, 0xFFFFC857);
    }
}
