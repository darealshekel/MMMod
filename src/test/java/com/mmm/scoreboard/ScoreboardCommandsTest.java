package com.mmm.scoreboard;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreboardCommandsTest {
    @Test void unchangedScoresGenerateNoCommands() {
        assertTrue(ScoreboardCommands.changes("mined", Map.of("Miner", 123), Map.of("Miner", 123)).isEmpty());
    }
    @Test void renamesRemoveOldOwnerAndSetNewOwnerOnOnlyThisObjective() {
        var commands = ScoreboardCommands.changes("mined", Map.of("Old", 123), Map.of("New", 456));
        assertEquals(List.of("/scoreboard players reset Old mined", "/scoreboard players set New mined 456"), commands);
    }
    @Test void rejectsSelectorsWildcardsWhitespaceAndCommands() {
        for (String owner : List.of("@a", "*", "Miner Other", "Miner\n/op Miner"))
            assertThrows(IllegalArgumentException.class, () -> ScoreboardCommands.changes("mined", Map.of(), Map.of(owner, 1)));
    }
    @Test void minimumIntegerAndNegativeScoresRemainValid() {
        assertEquals(List.of("/scoreboard players set Miner mined -2147483648"),
                ScoreboardCommands.changes("mined", Map.of(), Map.of("Miner", Integer.MIN_VALUE)));
    }
    @Test void rejectsConflictsOnEditedRowsOnly() {
        var before = Map.of("A", 10, "B", 20);
        var after = Map.of("A", 11, "B", 20);
        assertEquals(List.of("/scoreboard players set A mined 11"),
                ScoreboardCommands.changes("mined", before, after, Map.of("A", 10, "B", 99)));
        assertThrows(IllegalArgumentException.class,
                () -> ScoreboardCommands.changes("mined", before, after, Map.of("A", 12, "B", 20)));
    }
}
