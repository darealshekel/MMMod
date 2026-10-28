package com.mmm.feature;

import com.mmm.config.Configs;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslucentLavaRendererTest {
    @AfterEach void reset() { Configs.Generic.TRANSLUCENT_LAVA.resetToDefault(); Configs.Generic.LAVA_OPACITY.resetToDefault(); }
    @Test void convertsAndClampsConfiguredAlpha() {
        assertEquals(115, TranslucentLavaRenderer.alphaFromPercent(45));
        assertEquals(26, TranslucentLavaRenderer.alphaFromPercent(-1));
        assertEquals(255, TranslucentLavaRenderer.alphaFromPercent(150));
    }
    @Test void disabledDoesNotChangeResourcePackVertexAlpha() {
        Configs.Generic.TRANSLUCENT_LAVA.setBooleanValue(false);
        assertEquals(0.7F, TranslucentLavaRenderer.vertexAlpha(0.7F));
    }
    @Test void enabledMultipliesInsteadOfReplacingExistingAlpha() {
        Configs.Generic.TRANSLUCENT_LAVA.setBooleanValue(true);
        Configs.Generic.LAVA_OPACITY.setIntegerValue(20);
        assertEquals(0.1F, TranslucentLavaRenderer.vertexAlpha(0.5F), 0.0001);
    }
    @Test void minimumOpacitySurvivesVanillaAlphaTest() {
        assertTrue(TranslucentLavaRenderer.alphaFromPercent(10) / 255.0F > 0.1F);
    }
}

