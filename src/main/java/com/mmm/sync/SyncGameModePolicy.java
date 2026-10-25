package com.mmm.sync;

import com.google.gson.JsonObject;

public final class SyncGameModePolicy {
    private SyncGameModePolicy() {}

    public static boolean allowed(String mode) {
        return "survival".equals(mode) || "adventure".equals(mode);
    }

    public static boolean allowed(JsonObject payload) {
        if (payload == null || !payload.has("game_mode") || !payload.get("game_mode").isJsonPrimitive()) return false;
        if (!allowed(payload.get("game_mode").getAsString())) return false;
        if (payload.has("world") && payload.get("world").isJsonObject()) {
            JsonObject world = payload.getAsJsonObject("world");
            if (world.has("game_mode") && (!world.get("game_mode").isJsonPrimitive() || !allowed(world.get("game_mode").getAsString()))) return false;
        }
        return true;
    }
}
