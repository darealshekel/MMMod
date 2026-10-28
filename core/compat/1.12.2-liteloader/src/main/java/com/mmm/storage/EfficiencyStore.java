package com.mmm.storage;

import com.mmm.tracker.SessionEfficiency;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class EfficiencyStore {
    private EfficiencyStore() {}

    private static Path file(long start) {
        return AccountSessionStorage.root().resolve("session-efficiency").resolve(Math.max(0, start) + ".json");
    }

    public static void save(SessionData session) {
        if (session.efficiency.tracked() == 0) return;
        var snapshot = session.efficiency.toJson();
        Path path = file(session.startTimeMs);
        AsyncPersistence.submit("efficiency:" + path, () -> {
            try { AtomicJsonStorage.write(path, snapshot, true); }
            catch (Exception error) { throw new IllegalStateException("Could not save session efficiency", error); }
        });
    }

    public static CompletableFuture<SessionEfficiency> load(SessionData session) {
        long start = session.startTimeMs, duration = session.getActiveDurationMs();
        Path target = file(start);
        Path legacy = AccountSessionStorage.legacyFile("session-efficiency", Math.max(0, start) + ".json");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var data = AtomicJsonStorage.readObjectWithBackup(target).value();
                if (data == null && legacy != null) data = AtomicJsonStorage.readObjectWithBackup(legacy).value();
                return SessionEfficiency.fromJson(data, duration);
            }
            catch (Exception error) { throw new IllegalStateException("Could not load session efficiency", error); }
        });
    }
}
