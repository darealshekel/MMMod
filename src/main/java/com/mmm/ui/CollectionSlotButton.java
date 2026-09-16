package com.mmm.ui;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class CollectionSlotButton extends AbstractButton {
    private final ItemStack icon;
    private final boolean redBook;
    private final Runnable action;
    private final Supplier<String> badge;
    public final Supplier<List<Component>> details;
    public CollectionSlotButton(int x, int y, int size, String label, ItemStack icon, boolean redBook,
            Runnable action, Supplier<String> badge, Supplier<List<Component>> details) {
        super(x, y, size, size, Component.literal(label));
        this.icon = icon; this.redBook = redBook; this.action = action; this.badge = badge; this.details = details;
    }
    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { action.run(); }
    public void onPress() { action.run(); }
    @Override protected void extractContents(GuiGraphicsExtractor c, int mx, int my, float delta) {
        boolean hover = active && (isHovered() || isFocused());
        c.fill(getX(), getY(), getX() + width, getY() + height, hover ? 0xFF514545 : 0xFF303030);
        c.outline(getX(), getY(), width, height, hover ? 0xFFF26969 : 0xFF151515);
        String overlay = badge.get();
        int ix = getX() + (width - 16) / 2;
        int iy = getY() + (overlay.isEmpty() ? (height - 16) / 2 : height < 32 ? 1 : 3);
        if (redBook) RedBookIcon.draw(c, ix, iy);
        else c.item(icon, ix, iy);
        if (!overlay.isEmpty()) {
            var font = Minecraft.getInstance().font;
            // Items render on a raised GUI layer; reserve a separate strip below the icon.
            int badgeY = getY() + height - (height < 32 ? 9 : 10);
            c.fill(getX() + 1, badgeY, getX() + width - 1, getY() + height - 1, 0xFF202023);
            c.text(font, overlay, getX() + (width - font.width(overlay)) / 2, badgeY, 0xFFEAEAEA, false);
        }
        if (!active) c.fill(getX(), getY(), getX() + width, getY() + height, 0x77000000);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
}
