package com.mmm.hud;

import com.mmm.tracker.CollectionProgress;
import com.mmm.tracker.CollectionRankClient;
import java.util.Map;

/** Each accepted break extends visibility; only a block-type change replaces the icon. */
public final class MinedBlockOverlayState
{
    private static final long HOLD_NANOS = 10_000_000_000L;
    private static final long FADE_NANOS = 800_000_000L;
    private String blockId;
    private long lastMinedAt;
    private long selectedAt;

    public boolean mined(String id, long now)
    {
        if (id == null || id.isBlank()) return false;
        lastMinedAt = now;
        if (id.equals(blockId)) return false;
        blockId = id;
        selectedAt = now;
        return true;
    }

    public String blockId() { return blockId; }

    public boolean rankRequestReady(long now)
    {
        return blockId != null && now - selectedAt >= 750_000_000L && opacity(now) > 0;
    }

    public static String rankLabel(CollectionRankClient.Rank rank)
    {
        return rank != null && rank.status() == CollectionRankClient.Status.RANKED && rank.position() > 0
                ? " #" + rank.position() : "";
    }

    public float opacity(long now)
    {
        if (blockId == null) return 0;
        long elapsed = Math.max(0, now - lastMinedAt);
        if (elapsed <= HOLD_NANOS) return 1;
        if (elapsed >= HOLD_NANOS + FADE_NANOS) return 0;
        float remaining = 1F - (elapsed - HOLD_NANOS) / (float) FADE_NANOS;
        return remaining * remaining * remaining;
    }

    public void clear()
    {
        blockId = null;
        lastMinedAt = 0;
        selectedAt = 0;
    }

    public record Layout(float x, float y, float scale, int playtimeY)
    {
        public static final int HEIGHT = 36;

        public static Layout fit(int screenWidth, int screenHeight, int contentWidth)
        {
            return fit(screenWidth, screenHeight, contentWidth, 1, -1, -1);
        }

        public static Layout fit(int screenWidth, int screenHeight, int contentWidth, double requestedScale, int storedX, int storedY)
        {
            float scale = (float) Math.max(0.25, Math.min(3, requestedScale));
            scale = Math.min(scale, Math.min(Math.max(1, screenWidth - 16) / (float) contentWidth,
                    Math.max(1, screenHeight - 16) / (float) HEIGHT));
            float scaledWidth = contentWidth * scale;
            float scaledHeight = HEIGHT * scale;
            int maxX = Math.max(0, screenWidth - Math.round(scaledWidth));
            int maxY = Math.max(0, screenHeight - Math.round(scaledHeight));
            float x = storedX < 0 ? (screenWidth - scaledWidth) / 2
                    : Math.round(maxX * Math.max(0, Math.min(820, storedX)) / 820F);
            float y = storedY < 0 ? Math.min(maxY, Math.max(screenHeight / 2F + 12, screenHeight - 96 - scaledHeight))
                    : Math.round(maxY * Math.max(0, Math.min(460, storedY)) / 460F);
            int playtimeY = Math.max(4, screenHeight - 82);
            if (x < screenWidth / 2F + 70 && x + scaledWidth > screenWidth / 2F - 70
                    && playtimeY + 9 > y - 4 && playtimeY < y + scaledHeight + 4)
                playtimeY = Math.max(4, Math.min((int) y - 14, screenHeight / 2 - 20));
            return new Layout(x, y, scale, playtimeY);
        }
    }

    public record Progress(String collectionId, long count, int tier, long next, long cap)
    {
        public static Progress from(String blockId, Map<String, Long> counts)
        {
            String collection = CollectionProgress.collectionId(blockId);
            long count = Math.max(0, counts.getOrDefault(collection, 0L));
            return new Progress(collection, count, CollectionProgress.completed(collection, count),
                    CollectionProgress.next(collection, count), CollectionProgress.cap(collection));
        }

        public boolean complete() { return next == 0; }
        public double fraction() { return complete() ? 1 : Math.min(1, count / (double) next); }
        public double percentage() { return complete() ? 100 : Math.floor(fraction() * 1000) / 10; }
    }
}
