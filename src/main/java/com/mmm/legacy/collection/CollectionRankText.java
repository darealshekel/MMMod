package com.mmm.legacy.collection;

import com.mmm.tracker.CollectionRankClient;

public final class CollectionRankText {
    private CollectionRankText() {}
    public static String format(CollectionRankClient.Rank rank) {
        return switch (rank.status()) {
            case RANKED -> "\u00a77Synced rank \u00a7b#" + rank.position() + "\u00a77 / Top \u00a7e" + rank.topPercent();
            case LOADING -> "\u00a77Loading website rank...";
            case UNRANKED -> "\u00a77Not ranked on the website yet";
            case UNSUPPORTED -> "\u00a77No website ranking for this collection";
            case UNAVAILABLE -> "\u00a77Website rank unavailable - retrying shortly";
        };
    }
}
