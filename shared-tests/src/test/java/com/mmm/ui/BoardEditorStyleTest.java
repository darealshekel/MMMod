package com.mmm.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoardEditorStyleTest {
    @Test void plateLeavesCornersOpenAndDoesNotOverdrawTranslucentPixels() {
        int face = 0x44777777, rim = 0xffaabbcc;
        int[][] pixels = new int[7][9];
        BoardEditorStyle.plate((x1, y1, x2, y2, color) -> {
            for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) {
                assertEquals(0, pixels[y][x], "overdraw at " + x + "," + y);
                pixels[y][x] = color;
            }
        }, 0, 0, 9, 7, face, rim);
        assertEquals(0, pixels[0][0]);
        assertEquals(0, pixels[6][8]);
        assertEquals(rim, pixels[0][2]);
        assertEquals(rim, pixels[3][8]);
        assertEquals(face, pixels[3][4]);
    }

    @Test void tinyOrEmptyPlateNeverDrawsOutsideItsBounds() {
        int[] calls = {0};
        BoardEditorStyle.RectSink draw = (x1, y1, x2, y2, color) -> {
            assertArrayEquals(new int[]{3, 5, 5, 6, 123}, new int[]{x1, y1, x2, y2, color});
            calls[0]++;
        };
        BoardEditorStyle.plate(draw, 3, 5, 0, 1, 123, 456);
        BoardEditorStyle.plate(draw, 3, 5, 2, 1, 123, 456);
        assertEquals(1, calls[0]);
    }
}
