package com.mmm.config;

import com.google.gson.JsonObject;
import com.mmm.util.PeriodKeys;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrossVersionSnapshotTest {
    @Test void mergeDoesNotMutateEitherSnapshotOrLoseNewerProgress() {
        long now = System.currentTimeMillis();
        JsonObject local = state(now, 10, 20), disk = state(now, 40, 50);
        local.addProperty("dailyGoal", 250000);
        JsonObject merged = CrossVersionSnapshot.merge(local, disk, now);
        assertEquals(10, local.get("dailyBlocksMined").getAsLong());
        assertEquals(40, disk.get("dailyBlocksMined").getAsLong());
        assertEquals(40, merged.get("dailyBlocksMined").getAsLong());
        assertEquals(50, merged.get("weeklyBlocksMined").getAsLong());
        assertEquals(250000, merged.get("dailyGoal").getAsInt());
    }

    @Test void stalePeriodDoesNotOverwriteTodayButRetainsPersonalBest() {
        long now = System.currentTimeMillis();
        JsonObject merged = CrossVersionSnapshot.merge(state(now, 10, 20),
                state(now - 30L * 86400000L, 900, 2000), now);
        assertEquals(10, merged.get("dailyBlocksMined").getAsLong());
        assertEquals(20, merged.get("weeklyBlocksMined").getAsLong());
        assertEquals(900, merged.get("personalRecordDailyBlocks").getAsLong());
        assertEquals(2000, merged.get("personalRecordWeeklyBlocks").getAsLong());
    }

    private static JsonObject state(long now, long daily, long weekly) {
        JsonObject value = new JsonObject();
        value.addProperty("dailyBlocksDate", PeriodKeys.currentDailyKey(now));
        value.addProperty("weeklyBlocksWeek", PeriodKeys.currentWeeklyKey(now));
        value.addProperty("dailyBlocksMined", daily);
        value.addProperty("weeklyBlocksMined", weekly);
        return value;
    }
}
