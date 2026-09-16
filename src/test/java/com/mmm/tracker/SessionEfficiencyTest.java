package com.mmm.tracker;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.mmm.tracker.SessionEfficiency.Activity.*;

class SessionEfficiencyTest
{
    @Test void intervalsBelongToPreviousActivityAndDoNotOverlap() {
        var stats = new SessionEfficiency(); var sampler = new SessionEfficiency.Sampler();
        sampler.sample(stats, 0, MINING);
        sampler.sample(stats, 50, MOVING);
        sampler.sample(stats, 100, INVENTORY);
        sampler.sample(stats, 150, NON_INSTANT);
        sampler.sample(stats, 200, OTHER);
        assertEquals(50, stats.time(MINING));
        assertEquals(50, stats.time(MOVING));
        assertEquals(50, stats.time(INVENTORY));
        assertEquals(50, stats.time(NON_INSTANT));
        assertEquals(200, stats.tracked());
    }
    @Test void pausedIgtDoesNotAdvanceAndSessionChangesDoNotLeak() {
        var a = new SessionEfficiency(); var b = new SessionEfficiency(); var sampler = new SessionEfficiency.Sampler();
        sampler.sample(a, 0, MINING); sampler.sample(a, 50, null);
        for (int i = 0; i < 100; i++) sampler.sample(a, 50, null);
        sampler.sample(a, 50, MINING); sampler.sample(a, 100, MINING);
        sampler.sample(b, 0, MOVING); sampler.sample(b, 50, MOVING);
        assertEquals(100, a.time(MINING)); assertEquals(50, b.time(MOVING));
        assertEquals(0, b.time(MINING));
    }
    @Test void oldTimeAndLongGapsRemainUnknown() {
        var stats = new SessionEfficiency(); var sampler = new SessionEfficiency.Sampler();
        sampler.sample(stats, 60_000, MINING); sampler.sample(stats, 70_000, MINING);
        sampler.sample(stats, 70_050, MINING);
        assertEquals(50, stats.tracked()); assertEquals(70_000, stats.untracked(70_050));
    }
    @Test void jsonRoundtripAndBounds() {
        var stats = new SessionEfficiency(); stats.add(MINING, 200); stats.add(MENU, 100);
        var copy = SessionEfficiency.fromJson(stats.toJson(), 500);
        assertEquals(200, copy.time(MINING)); assertEquals(100, copy.time(MENU));
        JsonObject bad = new JsonObject(); bad.addProperty("MINING", Long.MAX_VALUE); bad.addProperty("MOVING", -20);
        assertEquals(500, SessionEfficiency.fromJson(bad, 500).tracked());
        assertEquals(0, SessionEfficiency.fromJson(null, 500).tracked());
    }
}
