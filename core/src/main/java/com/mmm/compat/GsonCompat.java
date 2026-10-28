package com.mmm.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/** Compatibility helpers for the Gson version bundled with Minecraft 1.17. */
public final class GsonCompat
{
    private GsonCompat()
    {
    }

    @SuppressWarnings("unchecked")
    public static <T extends JsonElement> T copy(T value)
    {
        return value == null ? null : (T) new JsonParser().parse(value.toString());
    }
}
