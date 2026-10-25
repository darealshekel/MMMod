package com.mmm.legacy.settings;

import com.mmm.config.Configs;
import com.mmm.config.value.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;

public final class SettingButton extends ButtonWidget {
    public final IConfigBase config;
    public final boolean slider;
    public SettingButton(int id, int x, int y, int width, IConfigBase config, boolean slider) {
        super(id, x, y, width, 20, SettingsUi.display(config));
        this.config = config; this.slider = slider;
    }
    @Override public void render(Minecraft client, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        fill(x, y, x + width, y + height, SettingsUi.opacity(hovered ? 0xFF303030 : 0xFF171717));
        if (slider) {
            int amount = (int) Math.round((width - 4) * SettingsUi.fraction(config));
            fill(x + 2, y + height - 4, x + 2 + amount, y + height - 2, Configs.getMenuColor());
        }
        if (config instanceof ConfigColor) {
            int color;
            try { color = SettingsUi.color(config.getStringValue()); } catch (IllegalArgumentException e) { color = 0xFF000000; }
            fill(x + 3, y + 4, x + 15, y + 16, 0xFFFFFFFF);
            fill(x + 3, y + 4, x + 15, y + 16, color);
        }
        String label = client.textRenderer.trim(SettingsUi.display(config), width - (config instanceof ConfigColor ? 22 : 8));
        int color = config instanceof IConfigBoolean b && b.getBooleanValue() ? 0xFF65BF86 : 0xFFF5F5F5;
        client.textRenderer.draw(label, x + (config instanceof ConfigColor ? 19 : (width - client.textRenderer.getWidth(label)) / 2), y + 6, color, false);
    }
    public void slide(int mouseX) { SettingsUi.slide(config, (mouseX - x) / (double) Math.max(1, width - 1)); }
}
