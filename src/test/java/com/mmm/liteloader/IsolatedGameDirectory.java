package com.mmm.liteloader;

import java.nio.file.Paths;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class IsolatedGameDirectory implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        LiteLoaderEnvironment.initialize(Paths.get(System.getProperty("mmm.testGameDirectory")));
    }
}
