package com.mmm.timer;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.util.BlockBreakdownCatalog;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;

public final class MmmBlockBreakDetector
{
    private static final ConfirmedBreaks<BlockPos, BreakAttempt> PENDING = new ConfirmedBreaks<>();

    private MmmBlockBreakDetector()
    {
    }

    public record BreakAttempt(ClientLevel world, BlockPos position, BlockState state, boolean nonInstant) {}

    public static BreakAttempt beginBreak(BlockPos pos)
    {
        if (FeatureToggle.MMM_MINING_TRACKER.getBooleanValue() == false || pos == null)
        {
            return null;
        }

        Minecraft client = Minecraft.getInstance();
        if (isSurvivalMiningMode(client) == false || client.level == null)
        {
            return null;
        }

        BlockState state = client.level.getBlockState(pos);
        if (state == null || state.isAir())
        {
            return null;
        }

        Block block = state.getBlock();
        if (BlockBreakdownCatalog.isValid(block) == false)
        {
            return null;
        }

        float breakingDelta = state.getDestroyProgress(client.player, client.level, pos);
        boolean nonInstant = block == net.minecraft.world.level.block.Blocks.DEEPSLATE && breakingDelta > 0 && breakingDelta < 1;
        return new BreakAttempt(client.level, pos.immutable(), state, nonInstant);
    }

    public static void completeBreak(BreakAttempt attempt, boolean success)
    {
        Minecraft client = Minecraft.getInstance();
        if (success && attempt != null && client.level == attempt.world())
            PENDING.completed(attempt.world(), attempt.position(), attempt, System.currentTimeMillis());
    }

    public static void acknowledged(ClientLevel world, BlockPos pos, BlockState serverState)
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level != world) return;
        boolean removed = serverState.isAir() || serverState.is(Blocks.WATER) || serverState.is(Blocks.LAVA);
        BreakAttempt attempt = PENDING.confirm(world, pos, removed, System.currentTimeMillis());
        if (attempt != null && FeatureToggle.MMM_MINING_TRACKER.getBooleanValue() && isSurvivalMiningMode(client))
            MiningStats.recordBlockMined(attempt.state().getBlock(), pos, attempt.state(), attempt.nonInstant());
    }

    public static void onClientTick(Minecraft client)
    {
        PENDING.expire(client == null ? null : client.level, System.currentTimeMillis());
    }

    public static void clear()
    {
        PENDING.clear();
    }

    private static boolean isSurvivalMiningMode(Minecraft client)
    {
        if (client == null || client.player == null || client.gameMode == null)
        {
            return false;
        }

        GameType gameMode = client.gameMode.getPlayerMode();
        return gameMode == GameType.SURVIVAL && client.player.isCreative() == false && client.player.isSpectator() == false;
    }

}
