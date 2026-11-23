package com.mmm.ui;

import java.util.function.Consumer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.ColorHelper;

final class BoardGraphics {
    private BoardGraphics() {}
    static void capture(Framebuffer buffer, Consumer<NativeImage> callback) {
        ScreenshotRecorder.takeScreenshot(buffer, callback);
    }
    static void push(DrawContext c) { c.getMatrices().pushMatrix(); }
    static void pop(DrawContext c) { c.getMatrices().popMatrix(); }
    static void translate(DrawContext c, float x, float y) { c.getMatrices().translate(x, y); }
    static void scale(DrawContext c, float x, float y) { c.getMatrices().scale(x, y); }
    static int lerp(float t, int a, int b) { return ColorHelper.lerp(t, a, b); }
}
