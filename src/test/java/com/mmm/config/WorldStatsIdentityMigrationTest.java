package com.mmm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mmm.storage.WorldIdentity;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorldStatsIdentityMigrationTest
{
    @AfterEach
    void clearWorldStats()
    {
        Configs.WORLD_STATS.clear();
    }

    @Test
    void legacyAndHashedProfilesMergeWithoutAddingCumulativeTotals()
    {
        Configs.WorldStatsEntry legacy = world("smp.cosymc.win", 1_502_434L, 100L, 10L);
        Configs.WorldStatsEntry current = world(
                WorldIdentity.multiplayerWorldId("smp.cosymc.win"),
                1_508_454L,
                110L,
                20L);
        current.lastSeenAt = 20L;
        Configs.WORLD_STATS.add(legacy);
        Configs.WORLD_STATS.add(current);

        assertTrue(Configs.mergeCanonicalWorldStats());
        assertEquals(1, Configs.WORLD_STATS.size());

        Configs.WorldStatsEntry merged = Configs.WORLD_STATS.getFirst();
        assertEquals("server_dffdebc6ee0e", merged.worldId);
        assertEquals(1_508_454L, merged.totalBlocks);
        assertEquals(110L, merged.blockBreakdown.get("minecraft:stone"));
        assertEquals(20L, merged.blockBreakdown.get("minecraft:dirt"));
        assertTrue(Configs.getLegacyWorldIds(merged.worldId).contains("smp.cosymc.win"));
    }

    private static Configs.WorldStatsEntry world(String worldId, long total, long stone, long dirt)
    {
        Configs.WorldStatsEntry entry = new Configs.WorldStatsEntry();
        entry.worldId = worldId;
        entry.displayName = "C.O.S.Y";
        entry.kind = "multiplayer";
        entry.host = "smp.cosymc.win";
        entry.totalBlocks = total;
        entry.lastSeenAt = 10L;
        entry.blockBreakdown = new LinkedHashMap<>();
        entry.blockBreakdown.put("minecraft:stone", stone);
        entry.blockBreakdown.put("minecraft:dirt", dirt);
        return entry;
    }
}
