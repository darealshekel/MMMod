package com.mmm.compat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Bridges MMM's screens to the MatrixStack UI used before Minecraft 1.20. */
public abstract class MmmScreen extends Screen {
    protected MmmScreen(Text title) { super(title); }

    @Override
    public final void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        render(new DrawContext(this.client, matrices), mouseX, mouseY, delta);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context.getMatrices(), mouseX, mouseY, delta);
    }

    public void renderBackground(DrawContext context) {
        super.renderBackground(context.getMatrices());
    }

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
    }
}
