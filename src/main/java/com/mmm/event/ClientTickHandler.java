package com.mmm.event;

import com.mmm.hud.SummaryScreen;
import com.mmm.social.MilestoneSocialRelay;
import com.mmm.storage.SessionData;
import com.mmm.sync.WebsiteProfileTotals;
import com.mmm.tags.TierTagManager;
import com.mmm.timer.MmmBlockBreakDetector;
import com.mmm.timer.MmmTimerState;
import com.mmm.timer.TimerCreditsScreen;

import com.mmm.hotkey.MmmHotkeyManager;
import net.minecraft.client.MinecraftClient;

public class ClientTickHandler
{
    public void onClientTick(MinecraftClient mc)
    {
        if (mc == null)
        {
            return;
        }

        MmmHotkeyManager.tick(mc);
        com.mmm.tracker.MiningStats.onClientTick();
        MmmBlockBreakDetector.onClientTick(mc);
        MmmTimerState.onClientTick(mc);
        MilestoneSocialRelay.onClientTick(mc);
        TierTagManager.onClientTick(mc);
        WebsiteProfileTotals.refresh(false);

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
