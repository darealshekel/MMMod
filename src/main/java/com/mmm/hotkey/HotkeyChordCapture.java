package com.mmm.hotkey;

import java.util.LinkedHashSet;
import java.util.Set;

public final class HotkeyChordCapture
{
    private final Set<String> keys = new LinkedHashSet<>();

    public void clear()
    {
        this.keys.clear();
    }

    public void press(String key)
    {
        if (key != null && !key.isBlank())
        {
            this.keys.add(key);
        }
    }

    public boolean isEmpty()
    {
        return this.keys.isEmpty();
    }

    public String storageString()
    {
        return String.join(",", this.keys);
    }

    public String storageString(String preferredOrder)
    {
        String captured = storageString();
        if (sameKeys(captured, preferredOrder))
        {
            return preferredOrder;
        }
        return captured;
    }

    private static boolean sameKeys(String first, String second)
    {
        if (first == null || second == null || first.isBlank() || second.isBlank())
        {
            return false;
        }
        return tokenSet(first).equals(tokenSet(second));
    }

    private static Set<String> tokenSet(String value)
    {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : value.split("[+,]"))
        {
            if (!token.isBlank())
            {
                tokens.add(token.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        return tokens;
    }
}