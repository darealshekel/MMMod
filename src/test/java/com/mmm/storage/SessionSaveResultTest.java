package com.mmm.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SessionSaveResultTest
{
    @TempDir Path directory;

    @Test
    void failedSaveIsReportedAndRetryIsIdempotent() throws Exception
    {
        SessionData session = new SessionData(1_000);
        session.endTimeMs = 61_000;
        session.totalBlocks = 10_000;
        Path blocked = directory.resolve("not-a-directory");
        Files.writeString(blocked, "preserve");
        assertFalse(SessionHistory.saveDurably(blocked.resolve("sessions.csv"), session));
        assertEquals("preserve", Files.readString(blocked));
        Path target = directory.resolve("world/sessions.csv");
        assertTrue(SessionHistory.saveDurably(target, session));
        assertTrue(SessionHistory.saveDurably(target, session));
        assertEquals(1, Files.readAllLines(target).size());
    }
}
