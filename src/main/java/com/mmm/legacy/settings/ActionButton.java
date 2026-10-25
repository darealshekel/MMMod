package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;

public final class ActionButton extends ButtonWidget {
    private final boolean selected;
    public ActionButton(int id, int x, int y, int width, String label, boolean selected) {
        super(id, x, y, width, 20, label); this.selected = selected;
    }
    @Override public void render(Minecraft client, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        fill(x, y, x + width, y + height, SettingsUi.opacity(hovered && active ? 0xFF303030 : 0xFF171717));
        if (selected) fill(x, y, x + 2, y + height, Configs.getMenuColor());
        else fill(x, y + height - 1, x + width, y + height, SettingsUi.opacity(0xFF454545));
        String label = client.textRenderer.trim(message, Math.max(0, width - 8));
        client.textRenderer.draw(label, x + (width - client.textRenderer.getWidth(label)) / 2, y + 6,
                selected || active ? 0xFFF5F5F5 : 0xFF858585, false);
    }
}
