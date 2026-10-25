package com.mmm.storage;

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
import java.util.function.Predicate;

public final class AtomicTextStorage
{
    private AtomicTextStorage()
    {
    }

    public record ReadResult(String value, Path source, boolean recoveredFromBackup)
    {
    }

    public static ReadResult readWithBackup(Path target, Predicate<String> validator) throws IOException
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
                String value = Files.readString(candidate, StandardCharsets.UTF_8);
                if (validator == null || validator.test(value))
                {
                    return new ReadResult(value, candidate, candidate.equals(backup));
                }
                if (firstFailure == null)
                {
                    firstFailure = new IOException("Text validation failed: " + candidate);
                }
            }
            catch (RuntimeException | IOException exception)
            {
                if (firstFailure == null)
                {
                    firstFailure = new IOException("Could not read text file " + candidate, exception);
                }
            }
        }

        if (firstFailure != null)
        {
            throw firstFailure;
        }
        return new ReadResult(null, null, false);
    }

    public static void write(
            Path target,
            String value,
            boolean keepBackup,
            Predicate<String> existingValueValidator) throws IOException
    {
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null)
        {
            throw new IOException("Text target has no parent directory: " + normalized);
        }
        Files.createDirectories(parent);

        Path temporary = parent.resolve(normalized.getFileName() + ".tmp-" + UUID.randomUUID());
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
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

            if (keepBackup && isValidExistingFile(normalized, existingValueValidator))
            {
                Files.copy(
                        normalized,
                        backupPath(normalized),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
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

    public static Path backupPath(Path target)
    {
        return target.resolveSibling(target.getFileName() + ".bak");
    }

    private static boolean isValidExistingFile(Path path, Predicate<String> validator)
    {
        if (Files.isRegularFile(path) == false)
        {
            return false;
        }
        try
        {
            String value = Files.readString(path, StandardCharsets.UTF_8);
            return validator == null || validator.test(value);
        }
        catch (RuntimeException | IOException ignored)
        {
            return false;
        }
    }
}
