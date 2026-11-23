package com.mmm.ui;

/** Tier numbers are one-based; zero follows the next milestone. */
public final class CollectionBoardState {
    private int inspected;

    public int inspectedTier() { return inspected; }
    public void reset() { inspected = 0; }
    public void toggleTier(int tier) {
        if (tier > 0) inspected = inspected == tier ? 0 : tier;
    }
    public int displayedTier(int earned, int tierCount) {
        if (tierCount <= 0) return 0;
        return inspected > 0 ? Math.min(inspected, tierCount)
                : earned >= tierCount ? tierCount : Math.max(1, earned + 1);
    }
    public static float reveal(long elapsedMs, int order) {
        float t = Math.max(0f, Math.min(1f, (elapsedMs - Math.max(0, Math.min(order, 10)) * 16L) / 185f));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
