package com.mmm.event;

import com.mmm.sync.WebsiteProfileTotals;

import com.mmm.hud.SummaryScreen;
import com.mmm.social.PublicChatClient;
import com.mmm.storage.SessionData;
import com.mmm.tags.TierTagManager;
import com.mmm.timer.MmmBlockBreakDetector;
import com.mmm.timer.MmmTimerState;
import com.mmm.timer.TimerCreditsScreen;
import net.minecraft.client.Minecraft;
import com.mmm.hotkey.MmmHotkeyManager;

public class ClientTickHandler
{
    public void onClientTick(Minecraft mc)
    {
        if (mc == null)
        {
            return;
        }

        MmmHotkeyManager.tick(mc);
        com.mmm.tracker.MiningStats.onClientTick();
        WebsiteProfileTotals.refresh(false);
        MmmBlockBreakDetector.onClientTick(mc);
        MmmTimerState.onClientTick(mc);
        PublicChatClient.onClientTick(mc);
        TierTagManager.onClientTick(mc);

        if (MmmTimerState.consumeCreditsPending() && mc.screen == null)
        {
            mc.setScreen(new TimerCreditsScreen(null));
            return;
        }

        SessionData pending = WorldLoadListener.consumePendingSummary();
        if (pending != null && mc.player == null && mc.level == null)
        {
            String worldName = WorldLoadListener.consumePendingSummaryName();
            mc.setScreen(SummaryScreen.worldExit(pending, mc.screen, worldName));
            return;
        }
    }
}
