package com.mmm.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("16,214,598", PlayerTagPayload.formatBlocks(tag.totalBlocks()));
    }

    @Test
    void findsPlayerInsideTeamDecoratedNameWithoutMatchingSubstrings()
    {
        assertEquals("SheronMan", PlayerTagPayload.findKnownUsername("[OWNER] SheronMan", List.of("SheronMan", "hero")));
        assertTrue(PlayerTagPayload.findKnownUsername("NotSheronManExtra", List.of("SheronMan")).isBlank());
    }
}
