package com.mmm.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicJsonStorage
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private AtomicJsonStorage()
    {
    }

    public record ReadResult(JsonObject value, Path source, boolean recoveredFromBackup)
    {
    }

    public static ReadResult readObjectWithBackup(Path target) throws IOException
    {
        AtomicTextStorage.ReadResult result = AtomicTextStorage.readWithBackup(target, AtomicJsonStorage::isJsonObject);
        JsonObject value = result.value() == null ? null : JsonParser.parseString(result.value()).getAsJsonObject();
        return new ReadResult(value, result.source(), result.recoveredFromBackup());
    }

    public static void write(Path target, JsonElement value, boolean keepBackup) throws IOException
    {
        AtomicTextStorage.write(target, GSON.toJson(value) + System.lineSeparator(), keepBackup, AtomicJsonStorage::isJsonObject);
    }

    private static boolean isJsonObject(String value)
    {
        JsonElement parsed = JsonParser.parseString(value);
        return parsed != null && parsed.isJsonObject();
    }

    public static Path createMigrationBackup(Path source) throws IOException
    {
        Path normalized = source.toAbsolutePath().normalize();
        Path backup = normalized.resolveSibling(normalized.getFileName() + ".pre-mmm-migration.bak");
        if (Files.exists(backup) == false)
        {
            Files.copy(normalized, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        return backup;
    }

    public static Path backupPath(Path target)
    {
        return AtomicTextStorage.backupPath(target);
    }
}
