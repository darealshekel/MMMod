package com.mmm.ui;

import com.mmm.ui.BoardPlatform.Canvas;
import net.minecraft.util.Util;

/** Original MMM archive surfaces; all coordinates are GUI pixels. */
final class CollectionBoardArt {
    static final int BLACK = MmmUi.PANEL, BASE = 0xff151515, RAIL = 0xff101010;
    static final int TILE = 0xff202020, LINE = 0xff373737, ACTIVE = 0xff39191b;
    static final int RED = MmmUi.RED, ROSE = 0xffff9494, TEXT = MmmUi.TEXT, MUTED = 0xffb5b1b1;
    private CollectionBoardArt() {}
    static long now() { return Util.getMeasuringTimeMs(); }
    static float reveal(long elapsed, int index) {
        return CollectionBoardState.reveal(elapsed, index);
    }
    // Both endpoints are opaque archive colors.
    static int lerp(float t, int from, int to) {
        int result = 0xff000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            result |= Math.round(a + t * (b - a)) << shift;
        }
        return result;
    }
    static String tierLabel(int tier) {
        int[] units = {10, 9, 5, 4, 1};
        String[] digits = {"X", "IX", "V", "IV", "I"};
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < units.length; i++)
            while (tier >= units[i]) { tier -= units[i]; label.append(digits[i]); }
        return label.toString();
    }
    static void meter(Canvas c, int x, int y, int w, float fill, int color) {
        c.raw().fill(x, y, x + w, y + 3, LINE);
        c.raw().fill(x, y, x + Math.round(w * net.minecraft.util.math.MathHelper.clamp(fill, 0f, 1f)), y + 3, color);
    }
    static void plate(Canvas c, int x, int y, int w, int h, int rim, int face) {
        BoardEditorStyle.plate(c.raw()::fill, x, y, w, h, MmmUi.menuSurface(face), MmmUi.menuSurface(rim));
    }
    static void dossier(Canvas c, int x, int y, int w, int h) {
        plate(c,x,y,w,h,0xff504045,0xff211e20);
        c.raw().fill(x+6,y+3,x+w-6,y+4,0xff654146);
        c.raw().fill(x+9,y,x+20,y+3,RED);
    }
    static void brackets(Canvas c, int x, int y, int w, int h, int reach) {
        for (int dx : SIDES) for (int dy : SIDES) {
            int xx = dx == 0 ? x : x+w-1, yy = dy == 0 ? y : y+h-1;
            c.raw().fill(dx == 0 ? xx : xx-reach+1,yy,dx == 0 ? xx+reach : xx+1,yy+1,ROSE);
            c.raw().fill(xx,dy == 0 ? yy : yy-reach+1,xx+1,dy == 0 ? yy+reach : yy+1,ROSE);
        }
    }
    private static final int[] SIDES = {0, 1};
}
