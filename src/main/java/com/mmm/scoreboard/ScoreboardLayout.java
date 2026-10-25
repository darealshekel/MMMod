package com.mmm.scoreboard;

import com.mmm.config.Configs.ScoreboardPosition;

/** Resolution-independent free positioning stores fractions of the available travel, not absolute pixels. */
public final class ScoreboardLayout {
    public static final int NORMALIZED_WIDTH = 820, NORMALIZED_HEIGHT = 460;
    private ScoreboardLayout() {}
    public static int normalize(int value, int travel, int maximum) {
        return travel <= 0 ? 0 : (int) Math.round(Math.max(0, Math.min(travel, value)) * (double) maximum / travel);
    }
    public static ScoreboardHudRenderer.Bounds place(int screenWidth, int screenHeight, int width, int height,
                                                     int storedX, int storedY, ScoreboardPosition anchor, int offset) {
        int maxX = Math.max(0, screenWidth - width), maxY = Math.max(0, screenHeight - height);
        int x, y;
        if (storedX >= 0 && storedY >= 0) {
            x = (int) Math.round(Math.min(NORMALIZED_WIDTH, storedX) * (double) maxX / NORMALIZED_WIDTH);
            y = (int) Math.round(Math.min(NORMALIZED_HEIGHT, storedY) * (double) maxY / NORMALIZED_HEIGHT);
        } else {
            x = anchor.isLeft() ? 3 : maxX - 3;
            y = switch (anchor) {
                case LEFT_UPPER, RIGHT_UPPER -> 3;
                case LEFT_LOWER, RIGHT_LOWER -> maxY - 3;
                default -> maxY / 2;
            };
            y += offset;
        }
        x = Math.max(0, Math.min(maxX, x)); y = Math.max(0, Math.min(maxY, y));
        return new ScoreboardHudRenderer.Bounds(x, y, x + width, y + height);
    }
}

