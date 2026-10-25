package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicJsonStorageTest
{
    @TempDir
    Path tempDir;

    @Test
    void writesValidJsonAtomically()
            throws Exception
    {
        Path target = this.tempDir.resolve("mmm.json");
        JsonObject value = new JsonObject();
        value.addProperty("dailyGoal", 35_000);

        AtomicJsonStorage.write(target, value, true);

        JsonObject stored = new JsonParser().parse(Files.readString(target)).getAsJsonObject();
        assertEquals(35_000, stored.get("dailyGoal").getAsInt());
    }

    @Test
    void keepsPreviousFileAsBackupOnReplacement()
            throws Exception
    {
        Path target = this.tempDir.resolve("mmm.json");
        JsonObject first = new JsonObject();
        first.addProperty("value", 1);
        JsonObject second = new JsonObject();
        second.addProperty("value", 2);

        AtomicJsonStorage.write(target, first, true);
        AtomicJsonStorage.write(target, second, true);

        JsonObject backup = new JsonParser().parse(Files.readString(AtomicJsonStorage.backupPath(target))).getAsJsonObject();
        assertEquals(1, backup.get("value").getAsInt());
        assertEquals(2, new JsonParser().parse(Files.readString(target)).getAsJsonObject().get("value").getAsInt());
    }

    @Test
    void recoversValidBackupWhenPrimaryFileIsMalformed()
            throws Exception
    {
        Path target = this.tempDir.resolve("mmm.json");
        JsonObject first = new JsonObject();
        first.addProperty("weeklyBlocksMined", 456_789L);
        JsonObject second = new JsonObject();
        second.addProperty("weeklyBlocksMined", 500_000L);
        AtomicJsonStorage.write(target, first, true);
        AtomicJsonStorage.write(target, second, true);
        Files.writeString(target, "{partially-written");

        AtomicJsonStorage.ReadResult recovered = AtomicJsonStorage.readObjectWithBackup(target);

        assertTrue(recovered.recoveredFromBackup());
        assertEquals(456_789L, recovered.value().get("weeklyBlocksMined").getAsLong());
    }

    @Test
    void corruptPrimaryDoesNotReplaceLastValidBackup()
            throws Exception
    {
        Path target = this.tempDir.resolve("mmm.json");
        JsonObject first = new JsonObject();
        first.addProperty("value", 1);
        JsonObject second = new JsonObject();
        second.addProperty("value", 2);
        JsonObject third = new JsonObject();
        third.addProperty("value", 3);
        AtomicJsonStorage.write(target, first, true);
        AtomicJsonStorage.write(target, second, true);
        Files.writeString(target, "not-json");

        AtomicJsonStorage.write(target, third, true);

        assertEquals(1, new JsonParser().parse(Files.readString(AtomicJsonStorage.backupPath(target))).getAsJsonObject().get("value").getAsInt());
        assertEquals(3, new JsonParser().parse(Files.readString(target)).getAsJsonObject().get("value").getAsInt());
    }
    @Test
    void migrationBackupIsCreatedOnlyOnce()
            throws Exception
    {
        Path source = this.tempDir.resolve("aetweaks.json");
        Files.writeString(source, "{\"value\":1}");
        Path backup = AtomicJsonStorage.createMigrationBackup(source);
        Files.writeString(source, "{\"value\":2}");
        Path secondCall = AtomicJsonStorage.createMigrationBackup(source);

        assertEquals(backup, secondCall);
        assertEquals("{\"value\":1}", Files.readString(backup));
        assertTrue(Files.exists(backup));
    }
}
