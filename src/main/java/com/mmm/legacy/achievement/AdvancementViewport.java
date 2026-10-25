package com.mmm.legacy.achievement;

/** Screen-space viewport from 1.12.2's renderTabContents translation and tab mask. */
public record AdvancementViewport(int left, int top, int right, int bottom) {
    public static final int WIDTH = 234;
    public static final int HEIGHT = 113;

    public static AdvancementViewport fromFrame(int frameX, int frameY) {
        int left = frameX + 9;
        int top = frameY + 18;
        return new AdvancementViewport(left, top, left + WIDTH, top + HEIGHT);
    }
}
