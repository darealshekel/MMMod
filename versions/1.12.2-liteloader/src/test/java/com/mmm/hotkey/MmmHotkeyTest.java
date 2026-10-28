package com.mmm.hotkey;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MmmHotkeyTest {
    @Test void xThenVTriggersOnceButVThenXDoesNot() {
        MmmHotkey key = new MmmHotkey("openGui", "X+V", "");
        AtomicInteger hits = new AtomicInteger();
        key.setCallback(hits::incrementAndGet);
        Set<String> pressed = new HashSet<>();
        pressed.add("V"); key.poll(false, pressed::contains);
        pressed.add("X"); key.poll(false, pressed::contains);
        assertEquals(0, hits.get());
        pressed.remove("V"); key.poll(false, pressed::contains);
        pressed.add("V"); key.poll(false, pressed::contains);
        key.poll(false, pressed::contains);
        assertEquals(1, hits.get());
    }
    @Test void eventAndPollingDoNotTriggerTwice() {
        MmmHotkey key = new MmmHotkey("openGui", "X,V", "");
        AtomicInteger hits = new AtomicInteger();
        key.setCallback(hits::incrementAndGet);
        key.onInput("V", 1, false, Set.of("X", "V")::contains);
        key.poll(false, Set.of("X", "V")::contains);
        key.onInput("V", 2, false, Set.of("X", "V")::contains);
        assertEquals(1, hits.get());
        key.onInput("V", 0, false, Set.of("X")::contains);
        key.onInput("V", 1, false, Set.of("X", "V")::contains);
        assertEquals(2, hits.get());
    }
    @Test void inputWhileChatOpenCannotFireAfterClosing() {
        MmmHotkey key = new MmmHotkey("openGui", "X+V", "");
        AtomicInteger hits = new AtomicInteger();
        key.setCallback(hits::incrementAndGet);
        key.poll(true, Set.of("X", "V")::contains);
        key.poll(false, Set.of("X", "V")::contains);
        assertEquals(0, hits.get());
    }
    @Test void unassignedKeysAndMalformedBindingsCannotFire() {
        MmmHotkey key = new MmmHotkey("openGui", "X+V", "");
        AtomicInteger hits = new AtomicInteger();
        key.setCallback(hits::incrementAndGet);
        key.setStorageString("");
        key.poll(false, ignored -> true);
        assertEquals(0, hits.get());
        key.resetToDefault();
        assertEquals("X+V", key.getStorageString());
    }
}
