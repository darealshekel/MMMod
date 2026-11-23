package com.mmm.ui;

/** Shared board colors and GUI-pixel layout for the loader-specific renderers. */
public final class BoardEditorStyle {
    public static final int FLOOR = 0xff050505, SURFACE = 0xff151515, TILE = 0xff202020;
    public static final int LINE = 0xff373737, TEXT = 0xfff6f3ef, MUTED = 0xffb5b1b1, ROSE = 0xffff9494;
    public static final int HEADER_HEIGHT = 31;

    private BoardEditorStyle() {}

    public static int contentWidth(int width, int max) {
        return Math.max(1, Math.min(width - 16, max));
    }

    public static int contentX(int width, int max) {
        return Math.max(0, (width - contentWidth(width, max)) / 2);
    }

    @FunctionalInterface
    public interface RectSink {
        void fill(int x1, int y1, int x2, int y2, int argb);
    }

    /** Paint every pixel at most once so menu opacity also works on the plate face. */
    public static void plate(RectSink draw, int x, int y, int w, int h, int face, int rim) {
        if (w <= 0 || h <= 0) return;
        if (w < 5 || h < 5) {
            draw.fill(x, y, x + w, y + h, face);
            return;
        }
        draw.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        draw.fill(x + 2, y, x + w - 2, y + 1, rim);
        draw.fill(x + 2, y + h - 1, x + w - 2, y + h, rim);
        draw.fill(x, y + 2, x + 1, y + h - 2, rim);
        draw.fill(x + w - 1, y + 2, x + w, y + h - 2, rim);
    }
}
