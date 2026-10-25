package com.mmm.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

final class MinecraftText
{
    private MinecraftText()
    {
    }

    static void drawPlaceholder(MatrixStack matrices, Text text, int x, int y)
    {
        MinecraftClient.getInstance().textRenderer.draw(matrices, text, x, y, 0xFF707070);
    }
}
