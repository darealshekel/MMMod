package com.mmm.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.TranslatableText;

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
                    client.setScreen(CreateWorldScreen.create(client.currentScreen));
                } else if (phase == 1 && client.currentScreen instanceof CreateWorldScreen create) {
                    for (var child : create.children()) {
                        if (child instanceof ButtonWidget button && button.getMessage().equals(new TranslatableText("selectWorld.create"))) {
                            phase = 2;
                            button.onPress();
                            break;
                        }
                    }
                } else if (phase == 2 && client.world != null && client.player != null && client.currentScreen == null) {
                    phase = 3;
                    new ReliabilitySmoke().install();
                }
            } catch (Throwable failure) {
                phase = 3;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-reliability-smoke.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }
}
