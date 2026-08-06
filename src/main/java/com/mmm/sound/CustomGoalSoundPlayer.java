package com.mmm.sound;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.mmm.MMM;
import com.mmm.mixin.SoundManagerAccessor;
import com.mmm.mixin.SoundSystemAccessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;

final class CustomGoalSoundPlayer
{
    private CustomGoalSoundPlayer()
    {
    }

    static boolean play(Path path)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null || path == null || !Files.isRegularFile(path))
        {
            return false;
        }

        try
        {
            SoundSystem soundSystem = ((SoundManagerAccessor) client.getSoundManager()).mmm$getSoundSystem();
            Channel channel = ((SoundSystemAccessor) soundSystem).mmm$getChannel();
            float volume = client.options == null ? 1.0F : client.options.getSoundVolume(SoundCategory.MASTER);

            CompletableFuture.supplyAsync(() -> open(path))
                    .thenAccept(stream -> channel.createSource(SoundEngine.RunMode.STREAMING)
                            .thenAccept(sourceManager -> {
                                if (sourceManager == null)
                                {
                                    closeQuietly(stream);
                                    return;
                                }
                                sourceManager.run(source -> {
                                    try
                                    {
                                        source.setRelative(true);
                                        source.disableAttenuation();
                                        source.setLooping(false);
                                        source.setPitch(1.0F);
                                        source.setVolume(volume);
                                        source.setStream(stream);
                                        source.play();
                                    }
                                    catch (RuntimeException exception)
                                    {
                                        closeQuietly(stream);
                                        sourceManager.close();
                                        MMM.LOGGER.warn("[MMM] Failed to start custom goal sound {}: {}", path, exception.getMessage());
                                    }
                                });
                            })
                            .exceptionally(exception -> {
                                closeQuietly(stream);
                                MMM.LOGGER.warn("[MMM] Failed to allocate a custom goal sound source: {}", rootMessage(exception));
                                return null;
                            }))
                    .exceptionally(exception -> {
                        MMM.LOGGER.warn("[MMM] Failed to load custom goal sound {}: {}", path, rootMessage(exception));
                        return null;
                    });
            return true;
        }
        catch (RuntimeException exception)
        {
            MMM.LOGGER.warn("[MMM] Custom goal sound playback is unavailable: {}", exception.getMessage());
            return false;
        }
    }

    private static OggAudioStream open(Path path)
    {
        try
        {
            InputStream input = new BufferedInputStream(Files.newInputStream(path));
            try
            {
                return new OggAudioStream(input);
            }
            catch (IOException | RuntimeException exception)
            {
                input.close();
                throw exception;
            }
        }
        catch (IOException exception)
        {
            throw new CompletionException(exception);
        }
    }

    private static void closeQuietly(OggAudioStream stream)
    {
        if (stream == null)
        {
            return;
        }
        try
        {
            stream.close();
        }
        catch (IOException ignored)
        {
        }
    }

    private static String rootMessage(Throwable throwable)
    {
        Throwable root = throwable;
        while (root.getCause() != null)
        {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
