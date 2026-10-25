package com.mmm.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mmm.util.MmmLog;
import com.mmm.social.PublicChatClient;
import com.mmm.storage.AsyncPersistence;
import com.mmm.storage.AtomicJsonStorage;
import com.mmm.storage.MiningCalendarStore;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.storage.SharedStoragePaths;
import com.mmm.tracker.MiningStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.toast.AdvancementToast;

public final class MmmAdvancementManager
{
    private static final int STATE_VERSION = 4;
    private static final long SAVE_INTERVAL_MS = 5_000L;
    private static final Set<MmmAdvancementDefinition> UNLOCKED = EnumSet.noneOf(MmmAdvancementDefinition.class);

    private static String activePlayerKey = "";
    private static boolean loaded;
    private static boolean dirty;
    private static long totalSessionMs;
    private static long longestSessionMs;
    private static int longestStreakDays;
    private static int bestHourBlocks;
    private static int sessionsAt40kBph;
    private static int sessionsAt50kBph;
    private static int loadedStateVersion;
    private static long lastSaveAtMs;
    private static long lastProgressRefreshAtMs;
    private static int tickCounter;
    private static boolean progressReady;
    private static boolean initialProgressPending;
    private static boolean pendingFirstTimeNotification;
    private static boolean existingHistory;
    private static long generation;

    private MmmAdvancementManager()
    {
    }

    public static synchronized void onClientTick(Minecraft client)
    {
        if (client == null || client.player == null)
        {
            return;
        }
        activate(client.player.getUuid().toString());
        if (++tickCounter < 20)
        {
            return;
        }
        tickCounter = 0;

        refreshProgress();
        reconcileUnlocks(client, true);
        if (dirty && System.currentTimeMillis() - lastSaveAtMs >= SAVE_INTERVAL_MS)
        {
            save();
        }
    }

    public static synchronized boolean isUnlocked(MmmAdvancementDefinition definition)
    {
        return progressReady && definition != null && UNLOCKED.contains(definition);
    }

    public static synchronized boolean isProgressReady() { return progressReady; }

    public static synchronized MmmAdvancementSnapshot snapshot()
    {
        return new MmmAdvancementSnapshot(
                totalSessionMs,
                longestStreakDays,
                bestHourBlocks,
                longestSessionMs,
                sessionsAt40kBph,
                sessionsAt50kBph);
    }

