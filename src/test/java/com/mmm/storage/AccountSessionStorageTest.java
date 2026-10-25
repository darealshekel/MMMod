package com.mmm.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccountSessionStorageTest {
    @TempDir Path root;
    private static final String A = "11111111111111111111111111111111";
    private static final String B = "22222222222222222222222222222222";

    @Test void legacyHistoryHasOnlyOneOwnerAndRemainsIntact() throws Exception {
        Files.createDirectories(root.resolve("sessions/server_test"));
        Path legacy = root.resolve("sessions/server_test/sessions.csv");
        Files.writeString(legacy, "legacy data");
        assertTrue(AccountSessionStorage.claimLegacy(root, A, A));
        assertFalse(AccountSessionStorage.claimLegacy(root, B, B));
        assertTrue(AccountSessionStorage.claimLegacy(root, A, A));
        assertEquals("legacy data", Files.readString(legacy));
        assertNotEquals(AccountSessionStorage.accountRoot(root, A), AccountSessionStorage.accountRoot(root, B));
    }

    @Test void unknownHistoryCannotBeAssignedToEveryAccount() throws Exception {
        assertFalse(AccountSessionStorage.claimLegacy(root, A, ""));
        assertFalse(AccountSessionStorage.claimLegacy(root, B, A));
        assertFalse(Files.exists(root.resolve("legacy-session-owner.txt")));
    }

    @Test void accountKeysAreNormalizedAndCannotEscapeStorage() {
        assertEquals(AccountSessionStorage.accountRoot(root, A),
                AccountSessionStorage.accountRoot(root, "11111111-1111-1111-1111-111111111111"));
        assertThrows(IllegalArgumentException.class, () -> AccountSessionStorage.accountRoot(root, "../other"));
    }
}
