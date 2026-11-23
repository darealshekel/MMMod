package com.mmm.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Menu-only chrome. HUD renderers retain their own palette and coordinate space. */
public final class BoardMenuChrome {
    private BoardMenuChrome() {}

    static void header(GuiGraphicsExtractor g, Font font, int width, String section) {
        header(g, font, width, BoardEditorStyle.contentX(width, 840), section);
    }

    public static void header(GuiGraphicsExtractor g, Font font, int width, int left, String section) {
        int bottom = BoardEditorStyle.HEADER_HEIGHT;
        g.fill(0, 0, width, bottom, MmmUi.menuSurface(BoardEditorStyle.FLOOR));
        g.fill(0, bottom - 1, width, bottom, BoardEditorStyle.LINE);
        int x = left;
        g.text(font, "MMM", x, 11, BoardEditorStyle.ROSE, false);
        x += font.width("MMM") + 7;
        g.text(font, "/", x, 11, BoardEditorStyle.MUTED, false);
        x += font.width("/") + 7;
        MmmUi.drawTextWithin(g, font, section, x, 11, width - left - x - 54, BoardEditorStyle.TEXT, false);
    }

    public static net.minecraft.client.gui.components.Button backButton(net.minecraft.client.gui.screens.Screen screen, int right) {
        return net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Back"), button -> screen.onClose())
                .bounds(right - 48, 6, 48, 18).build();
    }

    public static int focusInk() {
        int accent = MmmUi.accent();
        return accent == MmmUi.RED ? BoardEditorStyle.ROSE : accent;
    }

    /** Ledger sections use a single index rule, not a box inside a box. */
    public static void section(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean inspector) {
        if (w <= 0 || h <= 0) return;
        if (inspector) g.fill(x, y + 1, x + w, y + h, MmmUi.menuSurface(0xff211e20));
        g.fill(x, y, x + w, y + 1, inspector ? 0xff654146 : BoardEditorStyle.LINE);
        g.fill(x, y, x + Math.min(16, w), y + 2, inspector ? focusInk() : 0xff8a656a);
    }

    public static void record(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        if (w <= 0 || h <= 0) return;
        if (selected || hovered) {
            g.fill(x, y, x+w, y+h, MmmUi.menuSurface(selected ? CollectionBoardArt.ACTIVE : BoardEditorStyle.TILE));
            if (selected) g.fill(x, y+2, x+2, y+h-2, focusInk());
        } else {
            g.fill(x, y+h-1, x+w, y+h, BoardEditorStyle.LINE);
        }
    }

    public static void metric(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y + 5, x + 1, y + h - 5, BoardEditorStyle.LINE);
    }

    public static void choice(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean chosen, boolean hovered) {
        g.fill(x, y, x+w, y+h, MmmUi.menuSurface(chosen ? CollectionBoardArt.ACTIVE : BoardEditorStyle.TILE));
        g.fill(x, y+h-1, x+w, y+h, chosen || hovered ? focusInk() : BoardEditorStyle.LINE);
    }

    static void editor(GuiGraphicsExtractor g, Font font, int width, int height, String section) {
        MmmUi.backdrop(g, width, height);
        header(g, font, width, section);
    }
}
