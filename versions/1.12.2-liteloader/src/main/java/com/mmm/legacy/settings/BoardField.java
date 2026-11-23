package com.mmm.legacy.settings;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.TextRenderer;
import static com.mmm.ui.BoardEditorStyle.*;

/** Keep native selection, caret and hit testing; replace only the vanilla frame. */
public final class BoardField extends TextFieldWidget {
    private final int w, h;
    public BoardField(int id, TextRenderer font, int x, int y, int width, int height) {
        super(id, font, x, y, width, height);
        w = width; h = height;
        setEditableColor(TEXT); setUneditableColor(MUTED);
    }
    @Override public void render() {
        if (!isVisible()) return;
        super.render();
        fill(x - 1, y - 1, x + w + 1, y, TILE);
        fill(x - 1, y, x, y + h, TILE);
        fill(x + w, y, x + w + 1, y + h, TILE);
        fill(x - 1, y + h, x + w + 1, y + h + 1, isFocused() ? ROSE : LINE);
    }
}
