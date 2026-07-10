package com.mmm.timer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.storage.SharedStoragePaths;
import com.mmm.tracker.MiningStats;
import com.mmm.util.BlockBreakdownCatalog;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;

public final class MmmTimerState
{
    public static final long MAX_DURATION_MS = 24L * 60L * 60L * 1000L;
    public static final long DEFAULT_DURATION_MS = MAX_DURATION_MS;
    private static final long MIN_DURATION_MS = 1000L;
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final long SAVE_INTERVAL_MS = 5_000L;
    private static final long NOTIFICATION_VISIBLE_MS = 5_000L;

    private static long durationMs = DEFAULT_DURATION_MS;
    private static long remainingPausedMs = DEFAULT_DURATION_MS;
    private static long startedAtMs;
    private static boolean running;
    private static boolean expired;
    private static boolean creditsPending;
    private static long lastSaveMs;
    private static long runStartedAtMs;
    private static long hourStartTimeMs;
    private static long blocksBroken;
    private static long currentHourBlocks;
    private static long bestHourBlocks;
    private static long lastHourBlocks;
    private static int completedHour;
    private static long notificationAtMs;
    private static long notificationBlocks;
    private static double notificationBlocksPerMinute;
    private static int notificationHour;
    private static final Map<String, Long> blockTypeCounts = new LinkedHashMap<>();

    private MmmTimerState()
    {
    }

    public static void load()
    {
        Path file = stateFile();
        if (Files.isRegularFile(file) == false)
        {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file))
        {
            properties.load(input);
        }
        catch (IOException exception)
        {
            MMM.LOGGER.warn("[MMM] Failed to load timer state from {}: {}", file, exception.getMessage());
            return;
        }

        durationMs = clampDuration(readLong(properties, "durationMs", DEFAULT_DURATION_MS));
        remainingPausedMs = clampDuration(readLong(properties, "remainingPausedMs", durationMs));
        startedAtMs = Math.max(0L, readLong(properties, "startedAtMs", 0L));
        running = Boolean.parseBoolean(properties.getProperty("running", "false"));
        expired = Boolean.parseBoolean(properties.getProperty("expired", "false"));
        runStartedAtMs = Math.max(0L, readLong(properties, "runStartedAtMs", 0L));
        hourStartTimeMs = Math.max(0L, readLong(properties, "hourStartTimeMs", readLong(properties, "hourStartTime", runStartedAtMs)));
        blocksBroken = Math.max(0L, readLong(properties, "blocksBroken", 0L));
        currentHourBlocks = Math.max(0L, readLong(properties, "currentHourBlocks", 0L));
        bestHourBlocks = Math.max(0L, readLong(properties, "bestHourBlocks", 0L));
        lastHourBlocks = Math.max(0L, readLong(properties, "lastHourBlocks", 0L));
        completedHour = Math.max(0, (int) readLong(properties, "completedHour", 0L));
        notificationAtMs = Math.max(0L, readLong(properties, "notificationAtMs", 0L));
        notificationBlocks = Math.max(0L, readLong(properties, "notificationBlocks", 0L));
        notificationBlocksPerMinute = Math.max(0D, readDouble(properties, "notificationBlocksPerMinute", 0D));
        notificationHour = Math.max(0, (int) readLong(properties, "notificationHour", 0L));
        readLayout(properties);

        blockTypeCounts.clear();
        for (String key : properties.stringPropertyNames())
        {
            if (key.startsWith("block."))
            {
                String blockId = key.substring("block.".length()).trim().toLowerCase(Locale.ROOT);
                long count = Math.max(0L, readLong(properties, key, 0L));
                if (count > 0L && BlockBreakdownCatalog.isValid(blockId))
                {
                    blockTypeCounts.put(blockId, count);
                }
            }
        }

