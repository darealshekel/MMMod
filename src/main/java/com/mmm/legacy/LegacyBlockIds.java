package com.mmm.legacy;

import net.minecraft.block.state.BlockState;
import net.minecraft.block.Block;

/** Converts pre-flattening block variants to the website's canonical block IDs. */
public final class LegacyBlockIds {
    private static final String[] STONE = {
            "stone", "granite", "polished_granite", "diorite",
            "polished_diorite", "andesite", "polished_andesite"
    };
    private static final String[] WOOD = { "oak", "spruce", "birch", "jungle" };
    private static final String[] WOOD_TWO = { "acacia", "dark_oak" };
    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private LegacyBlockIds() {}

    public static boolean hasAmbiguousMinedStat(String id) {
        return switch (id) {
            case "minecraft:stone", "minecraft:dirt", "minecraft:sand", "minecraft:sandstone", "minecraft:red_sandstone",
                 "minecraft:log", "minecraft:log2", "minecraft:leaves", "minecraft:leaves2",
                 "minecraft:stained_hardened_clay", "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block" -> true;
            default -> false;
        };
    }

    public static String canonicalId(BlockState state) {
        if (state == null) return "";
        Block block = state.getBlock();
        return canonicalId(Block.REGISTRY.getKey(block).toString(), block.getMetadataFromState(state));
    }

    public static String canonicalId(String id, int metadata) {
        if (id == null || !id.startsWith("minecraft:")) return id == null ? "" : id;
        String name = id.substring("minecraft:".length());
        String canonical = switch (name) {
            case "stone" -> variant(STONE, metadata, "stone");
            case "dirt" -> variant(new String[] { "dirt", "coarse_dirt", "podzol" }, metadata, "dirt");
            case "sand" -> metadata == 1 ? "red_sand" : "sand";
            case "sandstone" -> variant(new String[] { "sandstone", "chiseled_sandstone", "cut_sandstone" }, metadata, "sandstone");
            case "red_sandstone" -> variant(new String[] { "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone" }, metadata, "red_sandstone");
            case "log" -> variant(WOOD, metadata & 3, "oak") + "_log";
            case "log2" -> variant(WOOD_TWO, metadata & 3, "acacia") + "_log";
            case "leaves" -> variant(WOOD, metadata & 3, "oak") + "_leaves";
            case "leaves2" -> variant(WOOD_TWO, metadata & 3, "acacia") + "_leaves";
            case "stained_hardened_clay" -> variant(COLORS, metadata, "white") + "_terracotta";
            case "hardened_clay" -> "terracotta";
            case "brown_mushroom_block", "red_mushroom_block" ->
                    metadata == 10 || metadata == 15 ? "mushroom_stem" : name;
            case "grass" -> "grass_block";
            case "snow" -> "snow_block";
            case "snow_layer" -> "snow";
            case "mob_spawner" -> "spawner";
            case "magma" -> "magma_block";
            case "nether_brick" -> "nether_bricks";
            case "quartz_ore" -> "nether_quartz_ore";
            case "lit_redstone_ore" -> "redstone_ore";
            default -> name;
        };
        return "minecraft:" + canonical;
    }

    private static String variant(String[] names, int metadata, String fallback) {
        return metadata >= 0 && metadata < names.length ? names[metadata] : fallback;
    }
}
