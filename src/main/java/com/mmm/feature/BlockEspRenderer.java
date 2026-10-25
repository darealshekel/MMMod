package com.mmm.feature;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.legacy.visual.VisualColors;
import com.mmm.legacy.visual.WorldBoxes;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.HitResult;

public final class BlockEspRenderer {
    private BlockEspRenderer() {}
    public static void refreshConfig() {
        Configs.Generic.BLOCK_ESP_HEX_COLOR.setValueFromString(
                Configs.normalizeBlockEspHexColor(Configs.Generic.BLOCK_ESP_HEX_COLOR.getStringValue()));
    }
    public static boolean shouldReplaceVanillaOutline(Minecraft client) {
        return FeatureToggle.MMM_BLOCK_ESP.getBooleanValue() && targetBounds(client) != null;
    }
    public static void render(Minecraft client, float partialTick) {
        if (!FeatureToggle.MMM_BLOCK_ESP.getBooleanValue() || client.options.hideGui) return;
        Box box = targetBounds(client);
        if (box == null || client.getCamera() == null) return;
        int color = currentColor(System.currentTimeMillis());
        float opacity = Configs.getBlockEspOpacity();
        boolean outlineOnly = Configs.isBlockEspOutlineOnly();
        WorldBoxes.render(WorldBoxes.cameraRelative(client, box.expand(0.002), partialTick),
                outlineOnly ? 0 : VisualColors.alpha(color, opacity),
                VisualColors.alpha(color, outlineOnly ? opacity : Math.min(1, opacity + 0.25F)),
                outlineOnly ? 2 : 1, !outlineOnly);
    }
    public static int currentColor(long now) {
        if (!Configs.isBlockEspRainbow())
            return VisualColors.parse(Configs.Generic.BLOCK_ESP_HEX_COLOR.getStringValue(), Configs.Generic.DEFAULT_BLOCK_ESP_HEX_COLOR);
        long cycle = (long) Math.max(250, 5000 / Math.max(0.1F, Configs.getBlockEspRainbowSpeed()));
        return java.awt.Color.HSBtoRGB(Math.floorMod(now, cycle) / (float) cycle, 0.9F, 1);
    }
    private static Box targetBounds(Minecraft client) {
        if (client == null || client.world == null || client.player == null || Configs.getBlockEspOpacity() <= 0) return null;
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.type != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getPos();
        var state = client.world.getBlockState(pos);
        if (state.getMaterial() == Material.AIR) return null;
        return state.getOutlineShape(client.world, pos);
    }
}

