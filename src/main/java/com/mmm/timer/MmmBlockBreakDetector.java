package com.mmm.timer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.util.BlockBreakdownCatalog;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public final class MmmBlockBreakDetector
{
    private static final long TRACKED_BLOCK_TTL_MS = 10_000L;
    private static final Map<BlockPos, TrackedBlock> trackedBlocks = new LinkedHashMap<>();

    private MmmBlockBreakDetector()
    {
    }

    public static void trackAttack(BlockPos pos)
    {
        if (FeatureToggle.TWEAK_MINING_TRACKER.getBooleanValue() == false || pos == null)
        {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (isSurvivalMiningMode(client) == false || client.world == null)
        {
            return;
        }

        BlockState state = client.world.getBlockState(pos);
        if (state == null || state.isAir())
        {
            return;
        }

        Block block = state.getBlock();
        if (BlockBreakdownCatalog.isValid(block) == false)
        {
            return;
        }

        trackedBlocks.put(pos.toImmutable(), new TrackedBlock(block, state, System.currentTimeMillis()));
    }

    public static void onClientTick(MinecraftClient client)
    {
        if (client == null || client.world == null || client.player == null || trackedBlocks.isEmpty())
        {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, TrackedBlock>> iterator = trackedBlocks.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<BlockPos, TrackedBlock> entry = iterator.next();
            BlockPos pos = entry.getKey();
            TrackedBlock tracked = entry.getValue();
            if (now - tracked.createdAtMs() > TRACKED_BLOCK_TTL_MS)
            {
                iterator.remove();
                continue;
            }

            if (client.world.getBlockState(pos).isAir())
            {
                MiningStats.recordBlockMined(tracked.block(), pos, tracked.state());
                iterator.remove();
            }
        }
    }

    public static void clear()
    {
        trackedBlocks.clear();
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

    private record TrackedBlock(Block block, BlockState state, long createdAtMs) {}
}
