package com.mmm.event;

import com.mmm.config.FeatureToggle;
import com.mmm.hud.MiningHudRenderer;
import com.mmm.hud.SpeedGraphRenderer;
import com.mmm.timer.TimerHudRenderer;
import com.mmm.tracker.MiningSpeedTracker;
import net.minecraft.client.MinecraftClient;
import com.mmm.compat.DrawContext;

public final class RenderHandler
{
    private static final boolean SPEED_GRAPH_AVAILABLE = false;

    private RenderHandler()
    {
    }

    public static void renderHud(DrawContext drawContext)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!FeatureToggle.MMM_MINING_TRACKER.getBooleanValue())
        {
            return;
        }

        MiningHudRenderer.render(drawContext, client);
        TimerHudRenderer.render(drawContext, client);
        if (SPEED_GRAPH_AVAILABLE && FeatureToggle.MMM_HUD_SPEED_GRAPH.getBooleanValue() && MiningSpeedTracker.hasSessionData())
        {
            SpeedGraphRenderer.render(drawContext, client);
        }
    }
}