package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import java.util.Locale;

public final class SettingsUi {
    private SettingsUi() {}
    public static int background() {
        return opacity(0xFF050505);
    }
    public static int opacity(int color) {
        int alpha = (int) Math.round((color >>> 24) * Configs.Generic.MENU_OPACITY.getIntegerValue() / 100.0);
        return alpha << 24 | color & 0xFFFFFF;
    }
    public static String display(IConfigBase config) {
        if (config instanceof IConfigBoolean b) return b.getBooleanValue() ? "ON" : "OFF";
        return config.getStringValue();
    }
    public static double fraction(IConfigBase config) {
        if (config instanceof IConfigInteger n) return ratio(n.getIntegerValue(), n.getMinIntegerValue(), n.getMaxIntegerValue());
        if (config instanceof IConfigDouble n) return ratio(n.getDoubleValue(), n.getMinDoubleValue(), n.getMaxDoubleValue());
        return 0;
    }
    private static double ratio(double value, double min, double max) {
        return max <= min ? 0 : Math.max(0, Math.min(1, (value - min) / (max - min)));
    }
    public static void slide(IConfigBase config, double fraction) {
        fraction = Math.max(0, Math.min(1, fraction));
        if (config instanceof IConfigInteger n) n.setIntegerValue((int) Math.round(n.getMinIntegerValue() + fraction * (n.getMaxIntegerValue() - n.getMinIntegerValue())));
        if (config instanceof IConfigDouble n) n.setDoubleValue(Math.round((n.getMinDoubleValue() + fraction * (n.getMaxDoubleValue() - n.getMinDoubleValue())) * 100) / 100.0);
    }
    public static int color(String value) {
        String hex = value.replace("#", "");
        if (!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException("Use #RRGGBB or #AARRGGBB");
        return (int) Long.parseLong(hex, 16) | (hex.length() == 6 ? 0xFF000000 : 0);
    }
    public static String hex(int color) { return String.format(Locale.ROOT, "#%08X", color); }
    public static String validate(IConfigBase config, String input) {
        String value = input.trim();
        if (config instanceof IConfigInteger n) {
            int parsed = Integer.parseInt(value);
            if (parsed < n.getMinIntegerValue() || parsed > n.getMaxIntegerValue()) throw new IllegalArgumentException("Out of range");
        }
        if (config instanceof IConfigDouble n) {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < n.getMinDoubleValue() || parsed > n.getMaxDoubleValue()) throw new IllegalArgumentException("Out of range");
        }
        return config instanceof ConfigColor ? hex(color(value)) : value;
    }
    public static long duration(String value) {
        String input = value.trim().toLowerCase(Locale.ROOT);
        if (!input.matches("[0-9]+(\\.[0-9]+)?[hms]?")) throw new IllegalArgumentException("Invalid duration");
        String amount = input.replaceAll("[hms]$", "");
        double multiplier = input.endsWith("h") ? 3600000 : input.endsWith("s") ? 1000 : 60000;
        double raw = Double.parseDouble(amount) * multiplier;
        if (!Double.isFinite(raw) || raw < 1000 || raw > com.mmm.timer.MmmTimerState.MAX_DURATION_MS)
            throw new IllegalArgumentException("Duration must be between 1s and 24h");
        return Math.round(raw);
    }
    public static int sidebarCapacity(int height) { return Math.max(1, (height - 78) / 22); }
    public static int columns(int contentWidth) { return contentWidth >= 640 ? 2 : 1; }
    public static int cardWidth(int contentWidth) { return (contentWidth - 8 - (columns(contentWidth) - 1) * 12) / columns(contentWidth); }
    public static int controlWidth(int cardWidth) { return Math.min(112, Math.max(60, cardWidth / 3)); }
    public static boolean matches(String text, String query) {
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String word : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) if (!haystack.contains(word)) return false;
        return true;
    }
    public static int clampScroll(int offset, int total, int capacity) { return Math.max(0, Math.min(offset, Math.max(0, total - capacity))); }
}
