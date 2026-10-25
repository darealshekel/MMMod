package com.mmm.liteloader;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiteLoaderEnvironmentTest {
    @Test void usesTheInstanceConfigDirectory() {
        Path instance = Path.of("build", "isolated-instance");
        LiteLoaderEnvironment.initialize(instance);
        assertEquals(instance.toAbsolutePath().normalize().resolve("config"), LiteLoaderEnvironment.configDirectory());
    }

    @Test void rejectsMissingGameDirectory() {
        assertThrows(NullPointerException.class, () -> LiteLoaderEnvironment.initialize(null));
    }
}
