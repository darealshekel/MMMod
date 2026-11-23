package com.mmm.legacy;

import com.mmm.ui.CollectionBoardState;
import com.mmm.ui.BoardEditorStyle;
import net.minecraft.client.render.TextRenderer;

/** MMM archive panels, in scaled GUI pixels. */
public final class BoardArt {
    public static final int BLACK = 0xff0c0c0c, BASE = 0xff151515, RAIL = 0xff101010,
            TILE = 0xff202020, LINE = 0xff373737, ACTIVE = 0xff39191b, RED = 0xffd90000,
            ROSE = 0xffff9494, TEXT = 0xfff5f5f5, MUTED = 0xffb5b1b1;
    private BoardArt() {}
    public static void header(LegacyDrawContext draw, TextRenderer font, int width, int left, int available, String section) {
        int h = BoardEditorStyle.HEADER_HEIGHT;
        draw.fill(0, 0, width, h, BoardEditorStyle.SURFACE);
        draw.fill(0, h - 1, width, h, BoardEditorStyle.LINE);
        draw.drawText(font, "MMM", left, 11, BoardEditorStyle.ROSE, false);
        int offset = font.getWidth("MMM") + 5;
        draw.drawText(font, font.trim("/ " + section, Math.max(0, available - offset)),
                left + offset, 11, BoardEditorStyle.TEXT, false);
    }
    public static void section(LegacyDrawContext draw, TextRenderer font, int x, int y, int w, String label) {
        String visible = font.trim(label, Math.max(0, w - 8));
        draw.drawText(font, visible, x, y, ROSE, false);
        int line = x + font.getWidth(visible) + 7;
        if (line < x + w) draw.fill(line, y + 4, x + w, y + 5, LINE);
    }
    public static void row(LegacyDrawContext draw, int x, int y, int w, int h, boolean hovered) {
        BoardEditorStyle.plate(draw::fill, x, y, w, h, hovered ? TILE : BASE, hovered ? MUTED : LINE);
    }
    public static long now() { return System.nanoTime() / 1_000_000L; }
    public static float reveal(long start, int order) { return CollectionBoardState.reveal(now() - start, order); }
    public static void meter(LegacyDrawContext c, int x, int y, int w, float fill) {
        c.fill(x, y, x + w, y + 3, LINE);
        c.fill(x, y, x + Math.round(w * Math.max(0f, Math.min(1f, fill))), y + 3, RED);
    }
    public static void dossier(LegacyDrawContext c, int x, int y, int w, int h) {
        row(c, x, y, w, h, false);
        c.fill(x, y, x + 2, y + h, ROSE);
    }
}
