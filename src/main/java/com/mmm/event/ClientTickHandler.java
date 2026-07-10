package com.mmm.event;

import com.mmm.hud.SummaryScreen;
import com.mmm.storage.SessionData;
import com.mmm.timer.MmmBlockBreakDetector;
import com.mmm.timer.MmmTimerState;
import com.mmm.timer.TimerCreditsScreen;
import com.mmm.tracker.MiningSpeedTracker;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import net.minecraft.client.MinecraftClient;

public class ClientTickHandler implements IClientTickHandler
{
    @Override
    public void onClientTick(MinecraftClient mc)
    {
        if (mc == null)
        {
            return;
        }

        com.mmm.tracker.MiningStats.onClientTick();
        MmmBlockBreakDetector.onClientTick(mc);
        MmmTimerState.onClientTick(mc);
        MiningSpeedTracker.tick(mc);

        if (MmmTimerState.consumeCreditsPending() && mc.currentScreen == null)
        {
            mc.setScreen(new TimerCreditsScreen(null));
            return;
        }

        SessionData pending = WorldLoadListener.consumePendingSummary();
        if (pending != null && mc.player == null && mc.world == null)
        {
            String worldName = WorldLoadListener.consumePendingSummaryName();
            mc.setScreen(SummaryScreen.worldExit(pending, mc.currentScreen, worldName));
            return;
        }
    }
}