    public static synchronized void refreshForDisplay()
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null)
        {
            return;
        }
        activate(client.player.getUuid().toString());
        long now = System.currentTimeMillis();
        if (now - lastProgressRefreshAtMs >= 1_000L)
        {
            refreshProgress();
            reconcileUnlocks(client, true);
        }
    }

    public static synchronized void flush()
    {
        if (loaded)
        {
            refreshProgress();
            save();
        }
    }

    public static synchronized void onDisconnect()
    {
        flush();
        resetState("");
    }

    private static void activate(String playerKey)
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

        resetState(normalized);
        Path path = SharedStoragePaths.advancementFile(activePlayerKey);
        boolean existingState = Files.isRegularFile(path) || Files.isRegularFile(AtomicJsonStorage.backupPath(path));
        if (existingState)
        {
            load(path);
            refreshProgress();
            if (loadedStateVersion < STATE_VERSION)
            {
                revalidateUnlockedSilently();
            }
        }
        else
        {
            backfillExistingProgress();
            unlockReachedSilently();
            if (UNLOCKED.add(MmmAdvancementDefinition.FIRST_TIME))
            {
                dirty = true;
                if (!progressReady)
                {
                    pendingFirstTimeNotification = true;
                }
                else if (!existingHistory)
                {
                    Minecraft client = Minecraft.getInstance();
                    if (client != null)
                    {
                        announce(client, MmmAdvancementDefinition.FIRST_TIME);
                    }
                }
            }
            save();
        }
        loaded = true;
    }

    private static void resetState(String playerKey)
    {
        generation++;
        activePlayerKey = playerKey;
        loaded = false;
        dirty = false;
        totalSessionMs = 0L;
        longestSessionMs = 0L;
        longestStreakDays = 0;
        bestHourBlocks = 0;
        sessionsAt40kBph = 0;
        sessionsAt50kBph = 0;
        loadedStateVersion = 0;
        lastSaveAtMs = 0L;
        lastProgressRefreshAtMs = 0L;
        tickCounter = 0;
        UNLOCKED.clear();
        progressReady = false;
        initialProgressPending = true;
        pendingFirstTimeNotification = false;
        existingHistory = false;
    }

    private static void backfillExistingProgress()
    {
        refreshProgress();
    }

    private static void unlockReachedSilently()
    {
        MmmAdvancementSnapshot snapshot = snapshot();
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
        {
            if (definition != MmmAdvancementDefinition.FIRST_TIME && definition.isReached(snapshot))
            {
                UNLOCKED.add(definition);
            }
        }
        dirty = true;
    }

    private static void revalidateUnlockedSilently()
    {
        if (!progressReady)
        {
            return;
        }
        MmmAdvancementSnapshot snapshot = snapshot();
        for (MmmAdvancementDefinition definition : MmmAdvancementDefinition.ORDERED)
        {
            if (definition.isReached(snapshot))
            {
                UNLOCKED.add(definition);
            }
            else
            {
                UNLOCKED.remove(definition);
            }
        }
        dirty = true;
    }

    private static void refreshProgress()
    {
        lastProgressRefreshAtMs = System.currentTimeMillis();
        SessionHistory.LifetimeSummary history = SessionHistory.getLifetimeSummary();
        progressReady = history != null;
        if (!progressReady)
        {
            return;
        }
        long nextTotalSessionMs = history.totalActiveMs();
        long nextLongestSessionMs = history.longestSessionMs();
        int nextBestHourBlocks = history.bestHourBlocks();
        int nextLongestStreakDays = MiningCalendarStore.longestQualifiedMiningStreakDays();
        // The newly started live session is not evidence of an existing MMM installation.
        existingHistory = AdvancementReconciliation.hasHistory(new MmmAdvancementSnapshot(
                nextTotalSessionMs, nextLongestStreakDays, nextBestHourBlocks, nextLongestSessionMs,
                history.sessionsAt40kBph(), history.sessionsAt50kBph()));
        if (MiningStats.isSessionActive())
        {
            SessionData session = MiningStats.getCurrentSession();
            long activeMs = Math.max(0L, MiningStats.getSessionDurationMs());
            nextTotalSessionMs = saturatedAdd(nextTotalSessionMs, activeMs);
            nextLongestSessionMs = Math.max(nextLongestSessionMs, activeMs);
            nextBestHourBlocks = Math.max(nextBestHourBlocks, session.getBestHourBlocks());
        }

        if (totalSessionMs != nextTotalSessionMs
                || longestSessionMs != nextLongestSessionMs
                || longestStreakDays != nextLongestStreakDays
                || bestHourBlocks != nextBestHourBlocks
                || sessionsAt40kBph != history.sessionsAt40kBph()
                || sessionsAt50kBph != history.sessionsAt50kBph())
        {
            totalSessionMs = nextTotalSessionMs;
            longestSessionMs = nextLongestSessionMs;
            longestStreakDays = nextLongestStreakDays;
            bestHourBlocks = nextBestHourBlocks;
            sessionsAt40kBph = history.sessionsAt40kBph();
            sessionsAt50kBph = history.sessionsAt50kBph();
            dirty = true;
        }
    }

    private static void reconcileUnlocks(Minecraft client, boolean announceNewUnlocks)
    {
        if (!progressReady)
        {
            return;
        }
        // Loading existing history must not announce old achievements as new ones.
        announceNewUnlocks &= !initialProgressPending;
        initialProgressPending = false;
        if (pendingFirstTimeNotification)
        {
            pendingFirstTimeNotification = false;
            if (!existingHistory)
            {
                announce(client, MmmAdvancementDefinition.FIRST_TIME);
            }
        }
        AdvancementReconciliation.Result result = AdvancementReconciliation.apply(UNLOCKED, snapshot());
        dirty |= result.changed();
        if (announceNewUnlocks) for (MmmAdvancementDefinition definition : result.newlyUnlocked()) announce(client, definition);
    }

    private static void announce(Minecraft client, MmmAdvancementDefinition definition)
    {
        String playerKey = activePlayerKey;
        long requestedGeneration = generation;
        client.executeTask(() -> {
            if (generation != requestedGeneration || client.player == null
                    || !normalizePlayerKey(client.player.getUuid().toString()).equals(playerKey)) return;
            client.getToasts().add(new AdvancementToast(MmmAdvancementTree.entry(definition)));
            PublicChatClient.publishAdvancement(definition.title(), definition.description());
        });
    }

    private static void load(Path path)
    {
        try
        {
            AtomicJsonStorage.ReadResult result = AtomicJsonStorage.readObjectWithBackup(path);
            JsonObject root = result.value();
            if (root == null)
            {
                return;
            }
            int version = intValue(root, "version");
            loadedStateVersion = version;
            JsonArray unlocked = root.getAsJsonArray("unlocked");
            if (unlocked != null)
            {
                for (JsonElement element : unlocked)
                {
                    try
                    {
                        UNLOCKED.add(MmmAdvancementDefinition.valueOf(element.getAsString()));
                    }
                    catch (Exception ignored)
                    {
                    }
                }
            }
            if (result.recoveredFromBackup())
            {
                dirty = true;
            }
        }
        catch (Exception exception)
        {
            MmmLog.LOGGER.warn("[MMM] Could not load advancement progress: {}", exception.getMessage());
        }
    }

    private static void save()
    {
        if (activePlayerKey.isBlank())
        {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", STATE_VERSION);
        root.addProperty("total_session_ms", Math.max(0L, totalSessionMs));
        root.addProperty("longest_session_ms", Math.max(0L, longestSessionMs));
        root.addProperty("longest_streak_days", Math.max(0, longestStreakDays));
        root.addProperty("best_hour_blocks", Math.max(0, bestHourBlocks));
        root.addProperty("sessions_at_40k_bph", Math.max(0, sessionsAt40kBph));
        root.addProperty("sessions_at_50k_bph", Math.max(0, sessionsAt50kBph));
        JsonArray unlocked = new JsonArray();
        MmmAdvancementDefinition.ORDERED.stream()
                .filter(UNLOCKED::contains)
                .forEach(definition -> unlocked.add(definition.name()));
        root.add("unlocked", unlocked);

        Path path = SharedStoragePaths.advancementFile(activePlayerKey);
        String playerKey = activePlayerKey;
        lastSaveAtMs = System.currentTimeMillis();
        dirty = false;
        AsyncPersistence.submit("advancements:" + path.toAbsolutePath().normalize(), () -> {
            try
            {
                AtomicJsonStorage.write(path, root, true);
            }
            catch (Exception exception)
            {
                synchronized (MmmAdvancementManager.class)
                {
                    if (activePlayerKey.equals(playerKey)) dirty = true;
                }
                throw new IllegalStateException("Could not save advancement progress", exception);
            }
        });
    }

    private static long longValue(JsonObject root, String key)
    {
        try
        {
            return Math.max(0L, root.get(key).getAsLong());
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }

    private static int intValue(JsonObject root, String key)
    {
        return (int) Math.min(Integer.MAX_VALUE, longValue(root, key));
    }

    private static long saturatedAdd(long left, long right)
    {
        long safeLeft = Math.max(0L, left);
        long safeRight = Math.max(0L, right);
        return Long.MAX_VALUE - safeLeft < safeRight ? Long.MAX_VALUE : safeLeft + safeRight;
    }

    private static String normalizePlayerKey(String value)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        return normalized.isBlank() ? "unlinked" : normalized;
    }
}
