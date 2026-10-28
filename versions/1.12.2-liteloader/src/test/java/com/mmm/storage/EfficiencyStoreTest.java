package com.mmm.storage;

import com.mmm.tracker.SessionEfficiency;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EfficiencyStoreTest {
    @Test void savedSummaryLoadsOriginalActiveTimeBreakdown() throws Exception {
        SessionData session = new SessionData(1_234_567_891L);
        session.endTimeMs += 1_000;
        session.efficiency.add(SessionEfficiency.Activity.MINING, 200);
        session.efficiency.add(SessionEfficiency.Activity.MOVING, 100);
        EfficiencyStore.save(session);
        AsyncPersistence.flush(Duration.ofSeconds(3));

        SessionData restored = new SessionData(session.startTimeMs);
        restored.endTimeMs = session.endTimeMs;
        var data = EfficiencyStore.load(restored).get(3, TimeUnit.SECONDS);
        assertEquals(200, data.time(SessionEfficiency.Activity.MINING));
        assertEquals(100, data.time(SessionEfficiency.Activity.MOVING));
        assertEquals(700, data.untracked(restored.getActiveDurationMs()));
    }
}
