package com.mmm.storage;

import com.google.gson.JsonObject;
import com.mmm.MMM;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ActiveSessionCheckpoint
{
    public record State(
            SessionData session,
            boolean paused,
            boolean autoPaused,
            boolean menuPaused,
            long pausedAtMs,
            long pausedAccumulatedMs,
            long sessionStartTotalMined,
            long pausedSessionMinedOffset,
            long lastScoreboardSessionUpdateActiveElapsedMs,
            boolean session100kRecorded,
            long savedAtMs)
    {
    }

    private ActiveSessionCheckpoint()
    {
    }

    public static void save(String worldId, State state)
    {
        if (state == null || state.session() == null)
        {
            return;
        }

        try
        {
            Path target = stateFile(worldId);
            JsonObject snapshot = serialize(state);
            AsyncPersistence.submit(persistenceKey(target), () -> writeSnapshot(target, snapshot));
        }
        catch (Exception exception)
        {
            MMM.LOGGER.warn("[MMM] Failed to queue active session checkpoint: {}", exception.getMessage());
        }
    }

    public static State load(String worldId)
    {
        try
        {
            return load(stateFile(worldId));
        }
        catch (Exception exception)
        {
            MMM.LOGGER.warn("[MMM] Failed to load active session checkpoint: {}", exception.getMessage());
            return null;
        }
    }

    public static void clear(String worldId)
    {
        Path target = stateFile(worldId);
        AsyncPersistence.cancel(persistenceKey(target));
        AsyncPersistence.flush(java.time.Duration.ofSeconds(1L));
        try
        {
            Files.deleteIfExists(target);
            Files.deleteIfExists(AtomicJsonStorage.backupPath(target));
        }
        catch (Exception exception)
        {
            MMM.LOGGER.warn("[MMM] Failed to clear active session checkpoint: {}", exception.getMessage());
        }
    }

    static void save(Path target, State state) throws Exception
    {
        AtomicJsonStorage.write(target, serialize(state), true);
    }

    private static void writeSnapshot(Path target, JsonObject snapshot)
    {
        try
        {
            AtomicJsonStorage.write(target, snapshot, true);
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Could not write active session checkpoint", exception);
        }
    }

    private static JsonObject serialize(State state)
    {
        JsonObject root = new JsonObject();
        root.addProperty("session", state.session().serialise());
        root.addProperty("paused", state.paused());
        root.addProperty("autoPaused", state.autoPaused());
        root.addProperty("menuPaused", state.menuPaused());
        root.addProperty("pausedAtMs", Math.max(0L, state.pausedAtMs()));
        root.addProperty("pausedAccumulatedMs", Math.max(0L, state.pausedAccumulatedMs()));
        root.addProperty("sessionStartTotalMined", Math.max(0L, state.sessionStartTotalMined()));
        root.addProperty("pausedSessionMinedOffset", Math.max(0L, state.pausedSessionMinedOffset()));
        root.addProperty(
                "lastScoreboardSessionUpdateActiveElapsedMs",
                Math.max(0L, state.lastScoreboardSessionUpdateActiveElapsedMs()));
        root.addProperty("session100kRecorded", state.session100kRecorded());
        root.addProperty("savedAtMs", Math.max(0L, state.savedAtMs()));
        return root;
    }

    static State load(Path target) throws Exception
    {
        AtomicJsonStorage.ReadResult result = AtomicJsonStorage.readObjectWithBackup(target);
        if (result.value() == null)
        {
            return null;
        }
        if (result.recoveredFromBackup())
        {
            MMM.LOGGER.warn("[MMM] Active session checkpoint recovered from backup {}", result.source());
        }

        JsonObject root = result.value();
        String serialized = stringValue(root, "session");
        SessionData session = SessionData.deserialise(serialized);
        if (session == null)
        {
            return null;
        }

        return new State(
                session,
                booleanValue(root, "paused"),
                booleanValue(root, "autoPaused"),
                booleanValue(root, "menuPaused"),
                longValue(root, "pausedAtMs"),
                longValue(root, "pausedAccumulatedMs"),
                longValue(root, "sessionStartTotalMined"),
                longValue(root, "pausedSessionMinedOffset"),
                longValue(root, "lastScoreboardSessionUpdateActiveElapsedMs"),
                booleanValue(root, "session100kRecorded"),
                longValue(root, "savedAtMs"));
    }

    private static Path stateFile(String worldId)
    {
        String safeWorldId = worldId == null
                ? "default"
                : worldId.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "_");
        if (safeWorldId.isBlank())
        {
            safeWorldId = "default";
        }
        return SharedStoragePaths.root().resolve("active-sessions").resolve(safeWorldId + ".json");
    }

    private static String persistenceKey(Path target)
    {
        return "active-session:" + target.toAbsolutePath().normalize();
    }

    private static String stringValue(JsonObject root, String key)
    {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : "";
    }

    private static long longValue(JsonObject root, String key)
    {
        try
        {
            return root.has(key) && root.get(key).isJsonPrimitive()
                    ? Math.max(0L, root.get(key).getAsLong())
                    : 0L;
        }
        catch (RuntimeException ignored)
        {
            return 0L;
        }
    }

    private static boolean booleanValue(JsonObject root, String key)
    {
        try
        {
            return root.has(key) && root.get(key).isJsonPrimitive() && root.get(key).getAsBoolean();
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }
}
