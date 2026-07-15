package com.mmm.tags;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmm.util.UiFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class PlayerTagPayload
{
    private PlayerTagPayload()
    {
    }

    public static Map<String, PlayerTagData> parse(String body)
    {
        Map<String, PlayerTagData> tags = new HashMap<>();
        try
        {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("tags") || !root.get("tags").isJsonArray())
            {
                return Map.of();
            }

            for (JsonElement element : root.getAsJsonArray("tags"))
            {
                if (!element.isJsonObject())
                {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String username = stringValue(object, "username").trim();
                if (!isMinecraftUsername(username))
                {
                    continue;
                }
                long totalBlocks = Math.max(0L, longValue(object, "totalBlocks"));
                int color = parseRgb(stringValue(object, "color"));
                tags.put(normalize(username), new PlayerTagData(username, totalBlocks, color));
            }
        }
        catch (Exception ignored)
        {
            return Map.of();
        }
        return Map.copyOf(tags);
    }

    public static Map<String, PlayerTagData> parseLeaderboard(String body)
    {
        Map<String, PlayerTagData> tags = new HashMap<>();
        try
        {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("rows") || !root.get("rows").isJsonArray())
            {
                return Map.of();
            }

            for (JsonElement element : root.getAsJsonArray("rows"))
            {
                if (!element.isJsonObject())
                {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String username = stringValue(object, "username").trim();
                if (!isMinecraftUsername(username))
                {
                    continue;
                }
                long totalBlocks = Math.max(0L, object.has("blocksMined")
                        ? longValue(object, "blocksMined")
                        : longValue(object, "totalDigs"));
                int color = UiFormat.getBlocksMinedMilestoneColor(totalBlocks) & 0x00FFFFFF;
                tags.put(normalize(username), new PlayerTagData(username, totalBlocks, color));
            }
        }
        catch (Exception ignored)
        {
            return Map.of();
        }
        return Map.copyOf(tags);
    }

    public static boolean isTagPayload(String body)
    {
        return hasArray(body, "tags");
    }

    public static boolean isLeaderboardPayload(String body)
    {
        return hasArray(body, "rows");
    }

    public static String findKnownUsername(String displayedText, Collection<String> usernames)
    {
        String text = displayedText == null ? "" : displayedText;
        for (String username : usernames)
        {
            if (text.equalsIgnoreCase(username))
            {
                return username;
            }
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        return usernames.stream()
                .filter(PlayerTagPayload::isMinecraftUsername)
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .filter(username -> containsUsernameToken(lowerText, username.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse("");
    }

    public static String formatBlocks(long totalBlocks)
    {
        double compactValue = Math.max(0L, totalBlocks);
        String[] suffixes = {"", "k", "M", "B", "T"};
        int suffixIndex = 0;
        while (compactValue >= 1_000D && suffixIndex < suffixes.length - 1)
        {
            compactValue /= 1_000D;
            suffixIndex++;
        }

        if (suffixIndex == 0)
        {
            return Long.toString((long) compactValue);
        }

        String number = compactValue < 10D
                ? String.format(Locale.US, "%.1f", compactValue)
                : String.format(Locale.US, "%.0f", compactValue);
        if (number.endsWith(".0"))
        {
            number = number.substring(0, number.length() - 2);
        }
        return number + suffixes[suffixIndex];
    }

    public static String normalize(String username)
    {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsUsernameToken(String text, String username)
    {
        int from = 0;
        while (from <= text.length() - username.length())
        {
            int index = text.indexOf(username, from);
            if (index < 0)
            {
                return false;
            }
            int end = index + username.length();
            boolean leftBoundary = index == 0 || !isUsernameCharacter(text.charAt(index - 1));
            boolean rightBoundary = end == text.length() || !isUsernameCharacter(text.charAt(end));
            if (leftBoundary && rightBoundary)
            {
                return true;
            }
            from = index + 1;
        }
        return false;
    }

    private static boolean isUsernameCharacter(char value)
    {
        return value == '_' || Character.isLetterOrDigit(value);
    }

    private static boolean isMinecraftUsername(String username)
    {
        return username != null && username.matches("[A-Za-z0-9_]{1,16}");
    }

    private static boolean hasArray(String body, String key)
    {
        try
        {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            return root.has(key) && root.get(key).isJsonArray();
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private static int parseRgb(String value)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("#"))
        {
            normalized = normalized.substring(1);
        }
        try
        {
            return normalized.length() == 6 ? Integer.parseInt(normalized, 16) : 0xFFFFFF;
        }
        catch (NumberFormatException ignored)
        {
            return 0xFFFFFF;
        }
    }

    private static String stringValue(JsonObject object, String key)
    {
        try
        {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
        }
        catch (Exception ignored)
        {
            return "";
        }
    }

    private static long longValue(JsonObject object, String key)
    {
        try
        {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : 0L;
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }
}
