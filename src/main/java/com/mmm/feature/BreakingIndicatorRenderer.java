package com.mmm.feature;

import com.mmm.config.Configs;
import com.mmm.legacy.visual.BreakIndicatorState;
import com.mmm.legacy.visual.VisualColors;
import com.mmm.legacy.visual.WorldBoxes;
import com.mmm.legacy.visual.mixin.WorldRendererAccessor;
import com.mmm.mixin.ClientPlayerInteractionManagerAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.HitResult;

public final class BreakingIndicatorRenderer {
    private static final BreakIndicatorState COMPLETED = new BreakIndicatorState();
    private static ClientWorld observedWorld;
    private BreakingIndicatorRenderer() {}
    public static void reset() { COMPLETED.clear(); observedWorld = null; }
    public static void completed(Minecraft client, BlockPos pos, BlockState before) {
        if (!Configs.Generic.BREAKING_INDICATORS.getBooleanValue() || before == null || client.world == null) return;
        reconcileWorld(client);
        Box box = before.getShape(client.world, pos);
        if (box != null && before.getMaterial() != Material.AIR)
            COMPLETED.complete(pos, box.move(pos), System.nanoTime());
    }
    private static void reconcileWorld(Minecraft client) {
        if (observedWorld != client.world) { COMPLETED.clear(); observedWorld = client.world; }
    }
    public static void render(Minecraft client, float partialTick) {
        reconcileWorld(client);
        if (!Configs.Generic.BREAKING_INDICATORS.getBooleanValue()) { COMPLETED.clear(); return; }
        if (client.world == null || client.player == null || client.interactionManager == null || client.getCamera() == null) return;
        Map<BlockPos, Float> progress = new LinkedHashMap<>();
        ClientPlayerInteractionManagerAccessor own = (ClientPlayerInteractionManagerAccessor) client.interactionManager;
        if (own.mmm$isBreakingBlock()) {
            BlockPos pos = own.mmm$getCurrentBreakingPos();
            float value = own.mmm$getCurrentBreakingProgress();
            if ((pos == null || value <= 0) && client.options.attackKey.isPressed()
                    && client.crosshairTarget != null && client.crosshairTarget.type == HitResult.Type.BLOCK) {
                pos = client.crosshairTarget.getPos();
                value = 0.12F;
            }
            if (pos != null && value > 0) progress.put(pos, value);
        }
        for (var entry : ((WorldRendererAccessor) client.worldRenderer).mmm$getMiningProgress().entrySet()) {
            var info = entry.getValue();
            if (entry.getKey() == client.player.getNetworkId() || info.getProgress() < 0 || info.getProgress() > 9) continue;
            if (progress.size() >= BreakIndicatorState.LIMIT) break;
            progress.merge(info.getPos(), (info.getProgress() + 1) / 10.0F, Math::max);
        }
        long now = System.nanoTime();
        var completed = COMPLETED.active(now);
        for (var entry : progress.entrySet()) {
            BlockPos pos = entry.getKey();
            if (completed.containsKey(pos) || !nearCamera(client, pos)) continue;
            var state = client.world.getBlockState(pos);
            if (state.getMaterial() != Material.AIR) {
                Box box = state.getOutlineShape(client.world, pos);
                if (box != null) draw(client, box, entry.getValue(), 1, partialTick);
            }
        }
        for (var entry : completed.entrySet())
            if (nearCamera(client, entry.getKey()))
                draw(client, entry.getValue().bounds(), 1, entry.getValue().opacity(now), partialTick);
    }
    private static boolean nearCamera(Minecraft client, BlockPos pos) {
        var camera = client.getCamera();
        double x = pos.getX() + 0.5 - camera.x, y = pos.getY() + 0.5 - camera.y, z = pos.getZ() + 0.5 - camera.z;
        return x*x + y*y + z*z <= 4096;
    }
    private static void draw(Minecraft client, Box box, float progress, float opacity, float partialTick) {
        int color = VisualColors.interpolate(
                VisualColors.parse(Configs.Generic.BREAKING_INDICATOR_START_HEX_COLOR.getStringValue(), Configs.Generic.DEFAULT_BREAKING_INDICATOR_START_HEX_COLOR),
                VisualColors.parse(Configs.Generic.BREAKING_INDICATOR_END_HEX_COLOR.getStringValue(), Configs.Generic.DEFAULT_BREAKING_INDICATOR_END_HEX_COLOR), progress);
        WorldBoxes.render(WorldBoxes.cameraRelative(client, BreakIndicatorState.scaledBounds(box, progress), partialTick),
                VisualColors.alpha(color, 0.25F * opacity), VisualColors.alpha(color, opacity), 2, true);
    }
}

