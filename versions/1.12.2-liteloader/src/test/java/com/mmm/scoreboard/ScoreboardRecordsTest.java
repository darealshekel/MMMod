package com.mmm.scoreboard;

import com.mmm.liteloader.LiteLoaderEnvironment;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ScoreboardRecordsTest {
    private List<ScoreboardState.Snapshot> previous;
    @BeforeEach void isolate() {
        previous = ScoreboardState.getSnapshots();
        while (ScoreboardState.removeSnapshot(0)) {}
    }
    @AfterEach void restore() {
        while (ScoreboardState.removeSnapshot(0)) {}
        for (var snapshot : previous) ScoreboardState.addSnapshot(snapshot);
    }
    @Test void snapshotOwnsAnImmutableCopyOfRows() {
        var rows = new ArrayList<ScoreboardState.SnapshotRow>();
        rows.add(new ScoreboardState.SnapshotRow("Miner", 123));
        var snapshot = new ScoreboardState.Snapshot("mined", "Mined", 1, rows);
        ScoreboardState.addSnapshot(snapshot);
        rows.clear();
        assertEquals(1, ScoreboardState.getSnapshots().getFirst().rows().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rows().clear());
    }
    @Test void reorderAndRemoveValidateBounds() {
        var a = new ScoreboardState.Snapshot("a", "A", 1, List.of());
        var b = new ScoreboardState.Snapshot("b", "B", 2, List.of());
        ScoreboardState.addSnapshot(a); ScoreboardState.addSnapshot(b);
        assertFalse(ScoreboardState.moveSnapshot(0, -1));
        assertTrue(ScoreboardState.moveSnapshot(1, -1));
        assertEquals(List.of(b, a), ScoreboardState.getSnapshots());
        assertTrue(ScoreboardState.removeSnapshot(1));
        assertFalse(ScoreboardState.removeSnapshot(1));
        assertEquals(List.of(b), ScoreboardState.getSnapshots());
    }
    @Test void combinedExportUsesRecordedOrderAndCsvEscaping() throws Exception {
        ScoreboardState.addSnapshot(new ScoreboardState.Snapshot("a", "A,\"quoted\"", 1,
                List.of(new ScoreboardState.SnapshotRow("Z,Name", -1), new ScoreboardState.SnapshotRow("A", 123456))));
        var path = ScoreboardService.exportRecorded();
        try {
            assertEquals(LiteLoaderEnvironment.configDirectory().getParent().resolve("scoreboard-exports"), path.getParent());
            String csv = Files.readString(path).replace("\r\n", "\n");
            assertTrue(csv.startsWith("\"A,\"\"quoted\"\"\"\nPlayer,Score\n"));
            assertTrue(csv.indexOf("\"A\",123456") < csv.indexOf("\"Z,Name\",-1"));
        } finally { Files.deleteIfExists(path); }
    }
}

