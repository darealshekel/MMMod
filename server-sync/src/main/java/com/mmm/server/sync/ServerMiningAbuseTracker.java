package com.mmm.server.sync;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class ServerMiningAbuseTracker
{
    static final long WINDOW_MILLIS = 60_000L;
    static final int MAX_BLOCKS_PER_MINUTE = 1_200;
    private static final int REPEATED_POSITION_THRESHOLD = 4;
    private static final int TINY_AREA_SAMPLE_THRESHOLD = 120;
    private static final int LOW_INPUT_SAMPLE_THRESHOLD = 300;

    private final Map<UUID, PlayerWindow> windows = new LinkedHashMap<>();

    Evidence record(
            UUID playerId,
            long timestampMillis,
            int blockX,
            int blockY,
            int blockZ,
            double playerX,
            double playerY,
            double playerZ,
            float yaw,
            float pitch)
    {
        PlayerWindow window = this.windows.computeIfAbsent(playerId, ignored -> new PlayerWindow());
        window.samples.addLast(new Sample(
                timestampMillis,
                blockX,
                blockY,
                blockZ,
                playerX,
                playerY,
                playerZ,
                yaw,
                pitch));
        long cutoff = timestampMillis - WINDOW_MILLIS;
        while (window.samples.isEmpty() == false && window.samples.peekFirst().timestampMillis < cutoff)
        {
            window.samples.removeFirst();
        }
        return inspect(window.samples);
    }

    private static Evidence inspect(Deque<Sample> samples)
    {
        if (samples.isEmpty())
        {
            return Evidence.empty();
        }

        Map<String, Integer> coordinateCounts = new LinkedHashMap<>();
        int minBlockX = Integer.MAX_VALUE;
        int minBlockY = Integer.MAX_VALUE;
        int minBlockZ = Integer.MAX_VALUE;
        int maxBlockX = Integer.MIN_VALUE;
        int maxBlockY = Integer.MIN_VALUE;
        int maxBlockZ = Integer.MIN_VALUE;
        double minPlayerX = Double.MAX_VALUE;
        double minPlayerY = Double.MAX_VALUE;
        double minPlayerZ = Double.MAX_VALUE;
        double maxPlayerX = -Double.MAX_VALUE;
        double maxPlayerY = -Double.MAX_VALUE;
        double maxPlayerZ = -Double.MAX_VALUE;
        float minYaw = Float.MAX_VALUE;
        float minPitch = Float.MAX_VALUE;
        float maxYaw = -Float.MAX_VALUE;
        float maxPitch = -Float.MAX_VALUE;

        for (Sample sample : samples)
        {
            coordinateCounts.merge(sample.blockX + ":" + sample.blockY + ":" + sample.blockZ, 1, Integer::sum);
            minBlockX = Math.min(minBlockX, sample.blockX);
            minBlockY = Math.min(minBlockY, sample.blockY);
            minBlockZ = Math.min(minBlockZ, sample.blockZ);
            maxBlockX = Math.max(maxBlockX, sample.blockX);
            maxBlockY = Math.max(maxBlockY, sample.blockY);
            maxBlockZ = Math.max(maxBlockZ, sample.blockZ);
            minPlayerX = Math.min(minPlayerX, sample.playerX);
            minPlayerY = Math.min(minPlayerY, sample.playerY);
            minPlayerZ = Math.min(minPlayerZ, sample.playerZ);
            maxPlayerX = Math.max(maxPlayerX, sample.playerX);
            maxPlayerY = Math.max(maxPlayerY, sample.playerY);
            maxPlayerZ = Math.max(maxPlayerZ, sample.playerZ);
            minYaw = Math.min(minYaw, sample.yaw);
            minPitch = Math.min(minPitch, sample.pitch);
            maxYaw = Math.max(maxYaw, sample.yaw);
            maxPitch = Math.max(maxPitch, sample.pitch);
        }

        int maxCoordinateHits = coordinateCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int blockSpanX = maxBlockX - minBlockX;
        int blockSpanY = maxBlockY - minBlockY;
        int blockSpanZ = maxBlockZ - minBlockZ;
        double movementRange = Math.max(maxPlayerX - minPlayerX, Math.max(maxPlayerY - minPlayerY, maxPlayerZ - minPlayerZ));
        float cameraRange = Math.max(maxYaw - minYaw, maxPitch - minPitch);

        Map<String, Long> flags = new LinkedHashMap<>();
        int score = 0;
        if (samples.size() > MAX_BLOCKS_PER_MINUTE)
        {
            flags.put("IMPOSSIBLE_BLOCK_RATE", (long) samples.size());
            score = Math.max(score, 100);
        }
        if (maxCoordinateHits >= REPEATED_POSITION_THRESHOLD)
        {
            flags.put("PLACE_AND_BREAK_PATTERN", (long) maxCoordinateHits);
            score = Math.max(score, 75);
        }
        if (samples.size() >= TINY_AREA_SAMPLE_THRESHOLD && blockSpanX <= 2 && blockSpanY <= 2 && blockSpanZ <= 2)
        {
            flags.put("REPEATED_BLOCK_CLUSTER", (long) samples.size());
            score = Math.max(score, 65);
        }
        if (samples.size() >= LOW_INPUT_SAMPLE_THRESHOLD && movementRange < 0.5D && cameraRange < 2.0F)
        {
            flags.put("AFK_MINING_PATTERN", (long) samples.size());
            score = Math.max(score, 70);
        }

        Map<String, Long> details = new LinkedHashMap<>();
        details.put("blocksInRollingMinute", (long) samples.size());
        details.put("maximumBreaksAtOneCoordinate", (long) maxCoordinateHits);
        details.put("blockSpanX", (long) blockSpanX);
        details.put("blockSpanY", (long) blockSpanY);
        details.put("blockSpanZ", (long) blockSpanZ);
        details.put("movementRangeMilliBlocks", Math.round(movementRange * 1_000.0D));
        details.put("cameraRangeMilliDegrees", (long) Math.round(cameraRange * 1_000.0F));
        return new Evidence(score, flags, details, samples.size());
    }

    record Evidence(int suspicionScore, Map<String, Long> flags, Map<String, Long> details, int blocksInWindow)
    {
        static Evidence empty()
        {
            return new Evidence(0, Map.of(), Map.of(), 0);
        }

        boolean suspicious()
        {
            return this.suspicionScore > 0 && this.flags.isEmpty() == false;
        }
    }

    private record Sample(
            long timestampMillis,
            int blockX,
            int blockY,
            int blockZ,
            double playerX,
            double playerY,
            double playerZ,
            float yaw,
            float pitch)
    {
    }

    private static final class PlayerWindow
    {
        private final Deque<Sample> samples = new ArrayDeque<>();
    }
}
