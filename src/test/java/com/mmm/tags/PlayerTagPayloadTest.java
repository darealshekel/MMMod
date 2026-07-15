package com.mmm.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayerTagPayloadTest
{
    @Test
    void parsesCanonicalTagPayload()
    {
        Map<String, PlayerTagData> tags = PlayerTagPayload.parse("""
                {"tags":[{"username":"5hekel","totalBlocks":16214598,"color":"#FFB300"}]}
                """);

        PlayerTagData tag = tags.get("5hekel");
        assertEquals(16_214_598L, tag.totalBlocks());
        assertEquals(0xFFB300, tag.colorRgb());
        assertEquals("16.2M", PlayerTagPayload.formatBlocks(tag.totalBlocks()));
    }

    @Test
    void abbreviatesTagTotalsIndependentlyOfHudFormatting()
    {
        assertEquals("999", PlayerTagPayload.formatBlocks(999L));
        assertEquals("1.2k", PlayerTagPayload.formatBlocks(1_200L));
        assertEquals("16.2M", PlayerTagPayload.formatBlocks(16_214_598L));
        assertEquals("18.5M", PlayerTagPayload.formatBlocks(18_500_000L));
        assertEquals("2.5B", PlayerTagPayload.formatBlocks(2_500_000_000L));
        assertEquals("1T", PlayerTagPayload.formatBlocks(1_000_000_000_000L));
    }

    @Test
    void parsesCanonicalLeaderboardFallbackPayload()
    {
        Map<String, PlayerTagData> tags = PlayerTagPayload.parseLeaderboard("""
                {"rows":[{"username":"5hekel","blocksMined":16214598}]}
                """);

        assertEquals(16_214_598L, tags.get("5hekel").totalBlocks());
        assertEquals(0xFFB300, tags.get("5hekel").colorRgb());
        assertTrue(PlayerTagPayload.isLeaderboardPayload("{\"rows\":[]}"));
        assertTrue(PlayerTagPayload.isTagPayload("{\"tags\":[]}"));
        assertFalse(PlayerTagPayload.isTagPayload("<!doctype html>"));
    }

    @Test
    void findsPlayerInsideTeamDecoratedNameWithoutMatchingSubstrings()
    {
        assertEquals("SheronMan", PlayerTagPayload.findKnownUsername("[OWNER] SheronMan", List.of("SheronMan", "hero")));
        assertTrue(PlayerTagPayload.findKnownUsername("NotSheronManExtra", List.of("SheronMan")).isBlank());
    }
}
