package com.mmm.legacy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyInputTest {
    @Test void canonicalNamesSurviveKeyboardTableRoundTrip() {
        for (String key : new String[] {"X", "V", "ESCAPE", "BACKSPACE", "LEFT_CONTROL", "RIGHT_ALT", "PAGE_UP", "KP_7", "KP_ENTER", "LEFT_BRACKET", "PRINT_SCREEN"}) {
            int code = LegacyInput.keyCode(key);
            assertTrue(code > 0, key);
            assertEquals(key, LegacyInput.keyName(code));
        }
    }
    @Test void aliasesAndMissingKeysAreHandled() {
        assertEquals("LEFT_CONTROL", LegacyInput.normalize("KEY_LCTRL"));
        assertEquals("ENTER", LegacyInput.normalize("RETURN"));
        assertEquals(0, LegacyInput.keyCode("NOT_A_KEY"));
        assertEquals("", LegacyInput.keyName(-1));
    }
}
