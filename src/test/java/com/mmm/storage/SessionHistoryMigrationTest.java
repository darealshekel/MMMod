package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionHistoryMigrationTest
{
    @TempDir
    Path tempDir;

    @Test
    void legacySessionsMergeIntoCanonicalProfileAndDeduplicateBySessionKey() throws Exception
    {
        Path canonicalFile = this.tempDir.resolve("server_hash").resolve("sessions.csv");
        Path legacyFile = this.tempDir.resolve("play.example.test").resolve("sessions.csv");
        Files.createDirectories(canonicalFile.getParent());
        Files.createDirectories(legacyFile.getParent());

        SessionData partial = session(1_000L, 701_000L, 10_000L);
        SessionData completed = session(1_000L, 901_000L, 15_000L);
        SessionData second = session(2_000L, 902_000L, 20_000L);
        Files.write(canonicalFile, List.of(partial.serialise()));
        Files.write(legacyFile, List.of(completed.serialise(), second.serialise()));

        assertEquals(2, SessionHistory.mergeSessionFiles(canonicalFile, legacyFile));

        List<String> merged = Files.readAllLines(canonicalFile);
        assertEquals(2, merged.size());
        assertEquals(15_000L, SessionData.deserialise(merged.get(0)).totalBlocks);
        assertEquals(20_000L, SessionData.deserialise(merged.get(1)).totalBlocks);
    }

    private static SessionData session(long start, long end, long blocks)
    {
        SessionData session = new SessionData(start);
        session.endTimeMs = end;
        session.totalBlocks = blocks;
        return session;
    }
}
