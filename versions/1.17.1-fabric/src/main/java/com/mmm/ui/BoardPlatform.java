package com.mmm.ui;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import com.mmm.compat.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import com.mmm.compat.ButtonWidget;
import com.mmm.compat.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.registry.Registry;

public final class BoardPlatform {
    public static final Registry<Block> BLOCK = net.minecraft.util.registry.Registry.BLOCK;
    static final Registry<Item> ITEM = net.minecraft.util.registry.Registry.ITEM;
    private BoardPlatform() {}

    public static MutableText literal(String value) { return new net.minecraft.text.LiteralText(value); }
    static void rotateGradient(net.minecraft.client.util.math.MatrixStack matrices) {
        matrices.multiply(net.minecraft.util.math.Vec3f.POSITIVE_Z.getDegreesQuaternion(-90f));
    }
    static boolean inventoryKey(int key, int scan) { return MinecraftClient.getInstance().options.keyInventory.matchesKey(key, scan); }
    static boolean keyboard() { return keyboardFocus; }
    static void unfocus(Element element) { if (element instanceof BoardField field) field.focus(false);
        else if (element instanceof BoardButton button) button.setFocused(false); }
    static void flush(Canvas c) { /* MatrixStack drawing is immediate. */ }
    static ButtonWidget button(Text label, ButtonWidget.PressAction action, int x, int y, int w, int h) {
        return ButtonWidget.builder(label, action).dimensions(x,y,w,h).build();
    }

    record Canvas(DrawContext raw) {}
    private static boolean keyboardFocus;
    static abstract class BoardScreen extends com.mmm.compat.MmmScreen {
        BoardScreen(Text title) { super(title); }
        @Override public final void render(DrawContext c, int mx, int my, float delta) { render(new Canvas(c), mx, my, delta); }
        public void render(Canvas c, int mx, int my, float delta) { super.render(c.raw(), mx, my, delta); }
        @Override public final void renderBackground(DrawContext c) { renderBackground(new Canvas(c)); }
        public void renderBackground(Canvas c) {}
        @Override public boolean keyPressed(int key, int scan, int mods) {
            keyboardFocus = true;
            return super.keyPressed(key, scan, mods);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            keyboardFocus = false;
            return super.mouseClicked(x, y, button);
        }
    }
    static abstract class BoardButton extends ButtonWidget {
        BoardButton(int x, int y, int w, int h, Text label, PressAction action) {
            super(x, y, w, h, label, action);
        }
        void hintText(String hint) { /* The caption and inspector supply the accessible label. */ }
        @Override public void setFocused(boolean focused) { super.setFocused(focused); }
        public void setPosition(int x, int y) { this.x = x; this.y = y; }
        @Override public final void renderButton(net.minecraft.client.util.math.MatrixStack matrices, int mx, int my, float delta) {
            renderFace(new Canvas(new DrawContext(MinecraftClient.getInstance(), matrices)), mx, my, delta);
        }
        protected abstract void renderFace(Canvas c, int mx, int my, float delta);
    }
    static final class BoardField extends TextFieldWidget {
        BoardField(TextRenderer font, int x, int y, int w, int h, Text label) { super(font, x, y, w, h, label); }
        void focus(boolean focused) { setWidgetFocused(focused); }
    }
}
