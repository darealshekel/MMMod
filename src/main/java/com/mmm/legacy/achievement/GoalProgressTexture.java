package com.mmm.legacy.achievement;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ReloadableResourceManager;
import net.minecraft.resource.Identifier;

public final class GoalProgressTexture {
    private static final Identifier ID = new Identifier("mmm", "dynamic/goal_progress");
    private static DynamicTexture texture;
    private static int[] source;
    private static int currentColor;
    private static boolean failed;
    private static boolean registered;

    private GoalProgressTexture() {}

    public static void registerReloadListener(Minecraft client) {
        if (!registered && client.getResourceManager() instanceof ReloadableResourceManager manager) {
            registered = true;
            manager.addListener(resources -> {
                if (texture != null) client.getTextureManager().close(ID);
                texture = null;
                source = null;
                failed = false;
            });
        }
    }

    public static Identifier get(Minecraft client, int color) {
        registerReloadListener(client);
        if (failed) return null;
        try {
            if (texture == null) {
                try (Resource resource = client.getResourceManager().getResource(GuiElement.ICONS_LOCATION)) {
                    BufferedImage image = ImageIO.read(resource.asStream());
                    if (image == null || image.getWidth() < 256 || image.getHeight() < 256)
                        throw new IOException("Invalid HUD icon atlas");
                    // Downsample pack-resolution sprites to the vanilla 182x5 logical silhouette.
                    source = new int[182 * 5];
                    for (int y = 0; y < 5; y++) for (int x = 0; x < 182; x++)
                        source[y * 182 + x] = image.getRGB(x * image.getWidth() / 256, (69 + y) * image.getHeight() / 256);
                }
                texture = new DynamicTexture(182, 5);
                client.getTextureManager().register(ID, texture);
                currentColor = ~color;
            }
            if (currentColor != color) {
                recolor(source, texture.getPixels(), color);
                texture.upload();
                currentColor = color;
            }
            return ID;
        } catch (IOException | RuntimeException failure) {
            failed = true;
            return null;
        }
    }

    static void recolor(int[] source, int[] destination, int color) {
        for (int i = 0; i < source.length; i++) {
            int pixel = source[i];
            int brightness = Math.max(pixel >> 16 & 255, Math.max(pixel >> 8 & 255, pixel & 255));
            int r = Math.round((color >> 16 & 255) * brightness / 255F);
            int g = Math.round((color >> 8 & 255) * brightness / 255F);
            int b = Math.round((color & 255) * brightness / 255F);
            destination[i] = pixel & 0xFF000000 | r << 16 | g << 8 | b;
        }
    }
}
