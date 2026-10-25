package com.mmm.timer;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.util.BlockBreakdownCatalog;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public final class MmmBlockBreakDetector
{
    private static final ConfirmedBreaks<BlockPos, BreakAttempt> PENDING = new ConfirmedBreaks<>();

    private MmmBlockBreakDetector()
    {
    }

    public record BreakAttempt(ClientWorld world, BlockPos position, BlockState state, boolean nonInstant) {}

    public static BreakAttempt beginBreak(BlockPos pos)
    {
        if (FeatureToggle.MMM_MINING_TRACKER.getBooleanValue() == false || pos == null)
        {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (isSurvivalMiningMode(client) == false || client.world == null)
        {
            return null;
        }

        BlockState state = client.world.getBlockState(pos);
        if (state == null || state.isAir())
        {
            return null;
        }

        Block block = state.getBlock();
        if (BlockBreakdownCatalog.isValid(block) == false)
        {
            return null;
        }

        float breakingDelta = state.calcBlockBreakingDelta(client.player, client.world, pos);
        boolean nonInstant = block == net.minecraft.block.Blocks.DEEPSLATE && breakingDelta > 0 && breakingDelta < 1;
        return new BreakAttempt(client.world, pos.toImmutable(), state, nonInstant);
    }

    public static void completeBreak(BreakAttempt attempt, boolean success)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (success && attempt != null && client.world == attempt.world())
            PENDING.completed(attempt.world(), attempt.position(), attempt, System.currentTimeMillis());
    }

    public static void acknowledged(ClientWorld world, BlockPos pos, BlockState serverState)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world != world) return;
        boolean removed = serverState.isAir() || serverState.isOf(Blocks.WATER) || serverState.isOf(Blocks.LAVA);
        BreakAttempt attempt = PENDING.confirm(world, pos, removed, System.currentTimeMillis());
        if (attempt != null && FeatureToggle.MMM_MINING_TRACKER.getBooleanValue() && isSurvivalMiningMode(client))
            MiningStats.recordBlockMined(attempt.state().getBlock(), pos, attempt.state());
    }

    public static void onClientTick(MinecraftClient client)
    {
        PENDING.expire(client == null ? null : client.world, System.currentTimeMillis());
    }

    public static void clear()
    {
        PENDING.clear();
    }

    private static boolean isSurvivalMiningMode(MinecraftClient client)
    {
        if (client == null || client.player == null || client.interactionManager == null)
        {
            return false;
        }

        GameMode gameMode = client.interactionManager.getCurrentGameMode();
        return gameMode == GameMode.SURVIVAL && client.player.isCreative() == false && client.player.isSpectator() == false;
    }

}
