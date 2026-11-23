package com.mmm.ui;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.registry.Registry;

public final class BoardPlatform {
    public static final Registry<Block> BLOCK = net.minecraft.registry.Registries.BLOCK;
    static final Registry<Item> ITEM = net.minecraft.registry.Registries.ITEM;
    private BoardPlatform() {}

    public static MutableText literal(String value) { return Text.literal(value); }
    static boolean inventoryKey(int key, int scan) { return MinecraftClient.getInstance().options.inventoryKey.matchesKey(key, scan); }
    static boolean keyboard() { return MinecraftClient.getInstance().getNavigationType().isKeyboard(); }
    static void unfocus(Element element) { element.setFocused(false); }
    static void flush(Canvas c) { c.raw().draw(); }
    static ButtonWidget button(Text label, ButtonWidget.PressAction action, int x, int y, int w, int h) {
        return ButtonWidget.builder(label, action).dimensions(x,y,w,h).build();
    }

    record Canvas(DrawContext raw) {}
    
    static abstract class BoardScreen extends Screen {
        BoardScreen(Text title) { super(title); }
        @Override public final void render(DrawContext c, int mx, int my, float delta) { render(new Canvas(c), mx, my, delta); }
        public void render(Canvas c, int mx, int my, float delta) { super.render(c.raw(), mx, my, delta); }
        @Override public final void renderBackground(DrawContext c) { renderBackground(new Canvas(c)); }
        public void renderBackground(Canvas c) {}
        
    }
    static abstract class BoardButton extends ButtonWidget {
        BoardButton(int x, int y, int w, int h, Text label, PressAction action) {
            super(x, y, w, h, label, action, DEFAULT_NARRATION_SUPPLIER);
        }
        void hintText(String hint) { setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(literal(hint))); setTooltipDelay(600); }
        
        @Override public final void renderButton(DrawContext c, int mx, int my, float delta) {
            renderFace(new Canvas(c), mx, my, delta);
        }
        protected abstract void renderFace(Canvas c, int mx, int my, float delta);
    }
    static final class BoardField extends TextFieldWidget {
        BoardField(TextRenderer font, int x, int y, int w, int h, Text label) { super(font, x, y, w, h, label); }
        void focus(boolean focused) { setFocused(focused); }
    }
}
