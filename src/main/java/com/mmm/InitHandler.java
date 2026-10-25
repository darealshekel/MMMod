package com.mmm;

import com.mmm.config.Callbacks;
import com.mmm.config.Configs;
import com.mmm.event.ClientTickHandler;
import com.mmm.event.RenderHandler;
import com.mmm.event.WorldLoadListener;
import com.mmm.feature.BlockEspRenderer;
import com.mmm.timer.MmmClientCommands;
import com.mmm.timer.MmmTimerState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class InitHandler
{
    public void registerModHandlers()
    {
        Configs.loadFromFile();
        MmmTimerState.load();
        MmmClientCommands.register();
        Callbacks.init();
        BlockEspRenderer.initialize();

        WorldLoadListener worldListener = new WorldLoadListener();
        ClientTickHandler tickHandler = new ClientTickHandler();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> worldListener.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> worldListener.onDisconnect(client));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            worldListener.pollWorldChange(client);
            tickHandler.onClientTick(client);
        });
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("mmm", "main_hud"),
                (drawContext, tickCounter) -> RenderHandler.renderHud(drawContext)
        );
    }
}
