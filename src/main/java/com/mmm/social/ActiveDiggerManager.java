package com.mmm.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mmm.config.Configs;
import java.util.HashSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class ActiveDiggerManager
{
    private static final Pattern MINECRAFT_USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final long DEFAULT_TTL_MS = 75_000L;
    private static final int MMM_RED = 0xE00000;
    private static final Map<String, ActiveDigger> ACTIVE_DIGGERS = new ConcurrentHashMap<>();
    private static volatile Set<String> friendNames = Set.of();

    private ActiveDiggerManager()
    {
    }

    public static void applySocialState(JsonObject event)
    {
        long expiresAt = System.currentTimeMillis() + ttlMs(event);
        ACTIVE_DIGGERS.clear();
        for (Map.Entry<String, String> entry : usernames(event, "activeDiggers").entrySet())
        {
            ACTIVE_DIGGERS.put(entry.getKey(), new ActiveDigger(entry.getValue(), expiresAt));
        }
        applyFriends(event);
    }

    public static void applyFriends(JsonObject event)
    {
        friendNames = Set.copyOf(usernames(event, "friends").keySet());
    }

    public static void applyPresence(JsonObject event)
    {
        String displayName = validUsername(stringValue(event, "username"));
        String username = normalizeUsername(displayName);
        if (username.isBlank() || !event.has("active") || !event.get("active").isJsonPrimitive())
        {
            return;
        }
        if (event.get("active").getAsBoolean())
        {
            ACTIVE_DIGGERS.put(username, new ActiveDigger(displayName, System.currentTimeMillis() + ttlMs(event)));
        }
        else
        {
            ACTIVE_DIGGERS.remove(username);
        }
    }

    public static MutableText decorateName(String username, Text original)
    {
        if (!Configs.Generic.SHOW_ACTIVE_DIGGERS.getBooleanValue()
                || !isVisible(username, Configs.Generic.ACTIVE_DIGGERS_FRIENDS_ONLY.getBooleanValue(), System.currentTimeMillis()))
        {
            return null;
        }

        return Text.empty()
                .append(Text.literal("\u26CF").styled(style -> style.withColor(MMM_RED)))
                .append(Text.literal(" "))
                .append(original.copy());
    }

    static boolean isVisible(String username, boolean friendsOnly, long now)
    {
        String normalized = normalizeUsername(username);
        ActiveDigger activeDigger = ACTIVE_DIGGERS.get(normalized);
        if (activeDigger == null)
        {
            return false;
        }
        if (activeDigger.expiresAt() <= now)
        {
            ACTIVE_DIGGERS.remove(normalized, activeDigger);
            return false;
        }
        return !friendsOnly || friendNames.contains(normalized);
    }

    public static List<String> visibleRemoteDiggers(Collection<String> localPlayerNames)
    {
        if (!Configs.Generic.SHOW_ACTIVE_DIGGERS.getBooleanValue())
        {
            return List.of();
        }
        Set<String> localNames = new HashSet<>();
        for (String name : localPlayerNames)
        {
            String normalized = normalizeUsername(name);
            if (!normalized.isBlank())
            {
                localNames.add(normalized);
            }
        }

        boolean friendsOnly = Configs.Generic.ACTIVE_DIGGERS_FRIENDS_ONLY.getBooleanValue();
        long now = System.currentTimeMillis();
        return ACTIVE_DIGGERS.entrySet().stream()
                .filter(entry -> !localNames.contains(entry.getKey()))
                .filter(entry -> isVisible(entry.getKey(), friendsOnly, now))
                .map(entry -> entry.getValue().displayName())
                .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
                .toList();
    }

    public static void clear()
    {
        ACTIVE_DIGGERS.clear();
        friendNames = Set.of();
    }

    private static Map<String, String> usernames(JsonObject event, String key)
    {
        Map<String, String> names = new ConcurrentHashMap<>();
        if (event == null || !event.has(key) || !event.get(key).isJsonArray())
        {
            return names;
        }
        JsonArray array = event.getAsJsonArray(key);
        for (JsonElement element : array)
        {
            if (!element.isJsonPrimitive())
            {
                continue;
            }
            String displayName = validUsername(element.getAsString());
            String username = normalizeUsername(displayName);
            if (!username.isBlank())
            {
                names.putIfAbsent(username, displayName);
            }
        }
        return names;
    }

    private static long ttlMs(JsonObject event)
    {
        if (event != null && event.has("expiresInSeconds"))
        {
            try
            {
                long seconds = event.get("expiresInSeconds").getAsLong();
                if (seconds >= 15L && seconds <= 300L)
                {
                    return seconds * 1_000L;
                }
            }
            catch (Exception ignored)
            {
            }
        }
        return DEFAULT_TTL_MS;
    }

    private static String stringValue(JsonObject event, String key)
    {
        if (event == null || !event.has(key) || !event.get(key).isJsonPrimitive())
        {
            return "";
        }
        try
        {
            return event.get(key).getAsString();
        }
        catch (Exception ignored)
        {
            return "";
        }
    }

    private static String normalizeUsername(String username)
    {
        return validUsername(username).toLowerCase(Locale.ROOT);
    }

    private static String validUsername(String username)
    {
        String value = username == null ? "" : username.trim();
        return MINECRAFT_USERNAME.matcher(value).matches() ? value : "";
    }

    private record ActiveDigger(String displayName, long expiresAt)
    {
    }
}
