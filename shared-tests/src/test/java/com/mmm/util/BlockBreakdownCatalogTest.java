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
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:ice"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:cyan_terracotta"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:sulfur"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:cinnabar"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:poplar_log"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:poplar_leaves"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:sculk"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:pale_moss_block"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:diorite"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:glowstone"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:shroomlight"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:raw_iron_block"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:scaffolding"));
        assertTrue(BlockBreakdownCatalog.isValid("minecraft:flowering_azalea_leaves"));
        assertFalse(BlockBreakdownCatalog.isValid("minecraft:polished_blackstone_bricks"));
        assertFalse(BlockBreakdownCatalog.isValid("minecraft:command_block"));
    }
}
