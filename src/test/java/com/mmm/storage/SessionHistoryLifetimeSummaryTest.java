package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionHistoryLifetimeSummaryTest
{
    @TempDir
    Path tempDir;

    @Test
    void includesRootSessionsAndDeduplicatesMigratedCopies() throws Exception
    {
        SessionData rootOnly = session(1_000L, 3_600_000L, 50_000L);
        SessionData migratedCopy = session(10_000_000L, 3_600_000L, 45_000L);
        SessionData newerMigratedCopy = session(10_000_000L, 4_000_000L, 55_000L);

        Path rootFile = this.tempDir.resolve("sessions.csv");
        Path legacyFile = this.tempDir.resolve("legacy").resolve("sessions.csv");
        Path canonicalFile = this.tempDir.resolve("canonical").resolve("sessions.csv");
        Files.createDirectories(legacyFile.getParent());
        Files.createDirectories(canonicalFile.getParent());
        Files.writeString(rootFile, rootOnly.serialise());
        Files.writeString(legacyFile, migratedCopy.serialise());
        Files.writeString(canonicalFile, newerMigratedCopy.serialise());

        SessionHistory.LifetimeSummary summary = SessionHistory.summarizeLifetimeSessionFiles(
                List.of(rootFile, legacyFile, canonicalFile));

        assertEquals(7_600_000L, summary.totalActiveMs());
        assertEquals(4_000_000L, summary.longestSessionMs());
        assertEquals(2, summary.sessionsAt40kBph());
        assertEquals(1, summary.sessionsAt50kBph());
    }

    private static SessionData session(long startTimeMs, long durationMs, long blocks)
    {
        SessionData session = new SessionData(startTimeMs);
        session.endTimeMs = startTimeMs + durationMs;
        session.wallDurationMs = durationMs;
        session.totalBlocks = blocks;
        return session;
    }
}
