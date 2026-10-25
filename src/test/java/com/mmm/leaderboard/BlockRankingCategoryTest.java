package com.mmm.leaderboard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockRankingCategoryTest {
    @Test void obsidianIsNotOverworldButRemainsInOtherGroups() {
        assertFalse(BlockRankingCategory.OVERWORLD.includes("minecraft:obsidian"));
        for (var category : new BlockRankingCategory[] {BlockRankingCategory.ALL, BlockRankingCategory.NETHER,
                BlockRankingCategory.END, BlockRankingCategory.PICKAXE}) assertTrue(category.includes("minecraft:obsidian"));
        assertTrue(BlockRankingCategory.OVERWORLD.includes("minecraft:gravel"));
    }
    @Test void oresIncludeRequestedExtrasAndBothOreVariants() {
        for (String id : new String[] {"diamond_ore", "deepslate_iron_ore", "nether_quartz_ore", "nether_gold_ore",
                "ancient_debris", "raw_iron_block", "gilded_blackstone"}) {
            assertTrue(BlockRankingCategory.ORES.includes("minecraft:" + id), id);
            assertTrue(BlockRankingCategory.PICKAXE.includes("minecraft:" + id), id);
        }
        assertFalse(BlockRankingCategory.ORES.includes("minecraft:stone"));
    }
    @Test void pickaxeExcludesOtherToolGroups() {
        for (String id : new String[] {"stone", "red_terracotta", "spawner", "end_stone", "warped_nylium"})
            assertTrue(BlockRankingCategory.PICKAXE.includes(id), id);
        for (String id : new String[] {"dirt", "oak_log", "oak_leaves", "moss_block", "glowstone"})
            assertFalse(BlockRankingCategory.PICKAXE.includes(id), id);
    }
}
