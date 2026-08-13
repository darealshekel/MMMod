package com.mmm.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;

/** Backports the small DrawContext surface used by MMM to pre-1.20 clients. */
public final class DrawContext extends DrawableHelper
{
    private final MinecraftClient client;
    private final MatrixStack matrices;

    public DrawContext(MinecraftClient client, MatrixStack matrices)
    {
        this.client = client;
        this.matrices = matrices;
    }

    public MatrixStack getMatrices()
    {
        return this.matrices;
    }

    public int getScaledWindowWidth()
    {
        return this.client.getWindow().getScaledWidth();
    }

    public int getScaledWindowHeight()
    {
        return this.client.getWindow().getScaledHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color)
    {
        DrawableHelper.fill(this.matrices, x1, y1, x2, y2, color);
    }

    public void drawBorder(int x, int y, int width, int height, int color)
    {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public int drawText(TextRenderer renderer, Text text, int x, int y, int color, boolean shadow)
    {
        return shadow
                ? renderer.drawWithShadow(this.matrices, text, x, y, color)
                : renderer.draw(this.matrices, text, x, y, color);
    }

    public int drawText(TextRenderer renderer, String text, int x, int y, int color, boolean shadow)
    {
        return shadow
                ? renderer.drawWithShadow(this.matrices, text, x, y, color)
                : renderer.draw(this.matrices, text, x, y, color);
    }

    public int drawText(TextRenderer renderer, OrderedText text, int x, int y, int color, boolean shadow)
    {
        return shadow
                ? renderer.drawWithShadow(this.matrices, text, x, y, color)
                : renderer.draw(this.matrices, text, x, y, color);
    }

    public int drawTextWithShadow(TextRenderer renderer, String text, int x, int y, int color)
    {
        return renderer.drawWithShadow(this.matrices, text, x, y, color);
    }

    public int drawTextWithShadow(TextRenderer renderer, Text text, int x, int y, int color)
    {
        return renderer.drawWithShadow(this.matrices, text, x, y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer renderer, Text text, int centerX, int y, int color)
    {
        renderer.drawWithShadow(this.matrices, text, centerX - renderer.getWidth(text) / 2.0F, y, color);
    }

    public void drawItem(ItemStack stack, int x, int y)
    {
        this.client.getItemRenderer().renderInGui(stack, x, y);
    }

    public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height)
    {
        RenderSystem.setShaderTexture(0, texture);
        super.drawTexture(this.matrices, x, y, u, v, width, height);
    }

    public void drawTexture(Identifier texture, int x, int y, float u, float v,
                            int width, int height, int textureWidth, int textureHeight)
    {
        RenderSystem.setShaderTexture(0, texture);
        DrawableHelper.drawTexture(this.matrices, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void enableScissor(int x1, int y1, int x2, int y2)
    {
        Window window = this.client.getWindow();
        double scale = window.getScaleFactor();
        int framebufferHeight = window.getFramebufferHeight();
        int left = (int) Math.floor(x1 * scale);
        int bottom = (int) Math.floor(framebufferHeight - y2 * scale);
        int width = Math.max(0, (int) Math.ceil((x2 - x1) * scale));
        int height = Math.max(0, (int) Math.ceil((y2 - y1) * scale));
        RenderSystem.enableScissor(left, bottom, width, height);
    }

    public void disableScissor()
    {
        RenderSystem.disableScissor();
    }
}
