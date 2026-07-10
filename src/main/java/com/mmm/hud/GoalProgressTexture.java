package com.mmm.hud;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

public final class GoalProgressTexture
{
    private static final Identifier VANILLA_TEXTURE = Identifier.ofVanilla("textures/gui/sprites/hud/experience_bar_progress.png");
    private static final Identifier COLORED_TEXTURE = Identifier.of("mmm", "dynamic/goal_progress");

    private static NativeImageBackedTexture texture;
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
        MinecraftClient client = MinecraftClient.getInstance();
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

    private static void load(MinecraftClient client) throws IOException
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
                    int pixel = source.getColor(x, y);
                    alpha[index] = ColorHelper.Abgr.getAlpha(pixel);
                    int brightestChannel = Math.max(ColorHelper.Abgr.getRed(pixel),
                            Math.max(ColorHelper.Abgr.getGreen(pixel), ColorHelper.Abgr.getBlue(pixel)));
                    brightness[index] = brightestChannel / 255.0F;
                }
            }
        }

        texture = new NativeImageBackedTexture(width, height, false);
        client.getTextureManager().registerTexture(COLORED_TEXTURE, texture);
    }

    private static void recolor(int color)
    {
        NativeImage image = texture.getImage();
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
                image.setColor(x, y, ColorHelper.Abgr.getAbgr(
                        alpha[index],
                        Math.round(blue * shade),
                        Math.round(green * shade),
                        Math.round(red * shade)));
            }
        }
        texture.upload();
        currentColor = color;
    }
}
