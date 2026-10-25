package com.mmm.legacy.social;

import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public final class LegacySocialText {
    private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF,
            0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private LegacySocialText() {}

    public static Text text(String value, Formatting color) {
        Text text = new LiteralText(value);
        text.getStyle().setColor(color);
        return text;
    }

    public static Text rgb(String value, int color) { return text(value, nearestColor(color)); }

    public static Formatting nearestColor(int rgb) {
        int best = 15;
        long minimum = Long.MAX_VALUE;
        for (int index = 0; index < COLORS.length; index++) {
            int red = ((rgb >> 16) & 255) - ((COLORS[index] >> 16) & 255);
            int green = ((rgb >> 8) & 255) - ((COLORS[index] >> 8) & 255);
            int blue = (rgb & 255) - (COLORS[index] & 255);
            long distance = red * red + green * green + blue * blue;
            if (distance < minimum) { minimum = distance; best = index; }
        }
        return Formatting.byId(best);
    }

    public static Text prefix() { return text("[MMM] ", Formatting.DARK_GRAY); }
}
