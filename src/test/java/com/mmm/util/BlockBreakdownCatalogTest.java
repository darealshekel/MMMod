package com.mmm.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockBreakdownCatalogTest
{
    @Test
    void includesExpandedPublicBreakdownBlocks()
    {
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:red_sand"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:moss_block"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:mud"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:coarse_dirt"));
        assertFalse(BlockBreakdownCatalog.isValid("minecraft:command_block"));
    }
}
