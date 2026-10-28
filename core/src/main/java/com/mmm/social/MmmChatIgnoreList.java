package com.mmm.social;

import com.mmm.config.Configs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class MmmChatIgnoreList
{
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private MmmChatIgnoreList()
    {
    }

    public static boolean isValidUsername(String username)
    {
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static boolean isIgnored(String username)
    {
        if (!isValidUsername(username))
        {
            return false;
        }
        String key = key(username);
        return entries().stream().anyMatch(entry -> key(entry).equals(key));
    }

    public static boolean add(String username)
    {
        if (!isValidUsername(username) || isIgnored(username))
        {
            return false;
        }
        List<String> next = new ArrayList<>(entries());
        next.add(username.trim());
        persist(next);
        return true;
    }

    public static boolean remove(String username)
    {
        String requested = key(username);
        List<String> next = new ArrayList<>(entries());
        boolean removed = next.removeIf(entry -> key(entry).equals(requested));
        if (removed)
        {
            persist(next);
        }
        return removed;
    }

    public static List<String> entries()
    {
        return normalizeEntries(Configs.Generic.MMM_CHAT_IGNORED_PLAYERS.getStrings());
    }

    static List<String> normalizeEntries(List<String> values)
    {
        Map<String, String> unique = new LinkedHashMap<>();
        if (values != null)
        {
            for (String value : values)
            {
                if (isValidUsername(value))
                {
                    String trimmed = value.trim();
                    unique.putIfAbsent(key(trimmed), trimmed);
                }
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static void persist(List<String> entries)
    {
        Configs.Generic.MMM_CHAT_IGNORED_PLAYERS.setStrings(normalizeEntries(entries));
        Configs.saveToFile();
    }

    private static String key(String username)
    {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
