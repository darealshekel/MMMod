package com.mmm.tracker;

import com.mmm.MMM;
import com.mmm.config.Configs;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class MiningSanityGuard
{
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final long RATE_LIMIT_LOG_INTERVAL_MS = 30_000L;
    private static final int MAX_ACCEPTED_BREAKS_PER_COORDINATE = 3;
    private static final int MAX_TRACKED_COORDINATES = 250_000;
    private static final Map<String, Integer> DIMENSION_INDEXES = new HashMap<>();
    private static final List<Long2ByteOpenHashMap> COUNTED_BLOCK_COORDINATES = new ArrayList<>();
    private static final IntArrayFIFOQueue COORDINATE_DIMENSION_ORDER = new IntArrayFIFOQueue();
    private static final LongArrayFIFOQueue COORDINATE_POSITION_ORDER = new LongArrayFIFOQueue();
    private static final LongArrayFIFOQueue ACCEPTED_BLOCK_TIMES = new LongArrayFIFOQueue();

    private static String countedCoordinateWorldId = "";
    private static int trackedCoordinateCount;
    private static long worldDuplicateCoordinateRejects;
    private static long minuteCapRejects;
    private static long lastRateLimitLogMs;

    private MiningSanityGuard()
    {
    }

    public static void resetWorld(String worldId)
    {
        countedCoordinateWorldId = cleanScope(worldId);
        DIMENSION_INDEXES.clear();
        COUNTED_BLOCK_COORDINATES.clear();
        COORDINATE_DIMENSION_ORDER.clear();
        COORDINATE_POSITION_ORDER.clear();
        ACCEPTED_BLOCK_TIMES.clear();
        trackedCoordinateCount = 0;
        worldDuplicateCoordinateRejects = 0L;
        minuteCapRejects = 0L;
        lastRateLimitLogMs = 0L;
    }

    public static boolean shouldAcceptBlock(BlockPos pos, String worldId, String dimensionId, long now)
    {
        String normalizedWorldId = cleanScope(worldId);
        if (normalizedWorldId.equals(countedCoordinateWorldId) == false)
        {
            resetWorld(normalizedWorldId);
        }

        if (pos != null)
        {
            int dimensionIndex = getDimensionIndex(dimensionId);
            Long2ByteOpenHashMap coordinates = COUNTED_BLOCK_COORDINATES.get(dimensionIndex);
            long positionKey = pos.asLong();
            int previousBreaks = coordinates.get(positionKey);
            if (previousBreaks >= MAX_ACCEPTED_BREAKS_PER_COORDINATE)
            {
                worldDuplicateCoordinateRejects++;
                return false;
            }

            if (previousBreaks == 0)
            {
                COORDINATE_DIMENSION_ORDER.enqueue(dimensionIndex);
                COORDINATE_POSITION_ORDER.enqueue(positionKey);
                trackedCoordinateCount++;
            }
            coordinates.put(positionKey, (byte) (previousBreaks + 1));
            trimOldCoordinates();
        }

        pruneOldAcceptedBlocks(now);

        int cap = Math.max(1, Configs.Generic.MAX_BLOCKS_PER_MINUTE.getIntegerValue());
        if (ACCEPTED_BLOCK_TIMES.size() >= cap)
        {
            minuteCapRejects++;
            logRateLimit(now, cap);
            return false;
        }

        ACCEPTED_BLOCK_TIMES.enqueue(now);
        return true;
    }

    public static long getWorldDuplicateCoordinateRejects()
    {
        return worldDuplicateCoordinateRejects;
    }

    public static long getMinuteCapRejects()
    {
        return minuteCapRejects;
    }

    private static void trimOldCoordinates()
    {
        while (trackedCoordinateCount > MAX_TRACKED_COORDINATES)
        {
            int dimensionIndex = COORDINATE_DIMENSION_ORDER.dequeueInt();
            long positionKey = COORDINATE_POSITION_ORDER.dequeueLong();
            COUNTED_BLOCK_COORDINATES.get(dimensionIndex).remove(positionKey);
            trackedCoordinateCount--;
        }
    }

    private static void pruneOldAcceptedBlocks(long now)
    {
        long cutoff = now - RATE_WINDOW_MS;
        while (ACCEPTED_BLOCK_TIMES.isEmpty() == false && ACCEPTED_BLOCK_TIMES.firstLong() < cutoff)
        {
            ACCEPTED_BLOCK_TIMES.dequeueLong();
        }
    }

    private static int getDimensionIndex(String dimensionId)
    {
        String dimension = cleanScope(dimensionId);
        Integer existing = DIMENSION_INDEXES.get(dimension);
        if (existing != null)
        {
            return existing;
        }

        int index = COUNTED_BLOCK_COORDINATES.size();
        Long2ByteOpenHashMap coordinates = new Long2ByteOpenHashMap();
        coordinates.defaultReturnValue((byte) 0);
        COUNTED_BLOCK_COORDINATES.add(coordinates);
        DIMENSION_INDEXES.put(dimension, index);
        return index;
    }

    private static void logRateLimit(long now, int cap)
    {
        if (now - lastRateLimitLogMs < RATE_LIMIT_LOG_INTERVAL_MS)
        {
            return;
        }

        lastRateLimitLogMs = now;
        MMM.LOGGER.warn(
                "[MMM] Local mining sanity cap exceeded: {} valid block breaks in the last minute, cap={}. Excess local block counts are ignored.",
                ACCEPTED_BLOCK_TIMES.size(),
                cap);
    }

    private static String cleanScope(String value)
    {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }

}
