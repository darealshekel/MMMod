package com.mmm.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mmm.MMM;
import com.mmm.util.PeriodKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;

/** Cross-version, UUID-scoped local history of accepted valid block breaks. */
public final class MiningCalendarStore
{
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int MAX_DAYS = 400;
    private static final long SAVE_INTERVAL_MS = 5_000L;
    private static final Map<String, Long> DAYS = new LinkedHashMap<>();
    private static final Map<String, Long> SYNCED_DAYS = new LinkedHashMap<>();
    private static boolean loaded;
    private static boolean dirty;
    private static long lastSaveAtMs;
    private static String activePlayerKey = "";

    private MiningCalendarStore()
    {
    }

    public static synchronized void recordBlock(long now)
    {
        activateCurrentPlayer();
        String day = PeriodKeys.currentDailyKey(now);
        DAYS.merge(day, 1L, Long::sum);
        dirty = true;
        trimOldDays();
        if (now - lastSaveAtMs >= SAVE_INTERVAL_MS)
        {
            save();
        }
    }

    public static synchronized JsonArray pendingEntries()
    {
        activateCurrentPlayer();
        JsonArray result = new JsonArray();
        sortedDayKeys().forEach(day -> {
            long blocks = Math.max(0L, DAYS.getOrDefault(day, 0L));
            long synced = Math.max(0L, SYNCED_DAYS.getOrDefault(day, 0L));
            if (blocks <= synced)
            {
                return;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("date", day);
            entry.addProperty("blocks_mined", blocks);
            result.add(entry);
        });
        return result;
    }

    public static synchronized long currentDailyBlocks(long now)
    {
        activateCurrentPlayer();
        LocalDate currentDay = LocalDate.ofInstant(Instant.ofEpochMilli(now), PeriodKeys.UTC);
        return sumDaysWithin(DAYS, currentDay, currentDay);
    }

    public static synchronized long currentWeeklyBlocks(long now)
    {
        activateCurrentPlayer();
        LocalDate currentDay = LocalDate.ofInstant(Instant.ofEpochMilli(now), PeriodKeys.UTC);
        LocalDate weekStart = LocalDate.ofInstant(
                Instant.ofEpochMilli(PeriodKeys.currentWeeklyStartMs(now)),
                PeriodKeys.UTC);
        return sumDaysWithin(DAYS, weekStart, currentDay);
    }

    static long sumDaysWithin(Map<String, Long> days, LocalDate startInclusive, LocalDate endInclusive)
    {
        if (days == null || startInclusive == null || endInclusive == null || endInclusive.isBefore(startInclusive))
        {
            return 0L;
        }

        long total = 0L;
        for (Map.Entry<String, Long> entry : days.entrySet())
        {
            LocalDate day = parseDay(entry.getKey());
            if (day == null || day.isBefore(startInclusive) || day.isAfter(endInclusive))
            {
                continue;
            }

            long blocks = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
            if (Long.MAX_VALUE - total < blocks)
            {
                return Long.MAX_VALUE;
            }
            total += blocks;
        }
        return total;
    }

    public static synchronized void markPayloadSynced(JsonObject payload)
    {
        if (payload == null || payload.has("daily_mining") == false || payload.get("daily_mining").isJsonArray() == false)
        {
            return;
        }
        String payloadPlayerKey = stringValue(payload, "minecraft_uuid");
        if (payloadPlayerKey.isBlank())
        {
            activateCurrentPlayer();
        }
        else
        {
            activatePlayer(payloadPlayerKey);
        }

        for (JsonElement element : payload.getAsJsonArray("daily_mining"))
        {
            if (element == null || element.isJsonObject() == false)
            {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String day = stringValue(entry, "date");
            if (parseDay(day) == null || entry.has("blocks_mined") == false)
            {
                continue;
            }
            long sentBlocks;
            try
            {
                sentBlocks = Math.max(0L, entry.get("blocks_mined").getAsLong());
            }
            catch (Exception ignored)
            {
                continue;
            }
            long currentBlocks = Math.max(0L, DAYS.getOrDefault(day, 0L));
            long acknowledged = Math.min(sentBlocks, currentBlocks);
            SYNCED_DAYS.put(day, Math.max(SYNCED_DAYS.getOrDefault(day, 0L), acknowledged));
            dirty = true;
        }
        save();
    }

    public static synchronized void flush()
    {
        if (loaded == false)
        {
            activateCurrentPlayer();
        }
        save();
    }

    private static void activateCurrentPlayer()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerKey = client != null && client.player != null
                ? client.player.getUuidAsString()
                : activePlayerKey;
        activatePlayer(playerKey);
    }

    private static void activatePlayer(String playerKey)
    {
        String normalized = normalizePlayerKey(playerKey);
        if (loaded && normalized.equals(activePlayerKey))
        {
            return;
        }
        if (loaded)
        {
            save();
        }
        DAYS.clear();
        SYNCED_DAYS.clear();
        loaded = false;
        dirty = false;
        activePlayerKey = normalized;
        loadIfNeeded();
    }

    private static String normalizePlayerKey(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return normalized.isBlank() ? "unlinked" : normalized;
    }

    private static void loadIfNeeded()
    {
        if (loaded)
        {
            return;
        }
        loaded = true;
        Path path = SharedStoragePaths.miningCalendarFile(activePlayerKey);
        if (Files.exists(path) == false)
        {
            return;
        }

        try
        {
            AtomicJsonStorage.ReadResult result = AtomicJsonStorage.readObjectWithBackup(path);
            if (result.value() == null)
            {
                return;
            }
            JsonObject root = result.value();
            readDayMap(root.getAsJsonObject("days"), DAYS);
            readDayMap(root.getAsJsonObject("synced_days"), SYNCED_DAYS);
            trimOldDays();
            dirty = result.recoveredFromBackup();
            if (result.recoveredFromBackup())
            {
                MMM.LOGGER.warn("[MMM] Recovered mining calendar state from backup.");
                save();
            }
        }
        catch (Exception e)
        {
            DAYS.clear();
            SYNCED_DAYS.clear();
            MMM.LOGGER.warn("[MMM] Could not read mining calendar state: {}", e.getMessage());
        }
    }

    private static void readDayMap(JsonObject source, Map<String, Long> target)
    {
        if (source == null)
        {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : source.entrySet())
        {
            if (parseDay(entry.getKey()) == null)
            {
                continue;
            }
            try
            {
                target.put(entry.getKey(), Math.max(0L, entry.getValue().getAsLong()));
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private static void trimOldDays()
    {
        List<String> keys = sortedDayKeys();
        int removeCount = Math.max(0, keys.size() - MAX_DAYS);
        for (int index = 0; index < removeCount; index++)
        {
            String day = keys.get(index);
            DAYS.remove(day);
            SYNCED_DAYS.remove(day);
            dirty = true;
        }
        SYNCED_DAYS.keySet().removeIf(day -> DAYS.containsKey(day) == false);
    }

    private static List<String> sortedDayKeys()
    {
        List<String> keys = new ArrayList<>(DAYS.keySet());
        keys.removeIf(day -> parseDay(day) == null);
        keys.sort(Comparator.comparing(MiningCalendarStore::parseDay));
        return keys;
    }

    private static LocalDate parseDay(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value.trim(), DAY_FORMAT);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static String stringValue(JsonObject object, String key)
    {
        if (object == null || object.has(key) == false || object.get(key).isJsonPrimitive() == false)
        {
            return "";
        }
        try
        {
            return object.get(key).getAsString().trim();
        }
        catch (Exception ignored)
        {
            return "";
        }
    }

    private static void save()
    {
        if (dirty == false)
        {
            return;
        }

        Path path = SharedStoragePaths.miningCalendarFile(activePlayerKey);
        try
        {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("days", mapJson(DAYS));
            root.add("synced_days", mapJson(SYNCED_DAYS));
            AtomicJsonStorage.write(path, root, true);
            lastSaveAtMs = System.currentTimeMillis();
            dirty = false;
        }
        catch (Exception e)
        {
            MMM.LOGGER.warn("[MMM] Could not save mining calendar state: {}", e.getMessage());
        }
    }

    private static JsonObject mapJson(Map<String, Long> values)
    {
        JsonObject object = new JsonObject();
        for (String day : sortedDayKeys())
        {
            if (values.containsKey(day))
            {
                object.addProperty(day, Math.max(0L, values.get(day)));
            }
        }
        return object;
    }
}
