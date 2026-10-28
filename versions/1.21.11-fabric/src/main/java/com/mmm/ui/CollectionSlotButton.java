package com.mmm.ui;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class CollectionSlotButton extends ClickableWidget {
    private final ItemStack icon;
    private final boolean redBook;
    private final Runnable action;
    private final Supplier<String> badge;
    public final Supplier<List<Text>> details;
    public CollectionSlotButton(int x, int y, int size, String label, ItemStack icon, boolean redBook,
            Runnable action, Supplier<String> badge, Supplier<List<Text>> details) {
        super(x, y, size, size, Text.literal(label));
        this.icon = icon; this.redBook = redBook; this.action = action; this.badge = badge; this.details = details;
    }
    public void onPress() { action.run(); }
    @Override public void onClick(net.minecraft.client.gui.Click click, boolean doubleClick) { onPress(); }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (!active || !visible || !input.isEnterOrSpace()) return false;
        playDownSound(MinecraftClient.getInstance().getSoundManager());
        onPress();
        return true;
    }
    @Override protected void renderWidget(DrawContext c, int mx, int my, float delta) {
        boolean hover = active && (isHovered() || isFocused());
        c.fill(getX(), getY(), getX() + width, getY() + height, hover ? 0xFF514545 : 0xFF303030);
        com.mmm.ui.MmmUi.drawBorder(c, getX(), getY(), width, height, hover ? 0xFFF26969 : 0xFF151515);
        String overlay = badge.get();
        int ix = getX() + (width - 16) / 2;
        int iy = getY() + (overlay.isEmpty() ? (height - 16) / 2 : height < 32 ? 1 : 3);
        if (redBook) RedBookIcon.draw(c, ix, iy);
        else c.drawItem(icon, ix, iy);
        if (!overlay.isEmpty()) {
            var font = MinecraftClient.getInstance().textRenderer;
            // Items render on a raised GUI layer; reserve a separate strip below the icon.
            int badgeY = getY() + height - (height < 32 ? 9 : 10);
            c.fill(getX() + 1, badgeY, getX() + width - 1, getY() + height - 1, 0xFF202023);
            c.drawText(font, overlay, getX() + (width - font.getWidth(overlay)) / 2, badgeY, 0xFFEAEAEA, false);
        }
        if (!active) c.fill(getX(), getY(), getX() + width, getY() + height, 0x77000000);
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
}
