package com.mmm.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

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
        Path normalized = target.toAbsolutePath().normalize();
        Path backup = backupPath(normalized);
        IOException firstFailure = null;

        for (Path candidate : new Path[] {normalized, backup})
        {
            if (Files.isRegularFile(candidate) == false || Files.isReadable(candidate) == false)
            {
                continue;
            }

            try
            {
                JsonElement parsed = new JsonParser().parse(Files.readString(candidate, StandardCharsets.UTF_8));
                if (parsed != null && parsed.isJsonObject())
                {
                    return new ReadResult(parsed.getAsJsonObject(), candidate, candidate.equals(backup));
                }
                if (firstFailure == null)
                {
                    firstFailure = new IOException("JSON root is not an object: " + candidate);
                }
            }
            catch (RuntimeException | IOException exception)
            {
                if (firstFailure == null)
                {
                    firstFailure = new IOException("Could not parse JSON file " + candidate, exception);
                }
            }
        }

        if (firstFailure != null)
        {
            throw firstFailure;
        }
        return new ReadResult(null, null, false);
    }
    public static void write(Path target, JsonElement value, boolean keepBackup) throws IOException
    {
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null)
        {
            throw new IOException("JSON target has no parent directory: " + normalized);
        }
        Files.createDirectories(parent);

        Path temporary = parent.resolve(normalized.getFileName() + ".tmp-" + UUID.randomUUID());
        byte[] bytes = (GSON.toJson(value) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        try
        {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE))
            {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining())
                {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            if (keepBackup && Files.isRegularFile(normalized) && containsValidJson(normalized))
            {
                Files.copy(normalized, backupPath(normalized), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }

            try
            {
                Files.move(temporary, normalized, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException ignored)
            {
                Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean containsValidJson(Path path)
    {
        try
        {
            JsonElement parsed = new JsonParser().parse(Files.readString(path, StandardCharsets.UTF_8));
            return parsed != null && parsed.isJsonObject();
        }
        catch (RuntimeException | IOException ignored)
        {
            return false;
        }
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
        return target.resolveSibling(target.getFileName() + ".bak");
    }
}
