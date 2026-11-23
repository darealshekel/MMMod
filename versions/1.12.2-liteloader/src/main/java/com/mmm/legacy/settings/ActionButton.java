package com.mmm.legacy.settings;

import static com.mmm.ui.BoardEditorStyle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;

public class ActionButton extends ButtonWidget {
    private final boolean selected, record;
    public ActionButton(int id, int x, int y, int width, String label, boolean selected) {
        this(id, x, y, width, label, selected, false);
    }
    public ActionButton(int id, int x, int y, int width, String label, boolean selected, boolean record) {
        super(id, x, y, width, 20, label); this.selected = selected; this.record = record;
    }
    @Override public void render(Minecraft client, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        plate(net.minecraft.client.gui.GuiElement::fill, x, y, width, height,
                SettingsUi.opacity(hovered && active ? LINE : record ? SURFACE : TILE),
                SettingsUi.opacity(hovered && active || selected ? ROSE : LINE));
        if (selected) fill(x + 2, y + 3, x + 4, y + height - 3, ROSE);
        String label = client.textRenderer.trim(message, Math.max(0, width - (record ? 14 : 8)));
        client.textRenderer.draw(label, record ? x + 7 : x + (width - client.textRenderer.getWidth(label)) / 2, y + 6,
                selected || active ? TEXT : MUTED, false);
    }
}
