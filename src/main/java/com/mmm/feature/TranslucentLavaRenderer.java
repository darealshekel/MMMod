package com.mmm.feature;

import com.mmm.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;

public final class TranslucentLavaRenderer {
    private static int applied = -1, pending = -1, delay;
    private static ClientWorld world;
    private TranslucentLavaRenderer() {}
    public static boolean isEnabled() { return Configs.Generic.TRANSLUCENT_LAVA.getBooleanValue(); }
    public static int alphaFromPercent(int percent) { return Math.round(Math.max(10, Math.min(100, percent)) * 255 / 100.0F); }
    public static float vertexAlpha(float vanilla) {
        return isEnabled() ? vanilla * alphaFromPercent(Configs.Generic.LAVA_OPACITY.getIntegerValue()) / 255.0F : vanilla;
    }
    public static void requestReload() { pending = -1; }
    /** Called on the main client tick; rebuilding chunks moves existing lava between render layers. */
    public static void tick(Minecraft client) {
        int desired = isEnabled() ? alphaFromPercent(Configs.Generic.LAVA_OPACITY.getIntegerValue()) : 0;
        if (world != client.world) { world = client.world; applied = desired; pending = desired; return; }
        if (desired == applied || client.world == null) return;
        if (pending != desired) { pending = desired; delay = 4; return; }
        if (delay-- > 0) return;
        client.worldRenderer.reload();
        applied = desired;
    }
}

