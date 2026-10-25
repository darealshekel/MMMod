package com.mmm.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class ClientSmoke implements ClientModInitializer {
    private int phase;
    private final long started = System.currentTimeMillis();

    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 3) return;
            try {
                if (System.currentTimeMillis() - started > 180_000L) throw new AssertionError("World startup timed out");
                if (phase == 0 && client.getOverlay() == null && System.currentTimeMillis() - started > 8000L) {
                    phase = 1;
                    CreateWorldScreen.openFresh(client, () -> client.setScreen(new TitleScreen()));
                } else if (phase == 1 && client.screen instanceof CreateWorldScreen create) {
                    for (var child : Screens.getWidgets(create)) {
                        if (child instanceof Button button && button.getMessage().equals(Component.translatable("selectWorld.create"))) {
                            phase = 2;
                            button.onPress(new KeyEvent(257, 0, 0));
                            break;
                        }
                    }
                } else if (phase == 2 && client.level != null && client.player != null && client.screen == null) {
                    phase = 3;
                    new ReliabilitySmoke().install();
                }
            } catch (Throwable failure) {
                phase = 3;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-reliability-smoke.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.stop();
            }
        });
    }
}
