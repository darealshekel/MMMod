package com.mmm.leaderboard;

import java.util.Set;

public enum BlockRankingCategory
{
    ALL("All", "diamond_pickaxe"), OVERWORLD("Overworld", "grass_block"), NETHER("Nether", "netherrack"),
    END("End", "end_stone"), ORES("Ores", "diamond_ore"), PICKAXE("Pickaxe", "diamond_pickaxe"),
    SHOVEL("Shovel", "diamond_shovel"), HOE("Hoe", "diamond_hoe"), AXE("Axe", "diamond_axe");
    public final String label, icon;
    BlockRankingCategory(String label, String icon) { this.label = label; this.icon = icon; }
    private static final Set<String> NETHER_IDS = Set.of("nether_quartz_ore", "nether_gold_ore", "ancient_debris", "crimson_stem", "warped_stem",
            "warped_nylium", "crimson_nylium", "nether_bricks", "magma_block", "glowstone", "shroomlight", "warped_wart_block",
            "nether_wart_block", "gilded_blackstone", "netherrack", "gravel", "basalt", "smooth_basalt", "blackstone", "soul_sand", "soul_soil", "obsidian");
    public boolean includes(String block) {
        String id = block.replace("minecraft:", "");
        return switch (this) {
            case ALL -> true;
            case NETHER -> NETHER_IDS.contains(id);
            case END -> Set.of("end_stone", "obsidian").contains(id);
            case OVERWORLD -> (!NETHER_IDS.contains(id) && !id.equals("end_stone")) || Set.of("gravel", "smooth_basalt", "magma_block").contains(id);
            case ORES -> id.endsWith("_ore") || Set.of("ancient_debris", "raw_iron_block", "gilded_blackstone").contains(id);
            case PICKAXE -> ORES.includes(block) || id.endsWith("terracotta") || id.endsWith("_coral_block") || Set.of("mmm:coral", "mmm:non_insta_deepslate", "stone", "diorite", "andesite", "granite",
                    "deepslate", "tuff", "calcite", "dripstone_block", "amethyst_block", "sandstone", "packed_ice", "ice",
                    "spawner", "nether_bricks", "magma_block", "netherrack", "basalt", "smooth_basalt", "blackstone",
                    "crimson_nylium", "warped_nylium", "end_stone", "obsidian", "sulfur", "cinnabar").contains(id);
            case SHOVEL -> Set.of("clay", "coarse_dirt", "rooted_dirt", "mud", "mycelium", "podzol", "red_sand", "grass_block", "dirt", "snow_block", "sand", "gravel", "soul_sand", "soul_soil", "muddy_mangrove_roots").contains(id);
            case HOE -> id.endsWith("_leaves") || Set.of("warped_wart_block", "nether_wart_block", "shroomlight", "moss_block", "pale_moss_block", "sculk").contains(id);
            case AXE -> id.endsWith("_log") || Set.of("crimson_stem", "warped_stem", "mushroom_stem", "brown_mushroom_block", "red_mushroom_block", "mangrove_roots", "scaffolding").contains(id);
        };
    }
}
