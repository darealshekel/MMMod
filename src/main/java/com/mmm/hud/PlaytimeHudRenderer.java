package com.mmm.hud;

import com.mmm.config.Configs;
import com.mmm.tracker.PlaytimeTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class PlaytimeHudRenderer
{
    private PlaytimeHudRenderer() { }

    public static void render(DrawContext context, MinecraftClient client)
    {
        if (!Configs.Generic.WORLD_PLAYTIME_ABOVE_HOTBAR.getBooleanValue()
                || client.player == null || client.world == null || client.options.hudHidden) return;
        String label = "World Playtime: " + PlaytimeTracker.worldClock();
        int width = context.getScaledWindowWidth();
        label = client.textRenderer.trimToWidth(label, Math.max(0, width - 8));
        int x = (width - client.textRenderer.getWidth(label)) / 2;
        // Leave the vanilla health, armor, item-name and action-bar rows unobstructed.
        int y = Math.max(4, context.getScaledWindowHeight() - 82);
        context.drawText(client.textRenderer, label, x, y, Configs.getHudTextColor(), Configs.useHudTextShadow());
    }
}
