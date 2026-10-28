package com.mmm.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Personal state only. Credentials and shared UI preferences never enter this map. */
final class PlayerStatsAccounts {
    private static final List<String> NUMBERS = List.of("dailyProgress", "dailyGoalLastResetMs", "dailyBlocksMined",
            "weeklyBlocksMined", "weeklyLastResetMs", "personalRecordDailyBlocks", "personalRecordWeeklyBlocks",
            "fastest100kMs", "fastest100kStartedAtMs", "fastest100kFinishedAtMs", "totalBlocksMined");
    private static final List<String> STRINGS = List.of("dailyBlocksDate", "weeklyBlocksWeek", "activeProjectId");
    private static final List<String> ARRAYS = List.of("projects", "worldStats");

    private PlayerStatsAccounts() {}

    static JsonObject capture(JsonObject state) {
        JsonObject personal = new JsonObject();
        for (String key : NUMBERS) personal.addProperty(key, 0L);
        for (String key : STRINGS) personal.addProperty(key, "");
        for (String key : ARRAYS) personal.add(key, new JsonArray());
        for (String key : List.copyOf(personal.keySet()))
            if (state.has(key)) personal.add(key, state.get(key).deepCopy());
        return personal;
    }

    static JsonObject select(JsonObject accounts, String previousOwner, JsonObject current, String nextOwner) {
        // Ambiguous old data remains recoverable, but is not attributed to a new account.
        accounts.add(previousOwner.isEmpty() ? "unattributed" : previousOwner, capture(current));
        JsonObject selected = accounts.has(nextOwner) && accounts.get(nextOwner).isJsonObject()
                ? accounts.getAsJsonObject(nextOwner) : new JsonObject();
        JsonObject result = current.deepCopy();
        capture(selected).entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        return result;
    }
}
