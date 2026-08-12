package com.mmm.timer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.mmm.config.FeatureToggle;
import com.mmm.tracker.MiningStats;
import com.mmm.util.BlockBreakdownCatalog;

public final class MmmBlockBreakDetector
{
    private static final long TRACKED_BLOCK_TTL_MS = 10_000L;
    private static final Map<BlockPos, TrackedBlock> trackedBlocks = new LinkedHashMap<>();

    private MmmBlockBreakDetector()
    {
    }

    public static void trackAttack(BlockPos pos)
    {
        if (FeatureToggle.MMM_MINING_TRACKER.getBooleanValue() == false || pos == null)
        {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (isSurvivalMiningMode(client) == false || client.level == null)
        {
            return;
        }

        BlockState state = client.level.getBlockState(pos);
        if (state == null || state.isAir())
        {
            return;
        }

        Block block = state.getBlock();
        if (BlockBreakdownCatalog.isValid(block) == false)
        {
            return;
        }

        trackedBlocks.put(pos.immutable(), new TrackedBlock(block, state, System.currentTimeMillis()));
    }

    public static void onClientTick(Minecraft client)
    {
        if (client == null || client.level == null || client.player == null || trackedBlocks.isEmpty())
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

            if (client.level.getBlockState(pos).isAir())
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

    private static boolean isSurvivalMiningMode(Minecraft client)
    {
        if (client == null || client.player == null || client.gameMode == null)
        {
            return false;
        }

        GameType gameMode = client.gameMode.getPlayerMode();
        return gameMode == GameType.SURVIVAL && client.player.isCreative() == false && client.player.isSpectator() == false;
    }

    private record TrackedBlock(Block block, BlockState state, long createdAtMs) {}
}
