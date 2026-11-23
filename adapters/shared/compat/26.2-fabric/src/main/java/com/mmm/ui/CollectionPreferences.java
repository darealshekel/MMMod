package com.mmm.ui;

import com.google.gson.JsonObject;
import com.mmm.MMM;
import com.mmm.leaderboard.BlockRankingCategory;
import com.mmm.storage.AtomicJsonStorage;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Local UI preferences, independent of player credentials, counters and pins. */
record CollectionPreferences(BlockRankingCategory category, int filter, int sort, boolean global) {
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("mmm-collection-board.json"); }

    static CollectionPreferences load() { return load(path()); }
    static CollectionPreferences load(Path path) {
        try {
            var json = AtomicJsonStorage.readObjectWithBackup(path).value();
            if (json != null) {
                String group = json.has("category") ? json.get("category").getAsString() : "";
                return new CollectionPreferences(group.isEmpty() ? null : BlockRankingCategory.valueOf(group),
                        index(json, "filter"), index(json, "sort"), !json.has("global") || json.get("global").getAsBoolean());
            }
        } catch (IOException | RuntimeException invalid) { MMM.LOGGER.warn("Could not read collection board preferences", invalid); }
        return new CollectionPreferences(null, 0, 0, true);
    }
    private static int index(JsonObject json, String key) {
        return json.has(key) ? Math.clamp(json.get(key).getAsInt(), 0, 2) : 0;
    }
    void save() { save(path()); }
    void save(Path path) {
        var json = new JsonObject();
        json.addProperty("category", category == null ? "" : category.name());
        json.addProperty("filter", filter);
        json.addProperty("sort", sort);
        json.addProperty("global", global);
        try { AtomicJsonStorage.write(path, json, true); }
        catch (IOException failure) { MMM.LOGGER.warn("Could not save collection board preferences", failure); }
    }
}
