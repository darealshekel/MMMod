package com.mmm.liteloader;

import com.mmm.Reference;
import com.mmm.legacy.LegacyClient;
import com.mmm.config.FeatureToggle;
import com.mmm.hud.MiningHudRenderer;
import com.mmm.legacy.LegacyDrawContext;
import com.mmm.timer.TimerHudRenderer;
import com.mumfrey.liteloader.HUDRenderListener;
import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.core.LiteLoader;
import java.io.File;
import net.minecraft.client.Minecraft;

public final class LiteModMMM implements Tickable, HUDRenderListener {
    @Override public String getName() { return "MMM"; }
    @Override public String getVersion() { return Reference.MOD_VERSION; }

    @Override public void init(File configPath) {
        LiteLoaderEnvironment.initialize(LiteLoader.getGameDirectory().toPath());
        LegacyClient.init();
    }

    @Override public void onTick(Minecraft minecraft, float partialTicks, boolean inGame, boolean clock) {
        // LiteLoader also dispatches frame callbacks: only game ticks advance state.
        if (clock) LegacyClient.tick(minecraft);
    }

    @Override public void onPreRenderHUD(int width, int height) {}

    @Override public void onPostRenderHUD(int width, int height) {
        // LiteLoader dispatches this after both the vanilla and Forge HUD renderers.
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui || !FeatureToggle.MMM_MINING_TRACKER.getBooleanValue()) return;
        LegacyDrawContext context = new LegacyDrawContext();
        MiningHudRenderer.render(context, client);
        TimerHudRenderer.render(context, client);
    }

    @Override public void upgradeSettings(String version, File configPath, File oldConfigPath) {
        // MMM's existing Configs migration owns cross-version storage.
    }
}
