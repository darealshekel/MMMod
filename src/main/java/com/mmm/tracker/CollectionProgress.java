package com.mmm.tracker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CollectionProgress
{
    public static final List<Long> MILESTONES = List.of(1_000L, 2_500L, 5_000L, 10_000L, 25_000L,
            50_000L, 100_000L, 250_000L, 500_000L, 1_000_000L, 2_500_000L, 5_000_000L,
            10_000_000L, 15_000_000L, 20_000_000L, 25_000_000L);
    public static final List<Long> OVERVIEW_MILESTONES = List.of(100L, 250L, 500L, 750L, 1_000L, 1_250L,
            2_500L, 3_750L, 5_000L, 7_500L, 10_000L, 25_000L, 50_000L, 100_000L,
            250_000L, 500_000L, 1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L,
            15_000_000L, 20_000_000L, 25_000_000L);
    private CollectionProgress() {}

    public static List<Long> milestones(String block) {
        long cap = cap(block);
        if (cap <= 10_000) return List.of(cap / 10, cap / 4, cap / 2, cap * 3 / 4, cap);
        var tiers = new java.util.ArrayList<Long>();
        for (long tier : MILESTONES) if (tier < cap) tiers.add(tier);
        tiers.add(cap);
        return List.copyOf(tiers);
    }
    /** Used only to summarize tiers granted by the 1.0.21 spacing update. */
    static List<Long> legacyMilestones(String block) {
        long cap = cap(block);
        if (cap <= 10_000) return milestones(block);
        var tiers = new java.util.ArrayList<Long>();
        for (long tier : List.of(10_000L, 100_000L, 1_000_000L, 10_000_000L))
            if (tier < cap) tiers.add(tier);
        tiers.add(cap);
        return List.copyOf(tiers);
    }
    public static String tierLabel(int tier) {
        if (tier <= 0) return "";
        if (tier >= 10) return "X" + tierLabel(tier - 10);
        return switch (tier) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV";
            case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII";
            case 9 -> "IX"; default -> "";
        };
    }
    public static long cap(String block) {
        String id = block == null ? "" : block.replace("minecraft:", "");
        if (id.endsWith("_log") || id.equals("crimson_stem") || id.equals("warped_stem")) return 100_000;
        if (id.endsWith("_leaves")) return 250_000;
        if (id.endsWith("terracotta")) return 10_000_000;
        if (id.equals("mmm:coral") || id.endsWith("_coral_block")) return 100_000;
        return switch (id) {
            case "stone", "netherrack" -> 25_000_000;
            case "deepslate", "sand", "basalt", "cinnabar", "sulfur" -> 10_000_000;
            case "dirt", "mud", "sandstone" -> 2_500_000;
            case "mmm:non_insta_deepslate", "grass_block", "mycelium", "podzol", "packed_ice", "end_stone",
                    "warped_nylium", "crimson_nylium", "warped_wart_block", "nether_wart_block", "blackstone",
                    "soul_sand", "soul_soil", "pale_moss_block", "moss_block", "sculk", "red_sand", "gravel",
                    "snow_block", "clay", "dripstone_block", "andesite", "diorite", "granite", "tuff", "scaffolding" -> 1_000_000;
            case "coarse_dirt", "magma_block", "brown_mushroom_block", "red_mushroom_block", "coal_ore", "nether_quartz_ore" -> 250_000;
            case "nether_gold_ore", "iron_ore", "copper_ore", "mangrove_roots", "muddy_mangrove_roots", "ice",
                    "obsidian", "nether_bricks", "mushroom_stem", "calcite" -> 100_000;
            case "redstone_ore", "shroomlight", "glowstone", "smooth_basalt" -> 50_000;
            case "deepslate_iron_ore", "deepslate_redstone_ore", "deepslate_gold_ore", "gold_ore", "rooted_dirt", "amethyst_block" -> 25_000;
            case "deepslate_lapis_ore", "lapis_ore", "ancient_debris", "deepslate_coal_ore", "deepslate_diamond_ore" -> 10_000;
            case "gilded_blackstone", "deepslate_copper_ore" -> 5_000;
            case "diamond_ore", "emerald_ore", "deepslate_emerald_ore", "raw_iron_block", "spawner" -> 1_000;
            default -> 10_000_000;
        };
    }
    public static String targetLabel(long value) {
        if (value < 1_000) return Long.toString(value);
        long divisor = value >= 1_000_000 ? 1_000_000 : 1_000;
        return java.math.BigDecimal.valueOf(value).divide(java.math.BigDecimal.valueOf(divisor))
                .stripTrailingZeros().toPlainString() + (divisor == 1_000_000 ? "M" : "K");
    }
    public static String collectionId(String block) {
        if (block.endsWith("terracotta") && !block.contains("glazed")) return "minecraft:terracotta";
        if (block.endsWith("_coral_block")) return "mmm:coral";
        return block;
    }
    public static String iconId(String collection) {
        return switch (collection) {
            case "mmm:coral" -> "minecraft:tube_coral_block";
            case "mmm:non_insta_deepslate" -> "minecraft:deepslate";
            default -> collection;
        };
    }
    public static String customName(String collection) {
        return switch (collection) {
            case "mmm:coral" -> "Combined Coral";
            case "mmm:non_insta_deepslate" -> "Non-insta Deepslate";
            case "minecraft:terracotta" -> "Combined Terracotta";
            default -> null;
        };
    }
    public static Map<String, Long> groupCounts(Map<String, Long> raw) {
        var grouped = new HashMap<String, Long>();
        raw.forEach((id, count) -> grouped.merge(collectionId(id), Math.max(0, count), CollectionProgress::add));
        return Map.copyOf(grouped);
    }
    public static long add(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
    public static int completed(String block, long blocks) {
        return (int) milestones(block).stream().filter(target -> blocks >= target).count();
    }
    public static long next(String block, long blocks) {
        return milestones(block).stream().filter(target -> blocks < target).findFirst().orElse(0L);
    }
    public static long remaining(String block, long blocks) {
        long next = next(block, blocks);
        return next == 0 ? Long.MAX_VALUE : next - Math.max(0, blocks);
    }

    public static int completed(long blocks) {
        return (int) MILESTONES.stream().filter(target -> blocks >= target).count();
    }
    public static long next(long blocks) {
        return MILESTONES.stream().filter(target -> blocks < target).findFirst().orElse(0L);
    }
    public static long remaining(long blocks) {
        long next = next(blocks);
        return next == 0 ? Long.MAX_VALUE : next - Math.max(0, blocks);
    }

    public record Source(String id, Map<String, Long> blocks) {}

    public static Map<String, Long> aggregate(List<Source> sources) {
        Map<String, Map<String, Long>> worlds = new HashMap<>();
        for (Source source : sources) {
            if (source.id() == null || source.id().isBlank() || source.blocks() == null) continue;
            var world = worlds.computeIfAbsent(source.id(), ignored -> new HashMap<>());
            source.blocks().forEach((block, count) -> {
                if (block != null && count != null && count > 0) world.merge(block, count, Math::max);
            });
        }
        Map<String, Long> totals = new HashMap<>();
        worlds.values().forEach(world -> world.forEach((block, count) ->
                totals.merge(block, count, (a, b) -> Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b)));
        return Map.copyOf(totals);
    }
}
