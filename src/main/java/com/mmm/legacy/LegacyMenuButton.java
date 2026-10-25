package com.mmm.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;

final class LegacyMenuButton extends ButtonWidget {
    private final boolean selected;
    LegacyMenuButton(int id, int x, int y, int width, String label, boolean selected) {
        super(id, x, y, width, 20, label); this.selected = selected;
    }
    @Override public void render(Minecraft client, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        fill(x, y, x + width, y + height, selected ? 0xFF3C1818 : hovered && active ? 0xFF303030 : 0xFF1A1A1A);
        if (selected) fill(x, y, x + 2, y + height, 0xFFE00000);
        else fill(x, y + height - 1, x + width, y + height, 0xFF454545);
        String label = client.textRenderer.trim(message, width - 8);
        client.textRenderer.draw(label, x + (width - client.textRenderer.getWidth(label)) / 2, y + 6,
                active || selected ? 0xFFF5F5F5 : 0xFF858585, false);
    }
}
