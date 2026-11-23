package com.mmm;

import com.mmm.advancement.AchievementCardRenderer;
import com.mmm.advancement.MmmAdvancementManager;
import com.mmm.config.Configs;
import com.mmm.hud.MinedBlockOverlayRenderer;
import com.mmm.storage.AsyncPersistence;
import com.mmm.sync.SyncQueueManager;
import com.mmm.tracker.MiningStats;
import com.mmm.tracker.PlaytimeTracker;
import java.time.Duration;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class ClientSessionHooks implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MmmAdvancementManager.setNotificationRenderer(AchievementCardRenderer::show);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AchievementCardRenderer.clear();
            MinedBlockOverlayRenderer.clear();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MiningStats.finaliseSession();
            Configs.saveToFile();
            PlaytimeTracker.save();
            SyncQueueManager.shutdown();
            AsyncPersistence.flush(Duration.ofSeconds(2));
        });
    }
}
