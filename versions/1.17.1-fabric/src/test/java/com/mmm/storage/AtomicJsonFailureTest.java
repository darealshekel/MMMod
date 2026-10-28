package com.mmm.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicJsonFailureTest
{
    @TempDir
    Path tempDir;

    @Test
    void failedReplacementLeavesValidWeeklyStateUntouched()
            throws Exception
    {
        Path target = this.tempDir.resolve("mmm.json");
        JsonObject valid = new JsonObject();
        valid.addProperty("weeklyBlocksMined", 456_789L);
        AtomicJsonStorage.write(target, valid, false);

        Path blockedBackup = AtomicJsonStorage.backupPath(target);
        Files.createDirectory(blockedBackup);
        Files.writeString(blockedBackup.resolve("keep"), "prevents directory replacement");

        JsonObject invalidReplacement = new JsonObject();
        invalidReplacement.addProperty("weeklyBlocksMined", 0L);
        assertThrows(IOException.class, () -> AtomicJsonStorage.write(target, invalidReplacement, true));

        JsonObject stored = new JsonParser().parse(Files.readString(target)).getAsJsonObject();
        assertEquals(456_789L, stored.get("weeklyBlocksMined").getAsLong());
    }
}
