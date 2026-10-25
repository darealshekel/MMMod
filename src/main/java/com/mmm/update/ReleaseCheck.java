package com.mmm.update;

import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.util.Optional;

public final class ReleaseCheck
{
    private ReleaseCheck() {}

    public static Optional<String> newerVersion(String json, String minecraft, String installed)
    {
        String current = normalize(installed);
        if (current == null) return Optional.empty();
        String newest = current;
        var downloads = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("downloads");
        if (downloads == null) return Optional.empty();
        for (var entry : downloads)
        {
            if (!entry.isJsonObject()) continue;
            var release = entry.getAsJsonObject();
            if (!release.has("minecraftVersion") || !release.has("modVersion")) continue;
            if (!minecraft.equals(release.get("minecraftVersion").getAsString())) continue;
            String candidate = normalize(release.get("modVersion").getAsString());
            if (candidate != null && compare(candidate, newest) > 0) newest = candidate;
        }
        return newest.equals(current) ? Optional.empty() : Optional.of(newest);
    }

    private static String normalize(String version)
    {
        if (version == null || version.length() > 100) return null;
        String number = version.split("\\+", 2)[0];
        return number.matches("[0-9]+(?:\\.[0-9]+){1,5}") ? number : null;
    }

    private static int compare(String left, String right)
    {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++)
        {
            int difference = new BigInteger(i < a.length ? a[i] : "0")
                    .compareTo(new BigInteger(i < b.length ? b[i] : "0"));
            if (difference != 0) return difference;
        }
        return 0;
    }
}
