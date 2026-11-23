package com.mmm.ui;

import net.minecraft.client.gui.DrawContext;
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
    static void meter(DrawContext c, int x, int y, int w, float fill, int color) {
        c.fill(x, y, x + w, y + 3, LINE);
        c.fill(x, y, x + Math.round(w * Math.clamp(fill, 0f, 1f)), y + 3, color);
    }
    // Clipped corners and an inset lip keep a plate legible at native GUI scale.
    static void plate(DrawContext c, int x, int y, int w, int h, int rim, int face) {
        c.fill(x + 2, y, x + w - 2, y + h, rim);
        c.fill(x, y + 2, x + w, y + h - 2, rim);
        c.fill(x + 2, y + 1, x + w - 2, y + h - 2, face);
        c.fill(x + 1, y + 3, x + w - 1, y + h - 3, face);
    }
    static void dossier(DrawContext c, int x, int y, int w, int h) {
        plate(c,x,y,w,h,0xff504045,0xff211e20);
        c.fill(x+6,y+3,x+w-6,y+4,0xff654146);
        c.fill(x+9,y,x+20,y+3,RED);
    }
    static void brackets(DrawContext c, int x, int y, int w, int h, int reach) {
        for (int dx : SIDES) for (int dy : SIDES) {
            int xx = dx == 0 ? x : x+w-1, yy = dy == 0 ? y : y+h-1;
            c.fill(dx == 0 ? xx : xx-reach+1,yy,dx == 0 ? xx+reach : xx+1,yy+1,ROSE);
            c.fill(xx,dy == 0 ? yy : yy-reach+1,xx+1,dy == 0 ? yy+reach : yy+1,ROSE);
        }
    }
    private static final int[] SIDES = {0, 1};
}
