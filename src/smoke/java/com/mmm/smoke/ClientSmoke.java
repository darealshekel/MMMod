package com.mmm.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import com.mmm.storage.SessionHistory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ClientSmoke implements ClientModInitializer
{
    private int phase;
    private int checkedTicks;
    private final long started = System.currentTimeMillis();

    public void onInitializeClient()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 5) return;
            try
            {
                if (System.currentTimeMillis() - started > 180_000L)
                    throw new AssertionError("Runtime test timed out in phase " + phase);
                if (phase == 0 && client.currentScreen instanceof TitleScreen)
                {
                    phase = 1;
                    CreateWorldScreen.create(client, client.currentScreen);
                }
                else if (phase == 1 && client.currentScreen instanceof CreateWorldScreen create)
                {
                    for (var child : create.children())
                    {
                        if (child instanceof ButtonWidget button && button.getMessage().equals(Text.translatable("selectWorld.create")))
                        {
                            phase = 2;
                            button.onPress();
                            break;
                        }
                    }
                }
                else if (phase == 2 && client.player != null && client.world != null && client.currentScreen == null)
                {
                    RuntimeChecks.run(client);
                    phase = 3;
                }
                else if (phase == 3 && ++checkedTicks >= 20 && SessionHistory.getLifetimeSummary() != null)
                {
                    RuntimeChecks.prepareRendering(client);
                    phase = 4;
                    checkedTicks = 0;
                }
                else if (phase == 4 && ++checkedTicks >= 40)
                {
                    RuntimeChecks.mineBlocks(client);
                    phase = 5;
                    client.setScreen(null);
                    Files.writeString(Path.of("mmm-smoke-result.txt"), "PASS: Java 17, live scoreboard, snapshot invalidation, async history, mixin audit, chat typing, advancements and progress, settings, Tab commas, breaking indicator rendering, stone/glowstone/leaves mining");
                    System.out.println("MMM_SMOKE_PASS");
                    client.scheduleStop();
                }
            }
            catch (Throwable failure)
            {
                phase = 5;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-smoke-result.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }
}
