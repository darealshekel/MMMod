package com.mmm.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.util.Optional;

public final class ReleaseCheck {
    private ReleaseCheck() {}

    public static Optional<String> newerVersion(String json, String minecraft, String installed) {
        String current = normalize(installed == null ? null : installed.replaceFirst("-dev(?=\\+|$)", ""));
        if (current == null || json == null || json.length() > 1_048_576) return Optional.empty();
        String newest = current;
        try {
            var root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return Optional.empty();
            var downloads = root.getAsJsonObject().get("downloads");
            if (downloads == null || !downloads.isJsonArray()) return Optional.empty();
            for (JsonElement entry : downloads.getAsJsonArray()) {
                if (!entry.isJsonObject()) continue;
                var release = entry.getAsJsonObject();
                var game = release.get("minecraftVersion");
                var mod = release.get("modVersion");
                if (game == null || !game.isJsonPrimitive() || !game.getAsJsonPrimitive().isString()
                        || mod == null || !mod.isJsonPrimitive() || !mod.getAsJsonPrimitive().isString()
                        || !minecraft.equals(game.getAsString())) continue;
                String candidate = normalize(mod.getAsString());
                if (candidate != null && compare(candidate, newest) > 0) newest = candidate;
            }
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
        return newest.equals(current) ? Optional.empty() : Optional.of(newest);
    }

    private static String normalize(String version) {
        if (version == null || version.length() > 100) return null;
        String number = version.split("\\+", 2)[0];
        return number.matches("[0-9]+(?:\\.[0-9]+){1,5}") ? number : null;
    }

    private static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int result = new BigInteger(i < a.length ? a[i] : "0").compareTo(new BigInteger(i < b.length ? b[i] : "0"));
            if (result != 0) return result;
        }
        return 0;
    }
}
