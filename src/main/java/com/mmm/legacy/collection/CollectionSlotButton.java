package com.mmm.legacy.collection;

import com.mmm.legacy.LegacyDrawContext;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;

public final class CollectionSlotButton extends ButtonWidget {
    private final ItemStack icon;
    private final boolean redBook;
    private final Runnable action;
    private final Supplier<String> badge;
    public final Supplier<List<String>> details;
    public CollectionSlotButton(int id, int x, int y, int size, String name, ItemStack icon, boolean redBook,
            Runnable action, Supplier<String> badge, Supplier<List<String>> details) {
        super(id, x, y, size, size, name);
        this.icon = icon; this.redBook = redBook; this.action = action; this.badge = badge; this.details = details;
    }
    public boolean contains(int mx, int my) { return visible && mx >= x && my >= y && mx < x + width && my < y + height; }
    public void press() { if (active) action.run(); }
    @Override public void render(Minecraft client, int mx, int my, float delta) {
        if (!visible) return;
        hovered = contains(mx, my);
        var c = new LegacyDrawContext();
        c.fill(x, y, x + width, y + height, hovered && active ? 0xFF514545 : 0xFF303030);
        c.drawBorder(x, y, width, height, hovered && active ? 0xFFF26969 : 0xFF151515);
        String overlay = badge.get();
        int ix = x + (width - 16) / 2;
        int iy = y + (overlay.isEmpty() ? (height - 16) / 2 : height < 32 ? 1 : 3);
        if (redBook) RedBookIcon.draw(c, ix, iy); else c.drawItem(icon, ix, iy);
        if (!overlay.isEmpty()) {
            int by = y + height - (height < 32 ? 9 : 10);
            c.fill(x + 1, by, x + width - 1, y + height - 1, 0xFF202023);
            c.drawText(client.textRenderer, overlay, x + (width - client.textRenderer.getWidth(overlay)) / 2, by, 0xFFEAEAEA, false);
        }
        if (!active) c.fill(x, y, x + width, y + height, 0x77000000);
    }
}
