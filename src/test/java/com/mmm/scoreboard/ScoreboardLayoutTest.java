package com.mmm.scoreboard;

import com.mmm.config.Configs.ScoreboardPosition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreboardLayoutTest {
    @Test void freePositionSurvivesResolutionChanges() {
        var bounds = ScoreboardLayout.place(1920, 1080, 200, 100, 820, 460, ScoreboardPosition.LEFT, 0);
        assertEquals(1720, bounds.left()); assertEquals(980, bounds.top());
        var smaller = ScoreboardLayout.place(640, 360, 200, 100, 410, 230, ScoreboardPosition.RIGHT, 80);
        assertEquals(220, smaller.left()); assertEquals(130, smaller.top());
    }
    @Test void allAnchorsClampToTheViewport() {
        for (var anchor : ScoreboardPosition.values()) {
            var b = ScoreboardLayout.place(320, 240, 120, 100, -1, -1, anchor, 1000);
            assertTrue(b.left() >= 0 && b.right() <= 320);
            assertTrue(b.top() >= 0 && b.bottom() <= 240);
        }
    }
    @Test void storingPositionClampsNegativeAndOversizedValues() {
        assertEquals(0, ScoreboardLayout.normalize(-1, 100, 820));
        assertEquals(820, ScoreboardLayout.normalize(101, 100, 820));
        assertEquals(410, ScoreboardLayout.normalize(50, 100, 820));
        assertEquals(0, ScoreboardLayout.normalize(100, 0, 820));
    }
}