        if (running)
        {
            long remaining = getRemainingMs(System.currentTimeMillis());
            if (remaining <= 0L)
            {
                running = false;
                expired = true;
                remainingPausedMs = 0L;
            }
        }
    }

    public static void save()
    {
        Path file = stateFile();
        try
        {
            Files.createDirectories(file.getParent());
        }
        catch (IOException exception)
        {
            MMM.LOGGER.warn("[MMM] Failed to create timer state directory {}: {}", file.getParent(), exception.getMessage());
            return;
        }

        Properties properties = new Properties();
        long now = System.currentTimeMillis();
        properties.setProperty("durationMs", String.valueOf(durationMs));
        properties.setProperty("remainingPausedMs", String.valueOf(running ? getRemainingMs(now) : remainingPausedMs));
        properties.setProperty("startedAtMs", String.valueOf(startedAtMs));
        properties.setProperty("running", String.valueOf(running));
        properties.setProperty("expired", String.valueOf(expired));
        properties.setProperty("runStartedAtMs", String.valueOf(runStartedAtMs));
        properties.setProperty("hourStartTimeMs", String.valueOf(hourStartTimeMs));
        properties.setProperty("blocksBroken", String.valueOf(blocksBroken));
        properties.setProperty("currentHourBlocks", String.valueOf(currentHourBlocks));
        properties.setProperty("bestHourBlocks", String.valueOf(bestHourBlocks));
        properties.setProperty("lastHourBlocks", String.valueOf(lastHourBlocks));
        properties.setProperty("completedHour", String.valueOf(completedHour));
        properties.setProperty("notificationAtMs", String.valueOf(notificationAtMs));
        properties.setProperty("notificationBlocks", String.valueOf(notificationBlocks));
        properties.setProperty("notificationBlocksPerMinute", String.valueOf(notificationBlocksPerMinute));
        properties.setProperty("notificationHour", String.valueOf(notificationHour));
        writeLayout(properties);

        blockTypeCounts.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> properties.setProperty("block." + entry.getKey(), String.valueOf(entry.getValue())));

        try (OutputStream output = Files.newOutputStream(file))
        {
            properties.store(output, "MMM timer state");
            lastSaveMs = now;
        }
        catch (IOException exception)
        {
            MMM.LOGGER.warn("[MMM] Failed to save timer state to {}: {}", file, exception.getMessage());
        }
    }

    public static void start(Long requestedDurationMs)
    {
        Configs.Generic.TIMER_HUD_VISIBLE.setBooleanValue(true);
        if (requestedDurationMs != null)
        {
            setDuration(requestedDurationMs);
            resetRunStats();
        }
        else if (expired || remainingPausedMs <= 0L)
        {
            resetRunStats();
        }

        long now = System.currentTimeMillis();
        long remaining = Math.max(MIN_DURATION_MS, Math.min(durationMs, remainingPausedMs > 0L ? remainingPausedMs : durationMs));
        resetRunStats();
        startedAtMs = now - (durationMs - remaining);
        running = true;
        expired = false;
        creditsPending = false;
        runStartedAtMs = now;

        if (MiningStats.isSessionActive() == false)
        {
            MiningStats.startNewSession();
        }
        else if (MiningStats.isSessionPaused())
        {
            MiningStats.togglePauseSession();
        }
        Configs.Generic.TIMER_HUD_VISIBLE.setBooleanValue(true);
        Configs.saveToFile();
        save();
    }

    public static void stop()
    {
        if (running)
        {
            remainingPausedMs = getRemainingMs(System.currentTimeMillis());
            running = false;
        }
        if (MiningStats.isSessionActive() && MiningStats.isSessionPaused() == false)
        {
            MiningStats.togglePauseSession();
        }
        Configs.Generic.TIMER_HUD_VISIBLE.setBooleanValue(false);
        Configs.saveToFile();
        save();
    }

    public static void reset()
    {
        running = false;
        expired = false;
        remainingPausedMs = durationMs;
        startedAtMs = 0L;
        resetRunStats();
        Configs.Generic.TIMER_HUD_VISIBLE.setBooleanValue(false);
        Configs.saveToFile();
        save();
    }

    public static void setDuration(long requestedDurationMs)
    {
        durationMs = clampDuration(requestedDurationMs);
        running = false;
        expired = false;
        remainingPausedMs = durationMs;
        startedAtMs = 0L;
        Configs.Generic.TIMER_HUD_VISIBLE.setBooleanValue(false);
        Configs.saveToFile();
        save();
    }

    public static void onClientTick(MinecraftClient client)
    {
        if (client == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (running)
        {
            long remaining = getRemainingMs(now);
            updateHourBoundaries(now);
            if (remaining <= 0L)
            {
                running = false;
                expired = true;
                remainingPausedMs = 0L;
                lastHourBlocks = currentHourBlocks;
                bestHourBlocks = Math.max(bestHourBlocks, currentHourBlocks);
                currentHourBlocks = 0L;
                if (Configs.Generic.TIMER_CREDITS.getBooleanValue())
                {
                    creditsPending = true;
                }
                save();
            }
        }
        else if (MiningStats.isSessionActive() && MiningStats.isSessionPaused() == false)
        {
            updateHourBoundaries(now);
        }

        if (now - lastSaveMs >= SAVE_INTERVAL_MS)
        {
            save();
        }
    }

    public static void onBlockMined(Block block)
    {
        if (BlockBreakdownCatalog.isValid(block) == false)
        {
            return;
        }

        if (expired && running == false && MiningStats.isSessionActive() && MiningStats.isSessionPaused() == false)
        {
            clearExpiredTimerForSessionStats();
        }

        if (shouldCountBlockStats() == false)
        {
            return;
        }

        if (hourStartTimeMs <= 0L)
        {
            hourStartTimeMs = System.currentTimeMillis();
        }
        blocksBroken++;
        currentHourBlocks++;
        bestHourBlocks = Math.max(bestHourBlocks, currentHourBlocks);
        blockTypeCounts.merge(BlockBreakdownCatalog.blockId(block), 1L, Long::sum);
    }

    public static void onSessionStarted()
    {
        if (running == false)
        {
            if (expired || remainingPausedMs <= 0L)
            {
                clearExpiredTimerForSessionStats();
            }
            else
            {
                resetRunStats();
            }
            save();
        }
    }

    public static boolean consumeCreditsPending()
    {
        boolean pending = creditsPending;
        creditsPending = false;
        return pending;
    }

    public static boolean hasNotification(long now)
    {
        return Configs.Generic.TIMER_NOTIFICATIONS.getBooleanValue()
                && notificationAtMs > 0L
                && now - notificationAtMs < NOTIFICATION_VISIBLE_MS;
    }

    public static void dismissCredits()
    {
        creditsPending = false;
    }

    public static long getDurationMs()
    {
        return durationMs;
    }

    public static long getRemainingMs()
    {
        return getRemainingMs(System.currentTimeMillis());
    }

    public static boolean isRunning()
    {
        return running;
    }

    public static boolean isExpired()
    {
        return expired;
    }

    public static boolean isTimerDisplayActive()
    {
        return Configs.Generic.TIMER_HUD_VISIBLE.getBooleanValue()
                && (running || expired || Math.max(0L, remainingPausedMs) < Math.max(1L, durationMs));
    }

    public static boolean isTimerBlockStatsMode()
    {
        return running;
    }

    public static long getBlocksBroken()
    {
        return Math.max(0L, blocksBroken);
    }

    public static long getCurrentHourBlocks()
    {
        return Math.max(0L, currentHourBlocks);
    }

    public static long getBestHourBlocks()
    {
        return Math.max(bestHourBlocks, currentHourBlocks);
    }

    public static long getLastHourBlocks()
    {
        return Math.max(0L, lastHourBlocks);
    }

    public static double getBlocksPerMinute()
    {
        if (hourStartTimeMs == 0L)
        {
            return 0D;
        }
        long elapsedMs = System.currentTimeMillis() - hourStartTimeMs;
        if (elapsedMs <= 0L)
        {
            return 0D;
        }
        float minutes = elapsedMs / (60F * 1000F);
        if (minutes < 0.5F)
        {
            return 0D;
        }
        return Math.max(0D, currentHourBlocks / minutes);
    }

    public static long getEstimatedBlocksPerHour()
    {
        return Math.max(0L, (long) (getBlocksPerMinute() * 60D));
    }

    public static long getNotificationBlocks()
    {
        return notificationBlocks;
    }

    public static double getNotificationBlocksPerMinute()
    {
        return notificationBlocksPerMinute;
    }

    public static int getNotificationHour()
    {
        return notificationHour;
    }

    public static List<BlockCount> getTopBlocks()
    {
        List<BlockCount> entries = new ArrayList<>();
        blockTypeCounts.forEach((id, count) -> {
            if (count != null && count > 0L && BlockBreakdownCatalog.isValid(id))
            {
                entries.add(new BlockCount(id, count));
            }
        });
        entries.sort(Comparator.comparingLong(BlockCount::count).reversed().thenComparing(BlockCount::id));
        return entries;
    }

    public static String formatTime(long ms)
    {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static long parseDurationMs(String input)
    {
        if (input == null || input.isBlank())
        {
            return DEFAULT_DURATION_MS;
        }

        String value = input.trim().toLowerCase(Locale.ROOT);
        long multiplier = 60_000L;
        if (value.endsWith("h"))
        {
            multiplier = HOUR_MS;
            value = value.substring(0, value.length() - 1);
        }
        else if (value.endsWith("m"))
        {
            multiplier = 60_000L;
            value = value.substring(0, value.length() - 1);
        }
        else if (value.endsWith("s"))
        {
            multiplier = 1000L;
            value = value.substring(0, value.length() - 1);
        }

        double amount = Double.parseDouble(value);
        return clampDuration(Math.round(amount * multiplier));
    }

    private static long getRemainingMs(long now)
    {
        if (running == false)
        {
            return Math.max(0L, remainingPausedMs);
        }
        long elapsed = Math.max(0L, now - startedAtMs);
        return Math.max(0L, durationMs - elapsed);
    }

    private static long getElapsedMs(long now)
    {
        if (running)
        {
            return Math.max(0L, Math.min(durationMs, now - startedAtMs));
        }
        if (MiningStats.isSessionActive())
        {
            return Math.max(0L, now - runStartedAtMs);
        }
        return Math.max(0L, durationMs - Math.max(0L, remainingPausedMs));
    }

    private static void updateHourBoundaries(long now)
    {
        if (hourStartTimeMs <= 0L)
        {
            hourStartTimeMs = now;
            currentHourBlocks = 0L;
            return;
        }
        long elapsed = getElapsedMs(now);
        int nextCompletedHour = (int) Math.min(23L, elapsed / HOUR_MS);
        while (completedHour < nextCompletedHour)
        {
            completedHour++;
            lastHourBlocks = currentHourBlocks;
            bestHourBlocks = Math.max(bestHourBlocks, currentHourBlocks);
            if (currentHourBlocks > 0L && Configs.Generic.TIMER_NOTIFICATIONS.getBooleanValue())
            {
                notificationAtMs = now;
                notificationBlocks = currentHourBlocks;
                notificationBlocksPerMinute = currentHourBlocks / 60D;
                notificationHour = completedHour;
            }
            currentHourBlocks = 0L;
            hourStartTimeMs = now;
        }
    }

    private static void resetRunStats()
    {
        long now = System.currentTimeMillis();
        runStartedAtMs = now;
        hourStartTimeMs = now;
        blocksBroken = 0L;
        currentHourBlocks = 0L;
        bestHourBlocks = 0L;
        lastHourBlocks = 0L;
        completedHour = 0;
        notificationAtMs = 0L;
        notificationBlocks = 0L;
        notificationBlocksPerMinute = 0D;
        notificationHour = 0;
        blockTypeCounts.clear();
        creditsPending = false;
    }

    private static void clearExpiredTimerForSessionStats()
    {
        expired = false;
        remainingPausedMs = durationMs;
        startedAtMs = 0L;
        resetRunStats();
    }

    private static boolean shouldCountBlockStats()
    {
        if (expired)
        {
            return false;
        }
        if (running)
        {
            return true;
        }
        return MiningStats.isSessionActive() && MiningStats.isSessionPaused() == false;
    }

    private static long clampDuration(long value)
    {
        return Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, value));
    }

    private static Path stateFile()
    {
        return SharedStoragePaths.root().resolve("mmm").resolve("state.properties");
    }

    private static long readLong(Properties properties, String key, long fallback)
    {
        try
        {
            return Long.parseLong(properties.getProperty(key, String.valueOf(fallback)).trim());
        }
        catch (RuntimeException exception)
        {
            return fallback;
        }
    }

    private static double readDouble(Properties properties, String key, double fallback)
    {
        try
        {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)).trim());
        }
        catch (RuntimeException exception)
        {
            return fallback;
        }
    }

    private static void readLayout(Properties properties)
    {
        Configs.Generic.TIMER_HUD_VISIBLE.setBooleanValue(Boolean.parseBoolean(properties.getProperty("timerHudVisible", String.valueOf(Configs.Generic.TIMER_HUD_VISIBLE.getBooleanValue()))));
        Configs.Generic.HOURLY_STATS_VISIBLE.setBooleanValue(Boolean.parseBoolean(properties.getProperty("hourlyStatsVisible", String.valueOf(Configs.Generic.HOURLY_STATS_VISIBLE.getBooleanValue()))));
        Configs.Generic.BLOCK_STATS_VISIBLE.setBooleanValue(Boolean.parseBoolean(properties.getProperty("blockStatsVisible", String.valueOf(Configs.Generic.BLOCK_STATS_VISIBLE.getBooleanValue()))));
        Configs.Generic.BLOCK_STATS_STATIC.setBooleanValue(Boolean.parseBoolean(properties.getProperty("blockStatsStatic", String.valueOf(Configs.Generic.BLOCK_STATS_STATIC.getBooleanValue()))));
        Configs.Generic.BLOCK_STATS_ICONS.setBooleanValue(Boolean.parseBoolean(properties.getProperty("blockStatsIcons", String.valueOf(Configs.Generic.BLOCK_STATS_ICONS.getBooleanValue()))));
        Configs.Generic.TIMER_NOTIFICATIONS.setBooleanValue(Boolean.parseBoolean(properties.getProperty("timerNotifications", String.valueOf(Configs.Generic.TIMER_NOTIFICATIONS.getBooleanValue()))));
        Configs.Generic.TIMER_CREDITS.setBooleanValue(Boolean.parseBoolean(properties.getProperty("timerCredits", String.valueOf(Configs.Generic.TIMER_CREDITS.getBooleanValue()))));
        Configs.Generic.TIMER_HUD_X.setIntegerValue((int) readLong(properties, "timerHudX", Configs.Generic.TIMER_HUD_X.getIntegerValue()));
        Configs.Generic.TIMER_HUD_Y.setIntegerValue((int) readLong(properties, "timerHudY", Configs.Generic.TIMER_HUD_Y.getIntegerValue()));
        Configs.Generic.TIMER_HUD_SCALE.setDoubleValue(readDouble(properties, "timerHudScale", Configs.Generic.TIMER_HUD_SCALE.getDoubleValue()));
        Configs.Generic.HOURLY_STATS_X.setIntegerValue((int) readLong(properties, "hourlyStatsX", Configs.Generic.HOURLY_STATS_X.getIntegerValue()));
        Configs.Generic.HOURLY_STATS_Y.setIntegerValue((int) readLong(properties, "hourlyStatsY", Configs.Generic.HOURLY_STATS_Y.getIntegerValue()));
        Configs.Generic.HOURLY_STATS_SCALE.setDoubleValue(readDouble(properties, "hourlyStatsScale", Configs.Generic.HOURLY_STATS_SCALE.getDoubleValue()));
        Configs.Generic.BLOCK_STATS_X.setIntegerValue((int) readLong(properties, "blockStatsX", Configs.Generic.BLOCK_STATS_X.getIntegerValue()));
        Configs.Generic.BLOCK_STATS_Y.setIntegerValue((int) readLong(properties, "blockStatsY", Configs.Generic.BLOCK_STATS_Y.getIntegerValue()));
        Configs.Generic.BLOCK_STATS_SCALE.setDoubleValue(readDouble(properties, "blockStatsScale", Configs.Generic.BLOCK_STATS_SCALE.getDoubleValue()));
        Configs.Generic.TIMER_NOTIFICATION_X.setIntegerValue((int) readLong(properties, "timerNotificationX", Configs.Generic.TIMER_NOTIFICATION_X.getIntegerValue()));
        Configs.Generic.TIMER_NOTIFICATION_Y.setIntegerValue((int) readLong(properties, "timerNotificationY", Configs.Generic.TIMER_NOTIFICATION_Y.getIntegerValue()));
        Configs.Generic.TIMER_NOTIFICATION_SCALE.setDoubleValue(readDouble(properties, "timerNotificationScale", Configs.Generic.TIMER_NOTIFICATION_SCALE.getDoubleValue()));
    }

    private static void writeLayout(Properties properties)
    {
        properties.setProperty("timerHudVisible", String.valueOf(Configs.Generic.TIMER_HUD_VISIBLE.getBooleanValue()));
        properties.setProperty("hourlyStatsVisible", String.valueOf(Configs.Generic.HOURLY_STATS_VISIBLE.getBooleanValue()));
        properties.setProperty("blockStatsVisible", String.valueOf(Configs.Generic.BLOCK_STATS_VISIBLE.getBooleanValue()));
        properties.setProperty("blockStatsStatic", String.valueOf(Configs.Generic.BLOCK_STATS_STATIC.getBooleanValue()));
        properties.setProperty("blockStatsIcons", String.valueOf(Configs.Generic.BLOCK_STATS_ICONS.getBooleanValue()));
        properties.setProperty("timerNotifications", String.valueOf(Configs.Generic.TIMER_NOTIFICATIONS.getBooleanValue()));
        properties.setProperty("timerCredits", String.valueOf(Configs.Generic.TIMER_CREDITS.getBooleanValue()));
        properties.setProperty("timerHudX", String.valueOf(Configs.Generic.TIMER_HUD_X.getIntegerValue()));
        properties.setProperty("timerHudY", String.valueOf(Configs.Generic.TIMER_HUD_Y.getIntegerValue()));
        properties.setProperty("timerHudScale", String.valueOf(Configs.Generic.TIMER_HUD_SCALE.getDoubleValue()));
        properties.setProperty("hourlyStatsX", String.valueOf(Configs.Generic.HOURLY_STATS_X.getIntegerValue()));
        properties.setProperty("hourlyStatsY", String.valueOf(Configs.Generic.HOURLY_STATS_Y.getIntegerValue()));
        properties.setProperty("hourlyStatsScale", String.valueOf(Configs.Generic.HOURLY_STATS_SCALE.getDoubleValue()));
        properties.setProperty("blockStatsX", String.valueOf(Configs.Generic.BLOCK_STATS_X.getIntegerValue()));
        properties.setProperty("blockStatsY", String.valueOf(Configs.Generic.BLOCK_STATS_Y.getIntegerValue()));
        properties.setProperty("blockStatsScale", String.valueOf(Configs.Generic.BLOCK_STATS_SCALE.getDoubleValue()));
        properties.setProperty("timerNotificationX", String.valueOf(Configs.Generic.TIMER_NOTIFICATION_X.getIntegerValue()));
        properties.setProperty("timerNotificationY", String.valueOf(Configs.Generic.TIMER_NOTIFICATION_Y.getIntegerValue()));
        properties.setProperty("timerNotificationScale", String.valueOf(Configs.Generic.TIMER_NOTIFICATION_SCALE.getDoubleValue()));
    }

    public record BlockCount(String id, long count) {}
}
