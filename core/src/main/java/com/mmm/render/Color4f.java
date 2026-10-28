package com.mmm.render;

public final class Color4f
{
    public final float r;
    public final float g;
    public final float b;
    public final float a;

    public Color4f(float r, float g, float b, float a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public static Color4f fromColor(int argb)
    {
        return new Color4f(
                ((argb >>> 16) & 0xFF) / 255.0F,
                ((argb >>> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                ((argb >>> 24) & 0xFF) / 255.0F);
    }

    public static Color4f fromColor(Color4f color, float alpha)
    {
        return new Color4f(color.r, color.g, color.b, Math.max(0.0F, Math.min(1.0F, alpha)));
    }
}