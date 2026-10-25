package com.mmm.legacy.settings;

import com.google.gson.JsonObject;
import com.mmm.storage.AtomicJsonStorage;
import com.mmm.storage.SharedStoragePaths;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class SessionExport {
    private SessionExport() {}
    /** Capture JSON on the client thread before handing it to the IO worker. */
    public static CompletableFuture<Path> write(JsonObject snapshot, String kind) {
        JsonObject copy = snapshot.deepCopy();
        Path path = SharedStoragePaths.root().resolve("exports").resolve(kind + "-" + System.currentTimeMillis() + ".json");
        return CompletableFuture.supplyAsync(() -> {
            try { AtomicJsonStorage.write(path, copy, false); return path; }
            catch (Exception error) { throw new IllegalStateException("Could not export " + kind, error); }
        });
    }
    public static CompletableFuture<Path> world(com.mmm.storage.SessionHistory.WorldHistory world) {
        var sessions = java.util.List.copyOf(world.sessions());
        var loads = sessions.stream().map(com.mmm.storage.EfficiencyStore::load).toList();
        return CompletableFuture.allOf(loads.toArray(new CompletableFuture<?>[0])).thenCompose(ignored -> {
            JsonObject json = new JsonObject();
            json.addProperty("world", world.displayName());
            com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
            for (int i = 0; i < sessions.size(); i++) {
                var session = sessions.get(i);
                var snapshot = SessionUiModel.snapshot(session, world.displayName(), session.getActiveDurationMs(), session.getWallDurationMs(), session.getBestHourBlocks());
                var efficiency = loads.get(i).join();
                snapshot.add("efficiency", efficiency.toJson());
                snapshot.addProperty("untrackedMs", efficiency.untracked(session.getActiveDurationMs()));
                rows.add(snapshot);
            }
            json.add("sessions", rows);
            return write(json, "history");
        });
    }
}
