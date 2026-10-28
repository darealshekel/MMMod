package com.mmm.legacy.social;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalChatCompletionTest {
    @Test void cyclesForwardAndBackwardWithoutCallingTheServer() {
        var completion = new LocalChatCompletion();
        String result = completion.complete("/mmm t", 6, false);
        assertEquals("/mmm timer", result);
        completion.reset();
        result = completion.complete("/mmm timer st", 13, false);
        assertEquals("/mmm timer start", result);
        result = completion.complete(result, completion.cursor(), false);
        assertEquals("/mmm timer stop", result);
        result = completion.complete(result, completion.cursor(), true);
        assertEquals("/mmm timer start", result);
    }
    @Test void preservesTextAfterCursorAndLeavesOtherCommandsToVanilla() {
        var completion = new LocalChatCompletion();
        assertEquals("/mmm timer start 1h", completion.complete("/mmm timer st 1h", 13, false));
        assertNull(completion.complete("/msg Player hi", 14, false));
        assertNull(completion.complete("/m", 2, false));
        assertNull(completion.complete("hello", 5, false));
        assertFalse(LocalChatCompletion.isLocal("/mmmOther"));
        assertTrue(LocalChatCompletion.isLocal("/sbhelper max"));
    }
}
