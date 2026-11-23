package com.mmm.ui;

import java.awt.Color;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

/** Two native-resolution ramps: saturation across the top, then value toward black. */
final class PickerGradients extends DrawableHelper {
    private PickerGradients() {}

    static void field(MatrixStack matrices, int x, int y, int w, int h, float hue) {
        matrices.push();
        try {
            matrices.translate(x, y + h, 0);
            BoardPlatform.rotateGradient(matrices);
            fillGradient(matrices, 0, 0, h, w, 0xffffffff, Color.HSBtoRGB(hue, 1f, 1f), 0);
        } finally {
            matrices.pop();
        }
        fillGradient(matrices, x, y, x + w, y + h, 0x00000000, 0xff000000, 0);
    }

    static void hue(MatrixStack matrices, int x, int y, int w, int h) {
        for (int i = 0; i < 6; i++) {
            int top = y + Math.round(i * h / 6f), bottom = y + Math.round((i + 1) * h / 6f);
            fillGradient(matrices, x, top, x + w, bottom,
                    Color.HSBtoRGB(i / 6f, 1f, 1f), Color.HSBtoRGB((i + 1) / 6f, 1f, 1f), 0);
        }
    }
}
