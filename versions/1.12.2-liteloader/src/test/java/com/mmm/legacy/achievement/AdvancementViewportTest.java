package com.mmm.legacy.achievement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementViewportTest {
    @Test void matchesProbeScreenshotAtGuiScaleTwo() {
        AdvancementViewport viewport = AdvancementViewport.fromFrame((640 - 252) / 2, (360 - 140) / 2);
        assertEquals(new AdvancementViewport(203, 128, 437, 241), viewport);
        assertEquals(406, viewport.left() * 2);
        assertEquals(256, viewport.top() * 2);
        assertEquals(874, viewport.right() * 2);
        assertEquals(482, viewport.bottom() * 2);
        // LegacyDrawContext.clip converts GUI top-left coordinates to GL's bottom-left coordinates.
        assertEquals(238, 720 - viewport.bottom() * 2);
        assertEquals(468, (viewport.right() - viewport.left()) * 2);
        assertEquals(226, (viewport.bottom() - viewport.top()) * 2);
    }

    @Test void dimensionsStayFixedAcrossGuiScalesAndOddWindowSizes() {
        for (int[] size : new int[][]{{1280, 720}, {640, 360}, {427, 240}, {320, 240}, {683, 385}}) {
            int frameX = (size[0] - 252) / 2;
            int frameY = (size[1] - 140) / 2;
            AdvancementViewport viewport = AdvancementViewport.fromFrame(frameX, frameY);
            assertEquals(234, viewport.right() - viewport.left());
            assertEquals(113, viewport.bottom() - viewport.top());
            assertEquals(frameX + 9, viewport.left());
            assertEquals(frameY + 18, viewport.top());
            assertEquals(frameX + 243, viewport.right());
            assertEquals(frameY + 131, viewport.bottom());
        }
    }

    @Test void onlyContentsAreClippedNotHeaderAndRootTab() {
        AdvancementViewport viewport = AdvancementViewport.fromFrame(194, 110);
        assertTrue(viewport.top() > 110 + 6);
        assertTrue(viewport.left() > 194);
        assertTrue(viewport.right() < 194 + 252);
        assertTrue(viewport.bottom() < 110 + 140);
    }
}
