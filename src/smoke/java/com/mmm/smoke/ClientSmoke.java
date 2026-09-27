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
    private int menuIndex;
    private final long started = System.currentTimeMillis();

    public void onInitializeClient()
    {
        if (Boolean.getBoolean("mmm.miningFeaturesSmoke")) CollectionChatSmoke.start();
        if (Boolean.getBoolean("mmm.leaderboardSmoke")) {
            new LeaderboardSmoke().install();
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 4) return;
            try
            {
                if (System.currentTimeMillis() - started > 180_000L)
                    throw new AssertionError("Runtime test timed out in phase " + phase + ": " + client.currentScreen);
                if (phase == 0 && client.currentScreen != null
                        && client.currentScreen.getClass().getSimpleName().contains("Onboarding"))
                {
                    client.setScreen(new TitleScreen());
                }
                if (phase == 0 && client.currentScreen instanceof TitleScreen)
                {
                    phase = 1;
                    CreateWorldScreen.show(client, () -> client.setScreen(new TitleScreen()));
                }
                else if (phase == 1 && client.currentScreen instanceof CreateWorldScreen create)
                {
                    for (var child : create.children())
                    {
                        if (child instanceof ButtonWidget button && button.getMessage().equals(Text.translatable("selectWorld.create")))
                        {
                            phase = 2;
                            button.onPress(new net.minecraft.client.input.KeyInput(32, 0, 0));
                            break;
                        }
                    }
                }
                else if (phase == 2 && client.player != null && client.world != null && client.currentScreen == null)
                {
                    RuntimeChecks.run(client);
                    RuntimeChecks.prepareRendering(client);
                    phase = 5;
                }
                else if (phase == 5 && ++checkedTicks >= 40)
                {
                    phase = 6;
                    checkedTicks = 0;
                    client.options.pauseOnLostFocus = false;
                }
                else if (phase == 6 && ++checkedTicks >= 15)
                {
                    checkedTicks = 0;
                    net.minecraft.client.gui.screen.Screen screen = switch (menuIndex++) {
                        case 0 -> new com.mmm.ui.MmmSettingsScreen(null);
                        case 1 -> new com.mmm.gui.GuiConfigs(null);
                        case 2 -> new com.mmm.timer.TimerCreditsScreen(null);
                        case 3 -> new com.mmm.ui.WebsiteLinkScreen(null);
                        case 4 -> new net.minecraft.client.gui.screen.advancement.AdvancementsScreen(client.getNetworkHandler().getAdvancementHandler());
                        case 5 -> new com.mmm.ui.MmmChatIgnoreListScreen(null);
                        case 6 -> new com.mmm.scoreboard.ScoreboardScreen(null);
                        default -> null;
                    };
                    client.setScreen(screen);
                    if (screen != null) return;
                    if (Boolean.getBoolean("mmm.miningFeaturesSmoke")) {
                        phase = 4;
                        new MiningFeaturesSmoke().install();
                        return;
                    }
                    phase = 3;
                }
                else if (phase == 3 && ++checkedTicks >= 20 && SessionHistory.getLifetimeSummary() != null)
                {
                    phase = 4;
                    client.setScreen(null);
                    Files.writeString(Path.of("mmm-smoke-result.txt"), "PASS: live scoreboard, snapshot invalidation, async history, mixin audit, chat typing, advancements");
                    System.out.println("MMM_SMOKE_PASS");
                    client.scheduleStop();
                }
            }
            catch (Throwable failure)
            {
                phase = 4;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-smoke-result.txt"), "FAIL: " + failure); }
                catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }
}
