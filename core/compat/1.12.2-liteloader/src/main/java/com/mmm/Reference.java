package com.mmm;

import java.io.InputStream;
import java.util.Properties;

public final class Reference
{
    public static final String MOD_ID = "mmm";
    public static final String MOD_NAME = "MMM";
    public static final String STORAGE_ID = "mmm";
    public static final String LEGACY_STORAGE_ID = "mining" + "tracker" + "addon";
    public static final String MOD_VERSION = readVersion();

    private static String readVersion() {
        try (InputStream stream = Reference.class.getResourceAsStream("/mmm-build.properties")) {
            if (stream == null) throw new IllegalStateException("Missing MMM build metadata");
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("version", "unknown");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read MMM build metadata", exception);
        }
    }

    private Reference()
    {
    }
}
