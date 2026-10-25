package com.mmm.util;

import net.minecraft.client.Minecraft;
import net.minecraft.text.Text;

public final class MmmMessages
{
    private MmmMessages()
    {
    }

    public static void actionbar(String format, Object... args)
    {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null)
        {
            client.player.addMessage(new net.minecraft.text.LiteralText(format.formatted(args)), true);
        }
    }

    public static void error(String message)
    {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null)
        {
            client.player.addMessage(new net.minecraft.text.LiteralText(message), false);
        }
    }
}