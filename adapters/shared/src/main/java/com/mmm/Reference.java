package com.mmm;

import net.fabricmc.loader.api.FabricLoader;

public final class Reference
{
    public static final String MOD_ID = "mmm";
    public static final String MOD_NAME = "MMM";
    public static final String STORAGE_ID = "mmm";
    public static final String LEGACY_STORAGE_ID = "mining" + "tracker" + "addon";
    public static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");

    private Reference()
    {
    }
}