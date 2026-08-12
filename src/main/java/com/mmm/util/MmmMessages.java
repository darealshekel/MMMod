package com.mmm.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
            client.player.sendOverlayMessage(Component.literal(format.formatted(args)));
        }
    }

    public static void error(String message)
    {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null)
        {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }
}
