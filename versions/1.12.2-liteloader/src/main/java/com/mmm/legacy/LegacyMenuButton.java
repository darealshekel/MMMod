package com.mmm.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import static com.mmm.legacy.BoardArt.*;
import com.mmm.legacy.settings.SettingsUi;
import com.mmm.ui.BoardEditorStyle;

/** The clickable area and the two-line record drawn on it have the same bounds. */
public final class LegacyMenuButton extends ButtonWidget {
    private final String detail;
    private final ItemStack icon;
    boolean selected;
    public LegacyMenuButton(int id, int x, int y, int width, int height, String label, String detail, ItemStack icon, boolean selected) {
        super(id, x, y, width, height, label);
        this.detail = detail; this.icon = icon; this.selected = selected;
    }
    @Override public void render(Minecraft client, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        BoardEditorStyle.plate(net.minecraft.client.gui.GuiElement::fill, x, y, width, height,
                SettingsUi.opacity(selected || hovered ? ACTIVE : TILE), SettingsUi.opacity(selected || hovered ? ROSE : LINE));
        int inset = icon == null ? 7 : 27;
        if (icon != null) new LegacyDrawContext().drawItem(icon, x + 5, y + (height - 16) / 2);
        int room = Math.max(0, width - inset - 5);
        client.textRenderer.draw(client.textRenderer.trim(message, room), x + inset, y + 5, TEXT, false);
        client.textRenderer.draw(client.textRenderer.trim(detail, room), x + inset, y + height - 13, MUTED, false);
    }
}
