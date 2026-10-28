package com.mmm.legacy.settings;

import com.mmm.config.value.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsUiTest {
    @Test void integerSliderClampsAndRounds() {
        ConfigInteger number = new ConfigInteger("opacity", 50, 0, 100, "");
        SettingsUi.slide(number, -0.1); assertEquals(0, number.getIntegerValue());
        SettingsUi.slide(number, 0.735); assertEquals(74, number.getIntegerValue());
        SettingsUi.slide(number, 2); assertEquals(100, number.getIntegerValue());
        assertEquals(1, SettingsUi.fraction(number));
    }
    @Test void decimalSliderHonorsNonzeroBounds() {
        ConfigDouble scale = new ConfigDouble("scale", 1, 0.1, 1, "");
        SettingsUi.slide(scale, 0); assertEquals(0.1, scale.getDoubleValue());
        SettingsUi.slide(scale, 1); assertEquals(1, scale.getDoubleValue());
        SettingsUi.slide(scale, 0.5); assertEquals(0.55, scale.getDoubleValue(), 0.00001);
    }
    @Test void colorRoundTripsAlphaAndSupportsRgb() {
        assertEquals(0xFFAABBCC, SettingsUi.color("#aabbcc"));
        assertEquals(0x12AABBCC, SettingsUi.color("12aabbcc"));
        assertEquals("#00123456", SettingsUi.hex(SettingsUi.color("#00123456")));
        assertThrows(IllegalArgumentException.class, () -> SettingsUi.color("#abc"));
        assertThrows(IllegalArgumentException.class, () -> SettingsUi.color("#GG112233"));
    }
    @Test void valueValidationDoesNotMutateOnFailure() {
        ConfigDouble number = new ConfigDouble("number", 1, 0.25, 1.75, "");
        for (String input : new String[]{"NaN", "Infinity", "-Infinity", "0", "2", ""})
            assertThrows(IllegalArgumentException.class, () -> SettingsUi.validate(number, input), input);
        assertEquals(1, number.getDoubleValue());
        assertEquals("1.25", SettingsUi.validate(number, " 1.25 "));
        ConfigInteger integer = new ConfigInteger("i", 1, 0, 3, "");
        assertThrows(IllegalArgumentException.class, () -> SettingsUi.validate(integer, "1.5"));
        assertThrows(IllegalArgumentException.class, () -> SettingsUi.validate(integer, "4"));
    }
    @Test void timerParsingPreservesModernMinuteDefaultAndRejectsSilentClamps() {
        assertEquals(60_000L, SettingsUi.duration("1"));
        assertEquals(1_000L, SettingsUi.duration("1s"));
        assertEquals(5_400_000L, SettingsUi.duration(" 1.5H "));
        assertEquals(86_400_000L, SettingsUi.duration("24h"));
        for (String input : new String[]{"", "0s", "25h", "NaN", "Infinity", "-1m", "bad", "1s2", "0.5s"})
            assertThrows(IllegalArgumentException.class, () -> SettingsUi.duration(input), input);
    }
    @Test void tokenSearchAndScrollStayBounded() {
        assertTrue(SettingsUi.matches("HUD Background Color", "  color HUD "));
        assertFalse(SettingsUi.matches("HUD Color", "opacity"));
        assertEquals(0, SettingsUi.clampScroll(20, 0, 10));
        assertEquals(90, SettingsUi.clampScroll(1000, 100, 10));
        assertEquals(0, SettingsUi.clampScroll(-1, 100, 10));
    }
}
