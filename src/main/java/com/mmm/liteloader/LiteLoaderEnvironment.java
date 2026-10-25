package com.mmm.liteloader;

import java.nio.file.Path;
import java.util.Objects;

/** Config lives in the instance's config directory, just like the modern mod. */
public final class LiteLoaderEnvironment {
    private static Path configDirectory;

    private LiteLoaderEnvironment() {}

    public static void initialize(Path gameDirectory) {
        configDirectory = Objects.requireNonNull(gameDirectory, "gameDirectory")
                .toAbsolutePath().normalize().resolve("config");
    }

    public static Path configDirectory() {
        if (configDirectory == null) {
            throw new IllegalStateException("MMM must initialize its LiteLoader environment before reading configuration");
        }
        return configDirectory;
    }
}
