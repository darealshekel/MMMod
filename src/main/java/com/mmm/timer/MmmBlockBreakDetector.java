package com.mmm.timer;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public final class MmmBlockBreakDetector {
    private static final ConfirmedBreaks<BlockPos, BreakAttempt> PENDING = new ConfirmedBreaks<>();

    private MmmBlockBreakDetector() {}

    public record BreakAttempt(ClientWorld world, BlockPos position, BlockState state) {}

    public static BreakAttempt beginBreak(BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (pos == null || !eligible(client) || client.world == null) return null;
        BlockState state = client.world.getBlockState(pos);
        if (state.getMaterial() == Material.AIR) return null;
        return new BreakAttempt(client.world, pos.immutable(), state);
    }

    public static void completeBreak(BreakAttempt attempt, boolean success) {
        if (success && attempt != null && Minecraft.getInstance().world == attempt.world())
            PENDING.completed(attempt.world(), attempt.position(), attempt, System.currentTimeMillis());
    }

    public static void acknowledged(BlockPos pos, BlockState state) {
        Minecraft client = Minecraft.getInstance();
        Material material = state.getMaterial();
        boolean removed = material == Material.AIR || material == Material.WATER || material == Material.LAVA;
        BreakAttempt attempt = PENDING.confirm(client.world, pos, removed, System.currentTimeMillis());
        if (attempt != null && eligible(client))
            MiningStats.recordBlockMined(attempt.state().getBlock(), pos, attempt.state());
    }

    public static void onClientTick(Minecraft client) {
        PENDING.expire(client.world, System.currentTimeMillis());
    }

    public static void clear() { PENDING.clear(); }

    private static boolean eligible(Minecraft client) {
        return client.player != null && client.interactionManager != null
                && client.interactionManager.getGameMode() == GameMode.SURVIVAL
                && FeatureToggle.MMM_MINING_TRACKER.getBooleanValue();
    }
}
