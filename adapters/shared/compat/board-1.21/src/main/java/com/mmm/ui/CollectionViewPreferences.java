package com.mmm.ui;

import com.google.gson.JsonObject;
import com.mmm.MMM;
import com.mmm.leaderboard.BlockRankingCategory;
import com.mmm.storage.AsyncPersistence;
import com.mmm.storage.AtomicJsonStorage;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Instance-local view choices, independent of linked accounts and collection progress. */
record CollectionViewPreferences(int filter, int sort, boolean global, BlockRankingCategory category) {
    private static final CollectionViewPreferences DEFAULT = new CollectionViewPreferences(0, 0, true, null);
    private static CollectionViewPreferences cached;

    CollectionViewPreferences {
        filter = Math.clamp(filter, 0, 2);
        sort = Math.clamp(sort, 0, 2);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("mmm-collection-view.json");
    }

    static CollectionViewPreferences current() {
        if (cached == null) {
            try { cached = read(file()); }
            catch (IOException ex) {
                MMM.LOGGER.warn("Could not read collection view preferences", ex);
                cached = DEFAULT;
            }
        }
        return cached;
    }

    void remember() {
        cached = this;
        Path path = file();
        AsyncPersistence.submit("collection-view", () -> {
            try { write(path); }
            catch (IOException ex) { MMM.LOGGER.warn("Could not save collection view preferences", ex); }
        });
    }

    static CollectionViewPreferences read(Path file) throws IOException {
        JsonObject json = AtomicJsonStorage.readObjectWithBackup(file).value();
        if (json == null) return DEFAULT;
        BlockRankingCategory group = null;
        var name = json.get("category");
        if (name != null && name.isJsonPrimitive()) {
            try { group = BlockRankingCategory.valueOf(name.getAsString()); }
            catch (IllegalArgumentException ignored) { /* Removed category: open the overview. */ }
        }
        var scope = json.get("global");
        boolean global = scope == null || !scope.isJsonPrimitive() || !scope.getAsJsonPrimitive().isBoolean() || scope.getAsBoolean();
        return new CollectionViewPreferences(index(json, "filter"), index(json, "sort"), global, group);
    }

    private static int index(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
        return value.getAsInt();
    }

    void write(Path file) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("filter", filter);
        json.addProperty("sort", sort);
        json.addProperty("global", global);
        if (category != null) json.addProperty("category", category.name());
        AtomicJsonStorage.write(file, json, true);
    }
}
