package com.mmm.legacy.settings;

import com.mmm.hotkey.MmmHotkey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyChordDraftTest {
    @Test void normalizesLegacyAndModernModifierNames() {
        KeyChordDraft draft = new KeyChordDraft();
        draft.set("LMENU+S");
        assertEquals("LEFT_ALT,S", draft.storage());
        assertEquals("LEFT_ALT + S", draft.label());
        assertTrue(draft.conflicts("S,LEFT_ALT"));
    }
    @Test void deduplicatesAndLimitsToTwoKeys() {
        KeyChordDraft draft = new KeyChordDraft();
        draft.add("X"); draft.add("X"); draft.add("V");
        assertEquals("X,V", draft.storage());
        draft.add("B");
        assertEquals("B", draft.storage());
    }
    @Test void captureIsTransactionalAndUnassignIsExplicit() {
        MmmHotkey key = new MmmHotkey("sample", "X,V", "");
        KeyChordDraft draft = new KeyChordDraft(); draft.set(key.getStorageString());
        draft.clear();
        assertEquals("X,V", key.getStorageString());
        assertEquals("Unassigned", draft.label());
        assertFalse(draft.conflicts(""));
        key.setStorageString(draft.storage()); assertEquals("", key.getStorageString());
    }
    @Test void whitespaceAndMouseNamesRemainStable() {
        KeyChordDraft draft = new KeyChordDraft(); draft.set(" LEFT_SHIFT , MOUSE_3 ");
        assertEquals("LEFT_SHIFT,MOUSE_3", draft.storage());
        assertFalse(draft.conflicts("LEFT_SHIFT"));
        assertFalse(draft.conflicts("LEFT_SHIFT,MOUSE_2"));
    }
}
