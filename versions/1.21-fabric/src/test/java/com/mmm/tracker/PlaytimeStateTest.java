package com.mmm.tracker;

import com.google.gson.JsonObject;
import com.mmm.storage.AtomicJsonStorage;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlaytimeStateTest
{
    @Test void countsWithoutSessionAndImportsVanillaWithoutDoubling()
    {
        PlaytimeState state = new PlaytimeState();
        state.enter("server", 0);
        state.tick(0, true);
        state.observeVanilla(72_000); // One hour before this feature was installed.
        state.tick(1_000_000_000L, true);
        assertEquals(3_601_000, state.worldMillis());
        assertEquals(1_000, state.modMillis());
        state.observeVanilla(72_000); // Stale stats must not reset or duplicate elapsed time.
        assertEquals(3_601_000, state.worldMillis());
        state.observeVanilla(72_020);
        assertEquals(3_601_000, state.allWorldMillis());
    }

    @Test void skipsPauseOfflineTimeAndSuspendedClientGaps()
    {
        PlaytimeState state = new PlaytimeState();
        state.enter("world", 0);
        state.tick(0, true);
        state.tick(1_000_000_000L, true);
        state.tick(2_000_000_000L, false);
        state.tick(120_000_000_000L, false);
        state.tick(121_000_000_000L, true);
        state.tick(122_000_000_000L, true);
        assertEquals(2_000, state.modMillis());
        state.tick(900_000_000_000L, true);
        assertEquals(2_000, state.modMillis());
        state.leave();
        state.tick(901_000_000_000L, true);
        assertEquals(2_000, state.modMillis());
    }

    @Test void combinesWorldsAndDoesNotResetOnDimensionChange()
    {
        PlaytimeState state = new PlaytimeState();
        state.enter("server-a", 0);
        state.tick(0, true);
        state.observeVanilla(1_000);
        state.enter("server-a", 500_000_000L);
        state.tick(1_000_000_000L, true);
        assertEquals(51_000, state.worldMillis());
        state.leave();
        state.enter("server-b", 1_000_000_000L);
        state.observeVanilla(2_000);
        state.tick(1_000_000_000L, true);
        state.tick(2_000_000_000L, true);
        assertEquals(101_000, state.worldMillis());
        assertEquals(152_000, state.allWorldMillis());
        assertEquals(2_000, state.modMillis());
    }

    @Test void preservesSubMillisecondTimeInsteadOfDriftingEachTick()
    {
        PlaytimeState state = new PlaytimeState();
        state.enter("world", 0);
        state.tick(0, true);
        for (int tick = 1; tick <= 1000; tick++) state.tick(tick * 50_500_000L, true);
        assertEquals(50_500, state.modMillis());
    }

    @Test void persistsCountersButNotOfflineClockAnchors(@TempDir Path directory) throws Exception
    {
        PlaytimeState state = new PlaytimeState();
        state.enter("world", 0);
        state.tick(0, true);
        state.observeVanilla(72_000);
        state.tick(1_000_000_000L, true);
        Path file = directory.resolve("playtime.json");
        AtomicJsonStorage.write(file, state.snapshot(), true);
        PlaytimeState restored = PlaytimeState.restore(AtomicJsonStorage.readObjectWithBackup(file).value());
        restored.enter("world", 900_000_000_000L);
        restored.tick(900_000_000_000L, true);
        restored.tick(901_000_000_000L, true);
        assertEquals(3_602_000, restored.worldMillis());
        assertEquals(2_000, restored.modMillis());
    }

    @Test void rejectsMalformedDataInsteadOfResettingIt()
    {
        JsonObject invalid = new JsonObject();
        invalid.addProperty("version", 1);
        invalid.addProperty("modMillis", -1);
        invalid.add("worlds", new JsonObject());
        assertThrows(IllegalArgumentException.class, () -> PlaytimeState.restore(invalid));
        assertEquals(0, PlaytimeState.restore(null).modMillis());
    }

    @Test void ignoresNegativeOverflowedVanillaStats()
    {
        PlaytimeState state = new PlaytimeState();
        state.enter("world", 0);
        state.observeVanilla(100);
        state.observeVanilla(-1);
        assertEquals(5000, state.worldMillis());
    }
}
