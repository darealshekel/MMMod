package com.mmm.ui;

import com.mmm.tracker.CollectionRankClient;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class CollectionRankText {
    private CollectionRankText() {}
    public static Component format(CollectionRankClient.Rank rank) {
        return switch (rank.status()) {
            case RANKED -> Component.literal("Synced rank ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("#" + rank.position()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" / Top ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(rank.topPercent()).withStyle(ChatFormatting.YELLOW));
            case LOADING -> Component.literal("Loading website rank...").withStyle(ChatFormatting.GRAY);
            case UNRANKED -> Component.literal("Not ranked on the website yet").withStyle(ChatFormatting.GRAY);
            case UNSUPPORTED -> Component.literal("No website ranking for this collection").withStyle(ChatFormatting.GRAY);
            case UNAVAILABLE -> Component.literal("Website rank unavailable - retrying shortly").withStyle(ChatFormatting.GRAY);
        };
    }
}
