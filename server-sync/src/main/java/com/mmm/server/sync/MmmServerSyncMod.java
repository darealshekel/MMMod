package com.mmm.server.sync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class MmmServerSyncMod implements ModInitializer
{
    static final String MOD_ID = "mmm_server_sync";
    private static final ServerSyncManager SYNC_MANAGER = new ServerSyncManager();

    @Override
    public void onInitialize()
    {
        ServerLifecycleEvents.SERVER_STARTED.register(SYNC_MANAGER::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(SYNC_MANAGER::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(SYNC_MANAGER::onServerTick);
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer)
            {
                SYNC_MANAGER.onBlockBroken(serverPlayer, state);
            }
        });
    }
}
