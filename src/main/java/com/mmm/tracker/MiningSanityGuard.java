package com.mmm.tracker;

import com.mmm.MMM;
import com.mmm.config.Configs;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class MiningSanityGuard
{
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final long RATE_LIMIT_LOG_INTERVAL_MS = 30_000L;
    private static final Set<BlockCoordinate> COUNTED_BLOCK_COORDINATES = new HashSet<>();
    private static final ArrayDeque<Long> ACCEPTED_BLOCK_TIMES = new ArrayDeque<>();

    private static String countedCoordinateWorldId = "";
    private static long worldDuplicateCoordinateRejects;
    private static long minuteCapRejects;
    private static long lastRateLimitLogMs;

    private MiningSanityGuard()
    {
    }

    public static void resetWorld(String worldId)
    {
        countedCoordinateWorldId = cleanScope(worldId);
        COUNTED_BLOCK_COORDINATES.clear();
        ACCEPTED_BLOCK_TIMES.clear();
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
            BlockCoordinate coordinate = new BlockCoordinate(cleanScope(dimensionId), pos.asLong());
            if (COUNTED_BLOCK_COORDINATES.add(coordinate) == false)
            {
                worldDuplicateCoordinateRejects++;
                return false;
            }
        }

        pruneOldAcceptedBlocks(now);

        int cap = Math.max(1, Configs.Generic.MAX_BLOCKS_PER_MINUTE.getIntegerValue());
        if (ACCEPTED_BLOCK_TIMES.size() >= cap)
        {
            minuteCapRejects++;
            logRateLimit(now, cap);
            return false;
        }

        ACCEPTED_BLOCK_TIMES.addLast(now);
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

    private static void pruneOldAcceptedBlocks(long now)
    {
        long cutoff = now - RATE_WINDOW_MS;
        while (ACCEPTED_BLOCK_TIMES.isEmpty() == false && ACCEPTED_BLOCK_TIMES.peekFirst() < cutoff)
        {
            ACCEPTED_BLOCK_TIMES.pollFirst();
        }
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

    private record BlockCoordinate(String dimensionId, long posKey)
    {
    }
}
