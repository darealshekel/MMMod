package com.mmm.legacy.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;

/** Raw GL changes are paired with push/pop attributes; Minecraft's cached state is never changed. */
public final class WorldBoxes {
    private WorldBoxes() {}
    public static Box cameraRelative(Minecraft client, Box box, float partialTick) {
        Entity camera = client.getCamera();
        return box.moved(-(camera.prevX + (camera.x - camera.prevX) * partialTick),
                -(camera.prevY + (camera.y - camera.prevY) * partialTick),
                -(camera.prevZ + (camera.z - camera.prevZ) * partialTick));
    }
    public static void render(Box box, int fill, int outline, float lineWidth, boolean throughWalls) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (throughWalls) GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            if ((fill >>> 24) != 0)
                WorldRenderer.renderShape(box, channel(fill, 16), channel(fill, 8), channel(fill, 0), channel(fill, 24));
            if ((outline >>> 24) != 0) {
                GL11.glLineWidth(lineWidth);
                WorldRenderer.renderOutlineShape(box, channel(outline, 16), channel(outline, 8), channel(outline, 0), channel(outline, 24));
            }
        } finally { GL11.glPopAttrib(); }
    }
    private static float channel(int color, int shift) { return ((color >>> shift) & 255) / 255.0F; }
}

