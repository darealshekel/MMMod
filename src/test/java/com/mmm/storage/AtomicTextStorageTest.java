package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicTextStorageTest
{
    @TempDir
    Path tempDir;

    @Test
    void writesTextAndKeepsThePreviousValidValue() throws Exception
    {
        Path target = this.tempDir.resolve("state.properties");

        AtomicTextStorage.write(target, "version=1\nvalue=one\n", true, AtomicTextStorageTest::validState);
        AtomicTextStorage.write(target, "version=1\nvalue=two\n", true, AtomicTextStorageTest::validState);

        assertEquals("version=1\nvalue=two\n", Files.readString(target));
        assertEquals("version=1\nvalue=one\n", Files.readString(AtomicTextStorage.backupPath(target)));
    }

    @Test
    void malformedPrimaryRecoversTheLastValidBackup() throws Exception
    {
        Path target = this.tempDir.resolve("state.properties");
        AtomicTextStorage.write(target, "version=1\nvalue=one\n", true, AtomicTextStorageTest::validState);
        AtomicTextStorage.write(target, "version=1\nvalue=two\n", true, AtomicTextStorageTest::validState);
        Files.writeString(target, "partial");

        AtomicTextStorage.ReadResult recovered = AtomicTextStorage.readWithBackup(target, AtomicTextStorageTest::validState);

        assertTrue(recovered.recoveredFromBackup());
        assertEquals("version=1\nvalue=one\n", recovered.value());
    }

    @Test
    void malformedPrimaryDoesNotReplaceTheValidBackup() throws Exception
    {
        Path target = this.tempDir.resolve("state.properties");
        AtomicTextStorage.write(target, "version=1\nvalue=one\n", true, AtomicTextStorageTest::validState);
        AtomicTextStorage.write(target, "version=1\nvalue=two\n", true, AtomicTextStorageTest::validState);
        Files.writeString(target, "partial");

        AtomicTextStorage.write(target, "version=1\nvalue=three\n", true, AtomicTextStorageTest::validState);

        assertEquals("version=1\nvalue=one\n", Files.readString(AtomicTextStorage.backupPath(target)));
        assertEquals("version=1\nvalue=three\n", Files.readString(target));
    }

    private static boolean validState(String value)
    {
        return value != null && value.startsWith("version=1\n");
    }
}
