package com.mmm.legacy;

import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import org.lwjgl.opengl.GL11;

/** Small rendering bridge for the existing HUD models, using Minecraft's GUI pipeline. */
public final class LegacyDrawContext {
    /** Scoped clipping restores the caller's scissor state, including nested clips. */
    public Clip clip(int left, int top, int right, int bottom) {
        return new Clip(left, top, right, bottom);
    }

    public static final class Clip implements AutoCloseable {
        private final boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        // LWJGL 2's generic glGetInteger binding requires room for 16 values.
        private final java.nio.IntBuffer previous = org.lwjgl.BufferUtils.createIntBuffer(16);
        private Clip(int left, int top, int right, int bottom) {
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previous);
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.render.Window window = new net.minecraft.client.render.Window(client);
            int factor = window.getScale();
            int x = left * factor, y = client.height - bottom * factor;
            int r = right * factor, t = client.height - top * factor;
            if (enabled) {
                x = Math.max(x, previous.get(0)); y = Math.max(y, previous.get(1));
                r = Math.min(r, previous.get(0) + previous.get(2));
                t = Math.min(t, previous.get(1) + previous.get(3));
            }
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, Math.max(0, r - x), Math.max(0, t - y));
        }
        @Override public void close() {
            GL11.glScissor(previous.get(0), previous.get(1), previous.get(2), previous.get(3));
            if (!enabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
    public void fill(int left, int top, int right, int bottom, int color) {
        GuiElement.fill(left, top, right, bottom, color);
    }

    public void drawBorder(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y, x + 1, y + height, color);
        fill(x + width - 1, y, x + width, y + height, color);
    }

    public void drawText(TextRenderer renderer, String text, int x, int y, int color, boolean shadow) {
        renderer.draw(text, x, y, color, shadow);
    }

    public void drawItem(net.minecraft.item.ItemStack stack, int x, int y) {
        net.minecraft.client.render.platform.Lighting.turnOnGui();
        try {
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
        } finally {
            net.minecraft.client.render.platform.Lighting.turnOff();
            net.minecraft.client.render.platform.GlStateManager.disableDepthTest();
            net.minecraft.client.render.platform.GlStateManager.color4f(1, 1, 1, 1);
        }
    }

    public LegacyDrawContext getMatrices() { return this; }
    public void push() { GL11.glPushMatrix(); }
    public void pop() { GL11.glPopMatrix(); }
    public void translate(float x, float y, float z) { GL11.glTranslatef(x, y, z); }
    public void scale(float x, float y, float z) { GL11.glScalef(x, y, z); }
}
