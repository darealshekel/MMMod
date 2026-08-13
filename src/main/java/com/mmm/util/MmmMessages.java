package com.mmm.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class MmmMessages
{
    private MmmMessages()
    {
    }

    public static void actionbar(String format, Object... args)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null)
        {
            client.player.sendMessage(new net.minecraft.text.LiteralText(format.formatted(args)), true);
        }
    }

    public static void error(String message)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null)
        {
            client.player.sendMessage(new net.minecraft.text.LiteralText(message), false);
        }
    }
}