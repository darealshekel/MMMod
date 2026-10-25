package com.mmm.ui;

import com.mmm.mixin.HandledScreenAccessor;
import java.time.Duration;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public final class InventoryCollectionButton extends AbstractButton {
    private final Screen inventory;
    private InventoryCollectionButton(Screen inventory) {
        super(0, 0, 20, 20, Component.literal("MMM Collections"));
        this.inventory = inventory;
        setTooltip(Tooltip.create(Component.literal("MMM Collections")));
        setTooltipDelay(Duration.ZERO);
        position();
    }
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return;
            var button = new InventoryCollectionButton(screen);
            Screens.getWidgets(screen).add(button);
            ScreenEvents.beforeExtract(screen).register((current, context, mouseX, mouseY, delta) -> button.position());
        });
    }
    private void position() {
        var access = (HandledScreenAccessor) inventory;
        int right = access.mmm$getInventoryX() + access.mmm$getInventoryWidth();
        boolean creative = inventory instanceof CreativeModeInventoryScreen;
        setX(Math.max(2, Math.min(inventory.width - 22, creative ? right + 4 : right - 20)));
        setY(Math.max(2, creative ? access.mmm$getInventoryY() + 4 : access.mmm$getInventoryY() - 24));
        var player = Minecraft.getInstance().player;
        active = player != null && player.containerMenu.getCarried().isEmpty();
    }
    @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        var client = Minecraft.getInstance();
        if (client.player != null && client.player.containerMenu.getCarried().isEmpty())
            client.gui.setScreen(new CollectionBookScreen(inventory));
    }
    @Override protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (isHovered() || isFocused()) {
            context.fill(getX(), getY(), getX() + width, getY() + height, 0xCC292020);
            context.outline(getX(), getY(), width, height, MmmUi.accent());
        }
        RedBookIcon.draw(context, getX() + 2, getY() + 2);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput builder) { defaultButtonNarrationText(builder); }
}
