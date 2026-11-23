package com.mmm.ui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;

public final class BoardEntries implements ClientModInitializer {
    @Override public void onInitializeClient() {
        // The original initializer already registers the inventory button and collections command.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ClientCommandManager.literal("mmm")
                .then(ClientCommandManager.literal("settings").executes(ctx -> open(true)))));
    }
    private static int open(boolean settings) {
        var client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(settings ? new MmmSettingsScreen(null) : new CollectionBookScreen(null)));
        return 1;
    }
}
