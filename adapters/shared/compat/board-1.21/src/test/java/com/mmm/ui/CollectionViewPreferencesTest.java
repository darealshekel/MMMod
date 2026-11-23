package com.mmm.ui;

import com.mmm.leaderboard.BlockRankingCategory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CollectionViewPreferencesTest {
    @TempDir Path directory;

    @Test void remembersFiltersScopeAndCategoryAcrossOpens() throws Exception {
        Path file = directory.resolve("board.json");
        var defaults = new CollectionViewPreferences(0, 0, true, null);
        assertEquals(defaults, CollectionViewPreferences.read(file));
        var saved = new CollectionViewPreferences(2, 1, false, BlockRankingCategory.ORES);
        saved.write(file);
        assertEquals(saved, CollectionViewPreferences.read(file));
        defaults.write(file);
        assertEquals(defaults, CollectionViewPreferences.read(file));
        Files.writeString(file, "{");
        assertEquals(saved, CollectionViewPreferences.read(file));
    }

    @Test void stalePreferencesCannotIndexOutsideTheBoard() throws Exception {
        var file = directory.resolve("board.json");
        Files.writeString(file, "{\"filter\":99,\"sort\":-4,\"category\":\"OLD\",\"global\":{}}");
        assertEquals(new CollectionViewPreferences(2, 0, true, null), CollectionViewPreferences.read(file));
    }
}
