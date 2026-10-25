package com.mmm;

import com.mmm.config.Callbacks;
import com.mmm.config.Configs;
import com.mmm.event.ClientTickHandler;
import com.mmm.event.RenderHandler;
import com.mmm.event.WorldLoadListener;
import com.mmm.feature.BlockEspRenderer;
import com.mmm.timer.MmmClientCommands;
import com.mmm.timer.MmmTimerState;
import com.mmm.update.UpdateNotifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class InitHandler
{
    public void registerModHandlers()
    {
        Configs.loadFromFile();
        MmmTimerState.load();
        MmmClientCommands.register();
        com.mmm.ui.InventoryCollectionButton.register();
        Callbacks.init();
        BlockEspRenderer.initialize();

        WorldLoadListener worldListener = new WorldLoadListener();
        ClientTickHandler tickHandler = new ClientTickHandler();
        UpdateNotifier updates = new UpdateNotifier();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            worldListener.onJoin(client);
            updates.onJoin(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            worldListener.onDisconnect(client);
            updates.onDisconnect();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            com.mmm.tracker.PlaytimeTracker.save();
            com.mmm.storage.AsyncPersistence.flush(java.time.Duration.ofSeconds(2));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            worldListener.pollWorldChange(client);
            tickHandler.onClientTick(client);
            updates.tick(client);
        });
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> RenderHandler.renderHud(drawContext));
    }
}
