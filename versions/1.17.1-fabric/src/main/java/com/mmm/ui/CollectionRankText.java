package com.mmm.ui;

import com.mmm.tracker.CollectionRankClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CollectionRankText {
    private CollectionRankText() {}
    public static Text format(CollectionRankClient.Rank rank) {
        return switch (rank.status()) {
            case RANKED -> BoardPlatform.literal("Synced rank ").formatted(Formatting.GRAY)
                    .append(BoardPlatform.literal("#" + rank.position()).formatted(Formatting.AQUA))
                    .append(BoardPlatform.literal(" / Top ").formatted(Formatting.GRAY))
                    .append(BoardPlatform.literal(rank.topPercent()).formatted(Formatting.YELLOW));
            case LOADING -> BoardPlatform.literal("Loading website rank...").formatted(Formatting.GRAY);
            case UNRANKED -> BoardPlatform.literal("Not ranked on the website yet").formatted(Formatting.GRAY);
            case UNSUPPORTED -> BoardPlatform.literal("No website ranking for this collection").formatted(Formatting.GRAY);
            case UNAVAILABLE -> BoardPlatform.literal("Website rank unavailable - retrying shortly").formatted(Formatting.GRAY);
        };
    }
}
