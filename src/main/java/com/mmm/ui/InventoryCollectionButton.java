package com.mmm.ui;

import com.mmm.mixin.HandledScreenAccessor;
import java.time.Duration;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import com.mmm.compat.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

public final class InventoryCollectionButton extends PressableWidget {
    private final Screen inventory;
    private InventoryCollectionButton(Screen inventory) {
        super(0, 0, 20, 20, Text.literal("MMM Collections"));
        this.inventory = inventory;
        setTooltip(Tooltip.of(Text.literal("MMM Collections")));
        setTooltipDelay(0);
        position();
    }
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeInventoryScreen)) return;
            var button = new InventoryCollectionButton(screen);
            Screens.getButtons(screen).add(button);
            ScreenEvents.beforeRender(screen).register((current, context, mouseX, mouseY, delta) -> button.position());
        });
    }
    private void position() {
        var access = (HandledScreenAccessor) inventory;
        int right = access.mmm$getInventoryX() + access.mmm$getInventoryWidth();
        boolean creative = inventory instanceof CreativeInventoryScreen;
        setX(Math.max(2, Math.min(inventory.width - 22, creative ? right + 4 : right - 20)));
        setY(Math.max(2, creative ? access.mmm$getInventoryY() + 4 : access.mmm$getInventoryY() - 24));
        var player = MinecraftClient.getInstance().player;
        active = player != null && player.currentScreenHandler.getCursorStack().isEmpty();
    }
    @Override public void onPress() {
        var client = MinecraftClient.getInstance();
        if (client.player != null && client.player.currentScreenHandler.getCursorStack().isEmpty())
            client.setScreen(new CollectionBookScreen(inventory));
    }
    @Override public void renderButton(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
        DrawContext context = new DrawContext(MinecraftClient.getInstance(), matrices);
        if (isHovered() || isFocused()) {
            context.fill(getX(), getY(), getX() + width, getY() + height, 0xCC292020);
            context.drawBorder(getX(), getY(), width, height, MmmUi.accent());
        }
        RedBookIcon.draw(context, getX() + 2, getY() + 2);
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) { appendDefaultNarrations(builder); }
}
