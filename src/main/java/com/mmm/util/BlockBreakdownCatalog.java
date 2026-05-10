package com.mmm.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

public final class BlockBreakdownCatalog
{
    private static final Set<String> VALID_BLOCK_IDS = Set.of(
            "minecraft:sand",
            "minecraft:gravel",
            "minecraft:stone",
            "minecraft:deepslate",
            "minecraft:tuff",
            "minecraft:dirt",
            "minecraft:diorite",
            "minecraft:andesite",
            "minecraft:granite",
            "minecraft:grass_block",
            "minecraft:copper_ore",
            "minecraft:coal_ore",
            "minecraft:iron_ore",
            "minecraft:deepslate_redstone_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:sandstone",
            "minecraft:calcite",
            "minecraft:deepslate_iron_ore",
            "minecraft:deepslate_lapis_ore",
            "minecraft:lapis_ore",
            "minecraft:gold_ore",
            "minecraft:deepslate_copper_ore",
            "minecraft:nether_quartz_ore",
            "minecraft:ancient_debris",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore",
            "minecraft:deepslate_coal_ore",
            "minecraft:netherrack",
            "minecraft:basalt",
            "minecraft:blackstone",
            "minecraft:soul_sand",
            "minecraft:soul_soil",
            "minecraft:end_stone",
            "minecraft:obsidian",
            "minecraft:snow_block",
            "minecraft:podzol",
            "minecraft:mycelium",
            "minecraft:crimson_nylium",
            "minecraft:warped_nylium",
            "minecraft:dripstone_block",
            "minecraft:packed_ice",
            "minecraft:magma_block",
            "minecraft:nether_bricks",
            "minecraft:polished_blackstone_bricks",
            "minecraft:glowstone",
            "minecraft:shroomlight",
            "minecraft:clay",
            "minecraft:spawner",
            "minecraft:raw_iron_block",
            "minecraft:gilded_blackstone",
            "minecraft:smooth_basalt",
            "minecraft:amethyst_block",
            "minecraft:scaffolding",
            "minecraft:oak_log",
            "minecraft:spruce_log",
            "minecraft:birch_log",
            "minecraft:jungle_log",
            "minecraft:acacia_log",
            "minecraft:dark_oak_log",
            "minecraft:mangrove_log",
            "minecraft:cherry_log",
            "minecraft:pale_oak_log",
            "minecraft:crimson_stem",
            "minecraft:warped_stem",
            "minecraft:mangrove_roots",
            "minecraft:muddy_mangrove_roots",
            "minecraft:red_mushroom_block",
            "minecraft:brown_mushroom_block",
            "minecraft:mushroom_stem",
            "minecraft:oak_leaves",
            "minecraft:spruce_leaves",
            "minecraft:birch_leaves",
            "minecraft:jungle_leaves",
            "minecraft:acacia_leaves",
            "minecraft:dark_oak_leaves",
            "minecraft:mangrove_leaves",
            "minecraft:cherry_leaves",
            "minecraft:pale_oak_leaves",
            "minecraft:azalea_leaves",
            "minecraft:flowering_azalea_leaves",
            "minecraft:nether_wart_block",
            "minecraft:warped_wart_block",
            "minecraft:redstone_ore",
            "minecraft:diamond_ore",
            "minecraft:nether_gold_ore"
    );

    private BlockBreakdownCatalog()
    {
    }

    public static boolean isValid(Block block)
    {
        return isValid(blockId(block));
    }

    public static boolean isValid(String blockId)
    {
        return blockId != null && VALID_BLOCK_IDS.contains(blockId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static String blockId(Block block)
    {
        if (block == null)
        {
            return "";
        }
        return Registries.BLOCK.getId(block).toString();
    }

    public static Map<String, Long> sanitize(Map<String, Long> breakdown)
    {
        Map<String, Long> combined = new LinkedHashMap<>();
        if (breakdown == null)
        {
            return combined;
        }

        for (Map.Entry<String, Long> entry : breakdown.entrySet())
        {
            if (isValid(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0L)
            {
                String key = entry.getKey().trim().toLowerCase(java.util.Locale.ROOT);
                combined.merge(key, entry.getValue(), Long::sum);
            }
        }

        Map<String, Long> sanitized = new LinkedHashMap<>();
        combined.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(java.util.Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sanitized.put(entry.getKey(), entry.getValue()));
        return sanitized;
    }
}
