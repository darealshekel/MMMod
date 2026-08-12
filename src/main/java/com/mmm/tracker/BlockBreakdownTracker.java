package com.mmm.tracker;

import com.mmm.storage.WorldSessionContext;
import com.mmm.util.BlockBreakdownCatalog;
import com.mmm.util.MmmDebugLogger;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Block;

public final class BlockBreakdownTracker
{
    private static final long REQUEST_DELAY_MS = 1_000L;
    private static final long STATS_DEBUG_LOG_INTERVAL_MS = 30_000L;
    private static boolean statsRequestPending;
    private static long nextStatsRequestAtMs;
    private static String pendingWorldId = "";

    private BlockBreakdownTracker()
    {
    }

    public static void requestStatsOnWorldJoin()
    {
        statsRequestPending = true;
        nextStatsRequestAtMs = System.currentTimeMillis() + REQUEST_DELAY_MS;
        pendingWorldId = WorldSessionContext.getCurrentWorldId();
    }

    public static void onClientTick(Minecraft client, long now)
    {
        if (statsRequestPending == false || now < nextStatsRequestAtMs)
        {
            return;
        }

        if (client == null || client.level == null || client.player == null || client.getConnection() == null)
        {
            nextStatsRequestAtMs = now + 500L;
            return;
        }

        if (pendingWorldId.isBlank() == false && pendingWorldId.equals(WorldSessionContext.getCurrentWorldId()) == false)
        {
            statsRequestPending = false;
            return;
        }

        client.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        statsRequestPending = false;

        MmmDebugLogger.info(
                "requested-vanilla-mined-stats",
                STATS_DEBUG_LOG_INTERVAL_MS,
                "[MMM_DEBUG] requested-vanilla-mined-stats sourceName={}",
                WorldSessionContext.getCurrentWorldName());
    }

    public static void captureVanillaStats(Minecraft client, long now)
    {
        if (client == null || client.player == null)
        {
            return;
        }

        Map<String, Long> minedBlocks = new LinkedHashMap<>();
        for (Block block : BuiltInRegistries.BLOCK)
        {
            int count = client.player.getStats().getValue(Stats.BLOCK_MINED, block);
            if (count <= 0)
            {
                continue;
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (BlockBreakdownCatalog.isValid(blockId))
            {
                minedBlocks.put(blockId, (long) count);
            }
        }

        if (minedBlocks.isEmpty())
        {
            return;
        }

        MiningStats.applyMinecraftStatsBlockBreakdown(minedBlocks, now);

        long total = minedBlocks.values().stream().mapToLong(Long::longValue).sum();
        MmmDebugLogger.info(
                "captured-vanilla-mined-stats",
                STATS_DEBUG_LOG_INTERVAL_MS,
                "[MMM_DEBUG] captured-vanilla-mined-stats sourceName={} blockTypes={} total={}",
                WorldSessionContext.getCurrentWorldName(),
                minedBlocks.size(),
                total);
    }
}
