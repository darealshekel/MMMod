package com.mmm.config;

import com.mmm.config.value.IConfigOptionListEntry;
import java.util.Locale;

public enum PlaytimeFormat implements IConfigOptionListEntry
{
    DAYS_HOURS("days_hours", "Days Hours"),
    HOURS_MINUTES("hours_minutes", "Hours Minutes"),
    HOURS_MINUTES_SECONDS("hours_minutes_seconds", "Hours Min Sec");

    private final String key;
    private final String label;

    PlaytimeFormat(String key, String label) { this.key = key; this.label = label; }
    public String getStringValue() { return key; }
    public String getDisplayName() { return label; }
    public IConfigOptionListEntry cycle(boolean forward)
    {
        return values()[Math.floorMod(ordinal() + (forward ? 1 : -1), values().length)];
    }
    public IConfigOptionListEntry fromString(String value)
    {
        for (PlaytimeFormat format : values()) if (format.key.equalsIgnoreCase(value)) return format;
        return HOURS_MINUTES;
    }
    public String format(long millis)
    {
        long seconds = Math.max(0L, millis) / 1000L;
        long hours = seconds / 3600L;
        return switch (this)
        {
            case DAYS_HOURS -> String.format(Locale.ROOT, "%dd %02dh", hours / 24L, hours % 24L);
            case HOURS_MINUTES -> String.format(Locale.ROOT, "%dh %02dm", hours, seconds / 60L % 60L);
            case HOURS_MINUTES_SECONDS -> String.format(Locale.ROOT, "%dh %02dm %02ds", hours, seconds / 60L % 60L, seconds % 60L);
        };
    }
}
