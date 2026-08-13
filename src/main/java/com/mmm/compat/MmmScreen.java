package com.mmm.compat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Bridges MMM's current screen render contract to Minecraft 1.17's MatrixStack API. */
public abstract class MmmScreen extends Screen
{
    protected MmmScreen(Text title)
    {
        super(title);
    }

    @Override
    public final void render(MatrixStack matrices, int mouseX, int mouseY, float delta)
    {
        render(new DrawContext(this.client, matrices), mouseX, mouseY, delta);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        super.render(context.getMatrices(), mouseX, mouseY, delta);
    }

    @Override
    public final void renderBackground(MatrixStack matrices)
    {
        renderBackground(new DrawContext(this.client, matrices));
    }

    public void renderBackground(DrawContext context)
    {
        super.renderBackground(context.getMatrices());
    }

    public void close()
    {
        this.client.setScreen(null);
    }

    @Override
    public void onClose()
    {
        close();
    }

    public boolean shouldPause()
    {
        return false;
    }

    @Override
    public boolean isPauseScreen()
    {
        return shouldPause();
    }

    protected void clearAndInit()
    {
        clearChildren();
        init();
    }
}
