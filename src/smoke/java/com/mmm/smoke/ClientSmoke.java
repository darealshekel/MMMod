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
                if (phase == 0 && client.currentScreen instanceof net.minecraft.client.gui.screen.AccessibilityOnboardingScreen)
                    client.setScreen(new TitleScreen());
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
                    if (Boolean.getBoolean("mmm.breakingIndicatorSmoke")) {
                        phase = 4;
                        new BreakingIndicatorSmoke().install();
                        return;
                    }
                    RuntimeChecks.run(client);
                    client.options.pauseOnLostFocus = false;
                    phase = 3;
                }
                else if (phase == 3 && ++checkedTicks % 15 == 0 && SessionHistory.getLifetimeSummary() != null)
                {
                    int menu = checkedTicks / 15;
                    if (menu <= 7) {
                        client.setScreen(switch (menu) {
                            case 1 -> new com.mmm.ui.MmmSettingsScreen(null);
                            case 2 -> new com.mmm.gui.GuiConfigs(null);
                            case 3 -> new com.mmm.timer.TimerCreditsScreen(null);
                            case 4 -> new com.mmm.ui.WebsiteLinkScreen(null);
                            case 5 -> new net.minecraft.client.gui.screen.advancement.AdvancementsScreen(client.getNetworkHandler().getAdvancementHandler());
                            case 6 -> new com.mmm.ui.MmmChatIgnoreListScreen(null);
                            default -> new com.mmm.scoreboard.ScoreboardScreen(null);
                        });
                        return;
                    }
                    phase = 4;
                    client.setScreen(null);
                    if (Boolean.getBoolean("mmm.miningFeaturesSmoke")) {
                        new MiningFeaturesSmoke().install();
                        return;
                    }
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
