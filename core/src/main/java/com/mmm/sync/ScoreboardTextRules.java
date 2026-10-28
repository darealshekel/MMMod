package com.mmm.sync;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScoreboardTextRules
{
    private static final Pattern USERNAME_PATTERN = Pattern.compile("(?i)(?:^|\\s|[#>\\[(])([A-Za-z0-9_]{3,16})(?:$|\\s|[\\])<:,.-])");

    private ScoreboardTextRules()
    {
    }

    static boolean containsStandaloneMarker(String value, String marker)
    {
        String text = clean(value).toLowerCase(Locale.ROOT);
        String expected = clean(marker).toLowerCase(Locale.ROOT);
        if (text.isBlank() || expected.isBlank())
        {
            return false;
        }

        Pattern pattern = Pattern.compile(
                "(?<![a-z0-9_])" + Pattern.quote(expected) + "(?![a-z0-9_])",
                Pattern.CASE_INSENSITIVE);
        return pattern.matcher(text).find();
    }

    static boolean containsAnyStandaloneMarker(String value, List<String> markers)
    {
        if (markers == null || markers.isEmpty())
        {
            return false;
        }

        for (String marker : markers)
        {
            if (containsStandaloneMarker(value, marker))
            {
                return true;
            }
        }
        return false;
    }

    static String extractUsername(String owner, String cleaned)
    {
        if (isMinecraftUsername(owner))
        {
            return owner;
        }

        Matcher matcher = USERNAME_PATTERN.matcher(clean(cleaned));
        while (matcher.find())
        {
            String candidate = matcher.group(1);
            if (isMinecraftUsername(candidate))
            {
                return candidate;
            }
        }
        return null;
    }

    static boolean isMinecraftUsername(String value)
    {
        if (value == null || value.length() < 3 || value.length() > 16 || value.matches("[A-Za-z0-9_]+") == false)
        {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("total") == false
                && lower.equals("player") == false
                && lower.equals("you") == false
                && lower.equals("your") == false
                && lower.equals("me") == false
                && lower.equals("self") == false
                && lower.equals("digs") == false
                && lower.equals("dug") == false
                && lower.equals("rank") == false;
    }

    private static String clean(String value)
    {
        return value == null
                ? ""
                : value
                        .replaceAll("§.", "")
                        .replace('\u00A0', ' ')
                        .replaceAll("\\s+", " ")
                        .trim();
    }
}
