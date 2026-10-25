package com.mmm.ui;

import com.mmm.tracker.CollectionRankClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CollectionRankText {
    private CollectionRankText() {}
    public static Text format(CollectionRankClient.Rank rank) {
        return switch (rank.status()) {
            case RANKED -> Text.literal("Synced rank ").formatted(Formatting.GRAY)
                    .append(Text.literal("#" + rank.position()).formatted(Formatting.AQUA))
                    .append(Text.literal(" / Top ").formatted(Formatting.GRAY))
                    .append(Text.literal(rank.topPercent()).formatted(Formatting.YELLOW));
            case LOADING -> Text.literal("Loading website rank...").formatted(Formatting.GRAY);
            case UNRANKED -> Text.literal("Not ranked on the website yet").formatted(Formatting.GRAY);
            case UNSUPPORTED -> Text.literal("No website ranking for this collection").formatted(Formatting.GRAY);
            case UNAVAILABLE -> Text.literal("Website rank unavailable - retrying shortly").formatted(Formatting.GRAY);
        };
    }
}
