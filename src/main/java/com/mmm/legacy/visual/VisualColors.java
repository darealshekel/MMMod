package com.mmm.legacy.visual;

import com.mmm.config.Configs;

public final class VisualColors {
    private VisualColors() {}
    public static int parse(String value, String fallback) {
        String hex = Configs.normalizeHexColor(value, fallback);
        long color = Long.parseLong(hex.substring(1), 16);
        return (int) (hex.length() == 7 ? color | 0xFF000000L : color);
    }
    public static int alpha(int color, float opacity) {
        int a = Math.round((color >>> 24) * Math.max(0, Math.min(1, opacity)));
        return (a << 24) | (color & 0xFFFFFF);
    }
    public static int interpolate(int start, int end, float amount) {
        float t = Float.isFinite(amount) ? Math.max(0, Math.min(1, amount)) : 0;
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= Math.round(((start >>> shift) & 255) * (1 - t) + ((end >>> shift) & 255) * t) << shift;
        return result;
    }
}

