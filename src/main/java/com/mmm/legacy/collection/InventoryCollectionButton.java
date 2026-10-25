package com.mmm.legacy.collection;

import com.mmm.legacy.LegacyDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;

public final class InventoryCollectionButton extends ButtonWidget {
    private final Screen inventory;
    public InventoryCollectionButton(Screen inventory) {
        super(-1920, 0, 0, 20, 20, "MMM Collections"); this.inventory = inventory;
    }
    public void position(int inventoryX, int inventoryY, int inventoryWidth) {
        boolean creative = inventory instanceof CreativeInventoryScreen;
        int right = inventoryX + inventoryWidth;
        x = Math.max(2, Math.min(inventory.width - 22, creative ? right + 4 : right - 20));
        y = Math.max(2, creative ? inventoryY + 4 : inventoryY - 24);
        var player = Minecraft.getInstance().player;
        active = player != null && player.inventory.getCursorItem().isEmpty();
    }
    public boolean click(int mx, int my, int button) {
        var client = Minecraft.getInstance();
        if (button != 0 || !active || client.player == null || !client.player.inventory.getCursorItem().isEmpty()
                || !mouseClicked(client, mx, my)) return false;
        playClickSound(client.getSoundManager());
        client.openScreen(new CollectionBookScreen(inventory));
        return true;
    }
    @Override public void render(Minecraft client, int mx, int my, float delta) {
        hovered = mx >= x && mx < x + width && my >= y && my < y + height;
        var context = new LegacyDrawContext();
        if (hovered) {
            context.fill(x, y, x + width, y + height, 0xCC292020);
            context.drawBorder(x, y, width, height, 0xFFCC3344);
        }
        RedBookIcon.draw(context, x + 2, y + 2);
        if (hovered) inventory.renderTooltip("MMM Collections", mx, my);
    }
}
