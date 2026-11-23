package com.mmm.tracker;

import com.mmm.MMM;
import com.mmm.config.Configs;
import com.mmm.config.PlaytimeFormat;
import com.mmm.storage.AsyncPersistence;
import com.mmm.storage.AtomicJsonStorage;
import com.mmm.storage.SharedStoragePaths;
import com.mmm.storage.SessionHistory;
import com.mmm.storage.WorldSessionContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

public final class PlaytimeTracker
{
    private static final Map<String, PlaytimeState> accounts = new HashMap<>();
    private static PlaytimeState state;
    private static String account = "";
    private static Path file;
    private static long nextSaveNanos;

    private PlaytimeTracker() { }

    public static synchronized void onJoin(MinecraftClient client)
    {
        if (client.player == null) return;
        String nextAccount = client.player.getUuidAsString();
        if (!nextAccount.equals(account))
        {
            if (state != null) state.leave();
            save();
            account = nextAccount;
            file = SharedStoragePaths.root().resolve("playtime").resolve(account + ".json");
            if (!accounts.containsKey(account))
            {
                PlaytimeState loaded = null;
                try { loaded = PlaytimeState.restore(AtomicJsonStorage.readObjectWithBackup(file).value()); }
                catch (Exception exception)
                {
                    // Do not replace an unreadable history with an empty counter.
                    MMM.LOGGER.warn("[MMM] Could not load playtime; existing data preserved: {}", exception.getMessage());
                }
                accounts.put(account, loaded);
            }
            state = accounts.get(account);
        }
        if (state == null) return;
        String key = WorldSessionContext.getCurrentWorldId();
        if (client.isInSingleplayer() && client.getServer() != null)
        {
            String savePath = client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().toString();
            key = "singleplayer-" + UUID.nameUUIDFromBytes(savePath.getBytes(StandardCharsets.UTF_8));
        }
        state.enter(key, System.nanoTime());
        nextSaveNanos = System.nanoTime() + 30_000_000_000L;
    }

    public static synchronized void tick(MinecraftClient client)
    {
        if (state == null) return;
        long now = System.nanoTime();
        state.tick(now, client.world != null && client.player != null && !client.isPaused());
        if (now >= nextSaveNanos && client.world != null)
        {
            save();
            nextSaveNanos = now + 30_000_000_000L;
        }
    }

    public static synchronized void observeVanilla(int ticks)
    {
        if (state != null) state.observeVanilla(ticks);
    }

    public static synchronized void disconnect()
    {
        if (state != null) state.leave();
        save();
    }

    public static synchronized void save()
    {
        if (state == null || file == null) return;
        var snapshot = state.snapshot();
        Path target = file;
        AsyncPersistence.submit("playtime:" + target, () -> {
            try { AtomicJsonStorage.write(target, snapshot, true); }
            catch (Exception exception) { MMM.LOGGER.warn("[MMM] Could not save playtime: {}", exception.getMessage()); }
        });
    }

    public static synchronized String worldClock() { return clock(state == null ? -1 : state.worldMillis()); }
    public static String modClock()
    {
        var history = SessionHistory.getLifetimeSummary();
        return history == null ? "Loading..." : clock(sessionIgtMillis(history.totalActiveMs(), MiningStats.getSessionDurationMs()));
    }

    static long sessionIgtMillis(long savedIgt, long currentIgt)
    {
        long saved = Math.max(0L, savedIgt);
        long current = Math.max(0L, currentIgt);
        return Long.MAX_VALUE - saved < current ? Long.MAX_VALUE : saved + current;
    }
    public static synchronized String allWorldsClock() { return clock(state == null ? -1 : state.allWorldMillis()); }
    private static String clock(long millis)
    {
        return millis < 0 ? "Unavailable" : ((PlaytimeFormat) Configs.Generic.PLAYTIME_FORMAT.getOptionListValue()).format(millis);
    }
}
