package com.mmm.legacy.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.mmm.legacy.command.ClientCommandParser.Action.*;

class ClientCommandParserTest {
    @Test void doesNotCaptureServerChatOrSimilarlyNamedCommands() {
        for (String input : new String[]{"hello", "/msg x hi", "/mmmserver", "/mmm:other", "/sbhelperOther"}) {
            assertNull(ClientCommandParser.parse(input), input);
        }
    }

    @Test void timerSupportsResumePauseAndDurationShorthands() {
        assertEquals(new ClientCommandParser.Command(TIMER_START, ""), ClientCommandParser.parse("/mmm timer start"));
        assertEquals(new ClientCommandParser.Command(TIMER_START, "24h"), ClientCommandParser.parse("/mmm timer 24h"));
        assertEquals(new ClientCommandParser.Command(TIMER_SET, "90s"), ClientCommandParser.parse("/mmm timer set 90s"));
        assertEquals(TIMER_PAUSE, ClientCommandParser.parse("/mmm timer pause").action());
        assertEquals(HELP, ClientCommandParser.parse("/mmm timer 25h").action());
        assertEquals(HELP, ClientCommandParser.parse("/mmm timer pause extra").action());
    }

    @Test void exposesEveryModernCommandFamily() {
        assertEquals(LEADERBOARD, ClientCommandParser.parse("/mmm leaderboard").action());
        assertEquals(COLLECTIONS, ClientCommandParser.parse("/mmm collections").action());
        assertEquals(EFFICIENCY, ClientCommandParser.parse("/mmm efficiency").action());
        assertEquals(CHAT_IGNORED, ClientCommandParser.parse("/mmm chat ignored").action());
        assertEquals(new ClientCommandParser.Command(CHAT_IGNORE, "_SleepyZZZ"), ClientCommandParser.parse("/mmm chat ignore _SleepyZZZ"));
        assertEquals(new ClientCommandParser.Command(SCOREBOARD_MAX, "100"), ClientCommandParser.parse("/sbhelper maxDisplayCount 100"));
        assertEquals(SCOREBOARD_UP, ClientCommandParser.parse("/mmm scoreboard pageUp").action());
        assertEquals(SCOREBOARD_DOWN, ClientCommandParser.parse("/mmm scoreboard pageDown").action());
    }

    @Test void malformedLocalCommandsAreStillHandledLocally() {
        for (String command : new String[]{"/mmm", "/mmm chat", "/mmm chat ignore", "/mmm collections extra", "/sbhelper"}) {
            assertEquals(HELP, ClientCommandParser.parse(command).action(), command);
        }
    }

    @Test void completionReturnsOnlyTheCurrentToken() {
        assertEquals(java.util.List.of("collections"), ClientCommandParser.suggestions("/mmm coll"));
        assertEquals(24, ClientCommandParser.suggestions("/mmm timer set ").size());
        assertTrue(ClientCommandParser.suggestions("/msg ").isEmpty());
    }
}
