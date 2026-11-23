package com.mmm.social;

import com.google.gson.JsonObject;

public final class MilestoneChatText
{
    private MilestoneChatText() {}

    public static String streakSuffix(int days)
    {
        return days > 0 ? " Daily streak: " + days + (days == 1 ? " day." : " days.") : "";
    }

    static int eventStreakDays(JsonObject event)
    {
        // Remote milestones must use the sender's event data, never the receiver's local calendar.
        try
        {
            var value = event.get("streakDays");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
            return Math.max(0, value.getAsBigDecimal().intValueExact());
        }
        catch (RuntimeException ignored) { return 0; }
    }
}
