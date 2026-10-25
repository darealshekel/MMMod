package com.mmm.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MmmChatIgnoreListTest
{
    @Test
    void validatesMinecraftUsernames()
    {
        assertTrue(MmmChatIgnoreList.isValidUsername("_SleepyZZZ"));
        assertFalse(MmmChatIgnoreList.isValidUsername("player name"));
        assertFalse(MmmChatIgnoreList.isValidUsername("seventeen_chars__"));
    }

    @Test
    void normalizesDuplicateNamesCaseInsensitively()
    {
        assertEquals(List.of("Another", "Player_1"), MmmChatIgnoreList.normalizeEntries(
                List.of("Player_1", "player_1", "invalid name", "Another")));
    }
}
