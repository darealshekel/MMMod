package com.mmm.hud;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class GoalProgressTexture
{
    private static final Identifier VANILLA_TEXTURE = Identifier.withDefaultNamespace("textures/gui/sprites/hud/experience_bar_progress.png");
    private static final Identifier COLORED_TEXTURE = Identifier.fromNamespaceAndPath("mmm", "dynamic/goal_progress");

    private static DynamicTexture texture;
    private static int[] alpha;
    private static float[] brightness;
    private static int width;
    private static int height;
    private static int currentColor = Integer.MIN_VALUE;
    private static boolean unavailable;

    private GoalProgressTexture()
    {
    }

    public static Identifier get(int color)
    {
        Minecraft client = Minecraft.getInstance();
        if (client == null || unavailable)
        {
            return null;
        }

        try
        {
            if (texture == null)
            {
                load(client);
            }
            if (texture != null && color != currentColor)
            {
                recolor(color);
            }
            return texture == null ? null : COLORED_TEXTURE;
        }
        catch (IOException | RuntimeException exception)
        {
            unavailable = true;
            return null;
        }
    }

    private static void load(Minecraft client) throws IOException
    {
        try (InputStream stream = client.getResourceManager().open(VANILLA_TEXTURE);
             NativeImage source = NativeImage.read(stream))
        {
            width = source.getWidth();
            height = source.getHeight();
            alpha = new int[width * height];
            brightness = new float[width * height];

            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    int index = y * width + x;
                    int pixel = source.getPixel(x, y);
                    alpha[index] = ARGB.alpha(pixel);
                    int brightestChannel = Math.max(ARGB.red(pixel),
                            Math.max(ARGB.green(pixel), ARGB.blue(pixel)));
                    brightness[index] = brightestChannel / 255.0F;
                }
            }
        }

        texture = new DynamicTexture("MMM goal progress", width, height, false);
        client.getTextureManager().register(COLORED_TEXTURE, texture);
    }

    private static void recolor(int color)
    {
        NativeImage image = texture.getPixels();
        if (image == null)
        {
            throw new IllegalStateException("Goal progress texture is closed");
        }

        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                int index = y * width + x;
                float shade = brightness[index];
                image.setPixel(x, y, ARGB.color(
                        alpha[index],
                        Math.round(red * shade),
                        Math.round(green * shade),
                        Math.round(blue * shade)));
            }
        }
        texture.upload();
        currentColor = color;
    }
}
