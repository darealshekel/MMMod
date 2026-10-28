package com.mmm.tracker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent per-account totals. Clock anchors are deliberately never persisted. */
public final class PlaytimeState
{
    private final Map<String, Long> worlds = new LinkedHashMap<>();
    private long modMillis;
    private long allWorldMillis;
    private String world = "";
    private long previousNanos;
    private long remainderNanos;
    private boolean playing;

    public synchronized void enter(String worldId, long nowNanos)
    {
        if (worldId.equals(world)) return;
        world = worldId;
        previousNanos = nowNanos;
        remainderNanos = 0;
        playing = false;
    }

    public synchronized void tick(long nowNanos, boolean active)
    {
        long deltaNanos = nowNanos - previousNanos;
        previousNanos = nowNanos;
        // Ignore suspension/loading gaps instead of crediting time when the game did not tick.
        if (playing && active && !world.isBlank() && deltaNanos > 0 && deltaNanos <= 5_000_000_000L)
        {
            long elapsed = (deltaNanos + remainderNanos) / 1_000_000L;
            remainderNanos = (deltaNanos + remainderNanos) % 1_000_000L;
            modMillis += elapsed;
            worlds.merge(world, elapsed, Long::sum);
            allWorldMillis += elapsed;
        }
        playing = active;
    }

    public synchronized void leave()
    {
        playing = false;
        world = "";
    }

    public synchronized void observeVanilla(long ticks)
    {
        if (world.isBlank() || ticks < 0) return;
        long existing = worldMillis();
        long observed = Math.max(existing, ticks * 50L);
        worlds.put(world, observed);
        allWorldMillis += observed - existing;
    }

    public synchronized long worldMillis() { return worlds.getOrDefault(world, 0L); }
    public synchronized long modMillis() { return modMillis; }
    public synchronized long allWorldMillis() { return allWorldMillis; }

    public synchronized JsonObject snapshot()
    {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("modMillis", modMillis);
        JsonObject totals = new JsonObject();
        worlds.forEach(totals::addProperty);
        json.add("worlds", totals);
        return json;
    }

    public static PlaytimeState restore(JsonObject json)
    {
        PlaytimeState state = new PlaytimeState();
        if (json == null) return state;
        if (json.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported playtime data version");
        state.modMillis = nonnegative(json.get("modMillis"));
        for (var entry : json.getAsJsonObject("worlds").entrySet())
        {
            long millis = nonnegative(entry.getValue());
            state.worlds.put(entry.getKey(), millis);
            state.allWorldMillis = Math.addExact(state.allWorldMillis, millis);
        }
        return state;
    }

    private static long nonnegative(JsonElement value)
    {
        long result = value.getAsBigDecimal().longValueExact();
        if (result < 0) throw new IllegalArgumentException("Negative playtime");
        return result;
    }
}
