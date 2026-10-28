package com.mmm.sound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import com.mmm.MMM;
import com.mmm.storage.SharedStoragePaths;

public final class GoalSoundLibrary
{
    public static final List<Integer> MILESTONES = List.of(25, 50, 75, 100);
    private static final long MAX_SOUND_BYTES = 32L * 1024L * 1024L;

    private GoalSoundLibrary()
    {
    }

    public static boolean isMilestone(int threshold)
    {
        return MILESTONES.contains(threshold);
    }

    public static boolean hasCustomSound(int threshold)
    {
        return isMilestone(threshold) && Files.isRegularFile(soundPath(threshold));
    }

    public static String getDisplayName(int threshold)
    {
        if (!hasCustomSound(threshold))
        {
            return "MMM default";
        }
        Path metadata = metadataPath(threshold);
        try
        {
            String value = Files.readString(metadata, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? "Custom OGG" : value;
        }
        catch (IOException ignored)
        {
            return "Custom OGG";
        }
    }

    public static void install(int threshold, Path source) throws IOException
    {
        requireMilestone(threshold);
        if (source == null || !Files.isRegularFile(source))
        {
            throw new IOException("Choose an existing OGG file.");
        }
        String name = source.getFileName() == null ? "custom.ogg" : source.getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".ogg"))
        {
            throw new IOException("Minecraft custom sounds must use OGG format.");
        }
        long size = Files.size(source);
        if (size <= 4L || size > MAX_SOUND_BYTES)
        {
            throw new IOException("The OGG file must be smaller than 32 MB.");
        }
        byte[] header = new byte[4];
        try (var input = Files.newInputStream(source))
        {
            if (input.read(header) != header.length
                    || header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S')
            {
                throw new IOException("The selected file is not a valid OGG sound.");
            }
        }

        Path directory = SharedStoragePaths.goalSoundsDir();
        Files.createDirectories(directory);
        Path destination = soundPath(threshold);
        Path temporary = directory.resolve("goal-" + threshold + ".ogg.tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        try
        {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException ignored)
        {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(metadataPath(threshold), sanitizeName(name), StandardCharsets.UTF_8);
    }

    public static void reset(int threshold) throws IOException
    {
        requireMilestone(threshold);
        Files.deleteIfExists(soundPath(threshold));
        Files.deleteIfExists(metadataPath(threshold));
    }

    public static void play(int threshold)
    {
        requireMilestone(threshold);
        if (hasCustomSound(threshold) && CustomGoalSoundPlayer.play(soundPath(threshold)))
        {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getSoundManager() != null)
        {
            client.getSoundManager().play(SimpleSoundInstance.forUI(
                    threshold >= 100 ? MmmSounds.GOAL_COMPLETE : MmmSounds.GOAL_SUCCESS,
                    1.0F,
                    1.0F));
        }
    }

    private static Path soundPath(int threshold)
    {
        return SharedStoragePaths.goalSoundsDir().resolve("goal-" + threshold + ".ogg");
    }

    private static Path metadataPath(int threshold)
    {
        return SharedStoragePaths.goalSoundsDir().resolve("goal-" + threshold + ".name");
    }

    private static String sanitizeName(String value)
    {
        String sanitized = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 80);
    }

    private static void requireMilestone(int threshold)
    {
        if (!isMilestone(threshold))
        {
            MMM.LOGGER.error("[MMM] Refused unsupported goal sound threshold {}", threshold);
            throw new IllegalArgumentException("Unsupported goal milestone: " + threshold);
        }
    }
}
