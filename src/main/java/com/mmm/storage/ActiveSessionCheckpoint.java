package com.mmm.storage;

import com.google.gson.JsonObject;
import com.mmm.util.MmmLog;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ActiveSessionCheckpoint
{
    private static final java.util.concurrent.ConcurrentHashMap<Path, JsonObject> PENDING = new java.util.concurrent.ConcurrentHashMap<>();
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
            PENDING.put(target, snapshot);
            AsyncPersistence.submit(persistenceKey(target), () -> {
                writeSnapshot(target, snapshot);
                PENDING.remove(target, snapshot);
            });
        }
        catch (Exception exception)
        {
            MmmLog.LOGGER.warn("[MMM] Failed to queue active session checkpoint: {}", exception.getMessage());
        }
    }

    public static State load(String worldId)
    {
        try
        {
            Path target = stateFile(worldId);
            JsonObject pending = PENDING.get(target);
            if (pending != null) return pending.size() == 0 ? null : deserialize(pending);
            return load(target);
        }
        catch (Exception exception)
        {
            MmmLog.LOGGER.warn("[MMM] Failed to load active session checkpoint: {}", exception.getMessage());
            return null;
        }
    }

    public static void clear(String worldId)
    {
        Path target = stateFile(worldId);
        JsonObject tombstone = new JsonObject();
        PENDING.put(target, tombstone);
        AsyncPersistence.submitOrdered(persistenceKey(target), () -> {
            try
            {
                Files.deleteIfExists(target);
                Files.deleteIfExists(AtomicJsonStorage.backupPath(target));
                PENDING.remove(target, tombstone);
            }
            catch (Exception exception)
            {
                throw new IllegalStateException("Could not retire active session checkpoint", exception);
            }
        });
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
        root.add("efficiency", state.session().efficiency.toJson());
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
            MmmLog.LOGGER.warn("[MMM] Active session checkpoint recovered from backup {}", result.source());
        }

        JsonObject root = result.value();
        return deserialize(root);
    }

    private static State deserialize(JsonObject root)
    {
        String serialized = stringValue(root, "session");
        SessionData session = SessionData.deserialise(serialized);
        if (session == null)
        {
            return null;
        }

        if (root.has("efficiency") && root.get("efficiency").isJsonObject())
        {
            session.efficiency = com.mmm.tracker.SessionEfficiency.fromJson(root.getAsJsonObject("efficiency"), session.getActiveDurationMs());
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
        Path target = AccountSessionStorage.root().resolve("active-sessions").resolve(safeWorldId + ".json");
        // One-time copy only for the established owner. Never guess the save that
        // produced the old shared "." checkpoint.
        Path legacy = AccountSessionStorage.legacyFile("active-sessions", safeWorldId + ".json");
        if (!safeWorldId.equals(".") && !safeWorldId.equals("..") && legacy != null
                && !Files.exists(target) && Files.isRegularFile(legacy))
        {
            Path marker = target.resolveSibling(target.getFileName() + ".migrated");
            try
            {
                if (!Files.exists(marker))
                {
                    Files.createDirectories(target.getParent());
                    Files.copy(legacy, target);
                    Files.writeString(marker, "legacy checkpoint copied");
                }
            }
            catch (Exception error) { MmmLog.LOGGER.warn("[MMM] Could not migrate active checkpoint", error); }
        }
        return target;
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
