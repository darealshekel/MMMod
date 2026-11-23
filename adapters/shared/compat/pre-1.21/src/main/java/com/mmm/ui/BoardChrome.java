package com.mmm.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import static com.mmm.ui.BoardEditorStyle.*;

/** Common navigation for tools. The editor's controls never sit under a persistent sidebar. */
public final class BoardChrome {
    public interface Page {}
    private static final int BACK_WIDTH = 48;
    private BoardChrome() {}

    static void header(BoardPlatform.Canvas canvas, int width, int height, String section) {
        var c = canvas.raw();
        var font = MinecraftClient.getInstance().textRenderer;
        int x = contentX(width, 840);
        c.fill(0, 0, width, height, MmmUi.menuSurface(FLOOR));
        c.fill(0, 0, width, HEADER_HEIGHT - 1, MmmUi.menuSurface(SURFACE));
        c.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, LINE);
        c.drawText(font, "MMM", x, 11, ROSE, false);
        MmmUi.drawTextWithin(c, font, "/ " + section, x + 28, 11, contentWidth(width, 840) - 34 - BACK_WIDTH, TEXT, false);
    }

    public static int buttonFace(BoardPlatform.Canvas canvas, int x, int y, int w, int h, boolean active, boolean hot) {
        var c = canvas.raw();
        plate(c::fill, x, y, w, h, MmmUi.menuSurface(active && hot ? TILE : SURFACE),
                active && hot ? ROSE : LINE);
        if (active && hot) c.fill(x, y + 2, x + 2, y + h - 2, ROSE);
        return active ? TEXT : MUTED;
    }

    public static boolean inToolViewport(Screen screen, double x, double y) {
        int left = MmmUi.contentLeft(screen.width);
        return x >= left && x < left + MmmUi.contentWidth(screen.width)
                && y >= MmmUi.TOP_BAR_HEIGHT && y < screen.height - MmmUi.pagePad(screen.width);
    }

    public static ButtonWidget back(int width, Runnable close) {
        int right = contentX(width, 840) + contentWidth(width, 840);
        return control(right - BACK_WIDTH, 5, BACK_WIDTH, 20, "< Back", false, close);
    }

    static ButtonWidget control(int x, int y, int w, int h, String name, boolean selected, Runnable action) {
        return new BoardPlatform.BoardButton(x, y, w, h, BoardPlatform.literal(name), ignored -> action.run()) {
            @Override protected void renderFace(BoardPlatform.Canvas canvas, int mx, int my, float delta) {
                var c = canvas.raw();
                boolean hot = isFocused() || isMouseOver(mx, my);
                plate(c::fill, getX(), getY(), getWidth(), getHeight(), MmmUi.menuSurface(hot || selected ? TILE : SURFACE), hot ? ROSE : LINE);
                if (selected || isFocused()) c.fill(getX(), getY() + 2, getX() + 2, getY() + getHeight() - 2, ROSE);
                MmmUi.drawTextWithin(c, MinecraftClient.getInstance().textRenderer, name, getX() + 7,
                        getY() + (getHeight() - 8) / 2, getWidth() - 12, selected ? ROSE : hot ? TEXT : MUTED, false);
            }
        };
    }

}
