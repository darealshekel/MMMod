package com.mmm.liteloader.smoke;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import net.minecraft.client.Minecraft;

/** Test-only idle run; uses synthetic, isolated storage and no authenticated website account. */
final class LegacyAfkProbe {
    private int ticks;
    boolean tick(Minecraft client) {
        ticks++;
        if (ticks == 1) {
            client.openScreen(null);
            FeatureToggle.MMM_MINING_TRACKER.setBooleanValue(true);
            FeatureToggle.MMM_HUD.setBooleanValue(true);
            FeatureToggle.MMM_DAILY_GOAL.setBooleanValue(true);
            FeatureToggle.MMM_HUD_GOAL_PROGRESS.setBooleanValue(true);
            FeatureToggle.MMM_BLOCK_ESP.setBooleanValue(true);
            Configs.Generic.ALWAYS_OVERRIDE_XP_BAR.setBooleanValue(true);
            Configs.Generic.BREAKING_INDICATORS.setBooleanValue(true);
            Configs.Generic.TRANSLUCENT_LAVA.setBooleanValue(true);
            Configs.Generic.TIER_NAME_TAGS.setBooleanValue(true);
            Configs.Generic.WEBSITE_SYNC_ENABLED.setBooleanValue(false);
            Configs.dailyProgress = 1000;
            client.player.pitch = 70;
            System.out.println("MMM_AFK_STARTED: HUD, goal texture, ESP, breaking indicators, tags; sync off");
        }
        if (ticks % 600 == 0) {
            System.gc();
            long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long direct = 0;
            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class))
                direct += pool.getMemoryUsed();
            System.out.println("MMM_AFK_SAMPLE ticks=" + ticks + " heap=" + heap + " nativeBuffers=" + direct
                    + " threads=" + ManagementFactory.getThreadMXBean().getThreadCount());
        }
        if (ticks < 4800) return false;
        System.out.println("MMM_AFK_PROBE_OK: 4800 idle client ticks");
        return true;
    }
}
