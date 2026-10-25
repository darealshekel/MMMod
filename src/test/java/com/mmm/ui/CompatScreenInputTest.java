package com.mmm.ui;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompatScreenInputTest
{
    @Test void sdlButtonsPreserveExistingMouseBindings()
    {
        assertEquals(0, CompatScreen.legacyMouseButton(InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals(1, CompatScreen.legacyMouseButton(InputConstants.MOUSE_BUTTON_RIGHT));
        assertEquals(2, CompatScreen.legacyMouseButton(InputConstants.MOUSE_BUTTON_MIDDLE));
        assertEquals(3, CompatScreen.legacyMouseButton(InputConstants.MOUSE_BUTTON_4));
        assertEquals(4, CompatScreen.legacyMouseButton(InputConstants.MOUSE_BUTTON_5));
    }
}
