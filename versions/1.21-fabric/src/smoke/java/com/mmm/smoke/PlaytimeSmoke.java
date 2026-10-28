package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.config.PlaytimeFormat;
import com.mmm.storage.AsyncPersistence;
import com.mmm.storage.AtomicJsonStorage;
import com.mmm.storage.SessionData;
import com.mmm.storage.SessionHistory;
import com.mmm.tracker.MiningStats;
import com.mmm.tracker.PlaytimeState;
import com.mmm.tracker.PlaytimeTracker;
import com.mmm.ui.MmmSettingsScreen;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;

final class PlaytimeSmoke
{
    private int phase, ticks;
    private long baseline;
    private String pausedModClock;
    private PlaytimeState state;
    private final long started = System.currentTimeMillis();

    void install()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 99) return;
            try {
                if (System.currentTimeMillis() - started > 60_000) throw new AssertionError("Playtime smoke timed out: " + phase);
                if (phase == 0) {
                    client.options.pauseOnLostFocus = false;
                    client.options.getGuiScale().setValue(2);
                    client.onResolutionChanged();
                    var field = PlaytimeTracker.class.getDeclaredField("state");
                    field.setAccessible(true);
                    state = (PlaytimeState) field.get(null);
                    if (state == null) throw new AssertionError("World join did not initialize playtime");
                    var stats = new Object2IntOpenHashMap<Stat<?>>();
                    stats.put(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME), 72_000);
                    client.getNetworkHandler().onStatistics(new StatisticsS2CPacket(stats));
                    if (state.worldMillis() < 3_600_000) throw new AssertionError("Vanilla stat packet not imported");
                    Configs.Generic.PLAYTIME_FORMAT.setOptionListValue(PlaytimeFormat.HOURS_MINUTES_SECONDS);
                    Configs.Generic.WORLD_PLAYTIME_ABOVE_HOTBAR.setBooleanValue(true);
                    var previousSession = new SessionData(System.currentTimeMillis() - 10_000_000L);
                    previousSession.endTimeMs = previousSession.startTimeMs + 7_200_000L;
                    previousSession.wallDurationMs = 9_000_000L;
                    previousSession.totalBlocks = 50_000;
                    SessionHistory.save(previousSession);
                    MiningStats.startNewSession();
                    baseline = state.modMillis();
                    phase = 1; ticks = 0;
                } else if (phase == 1 && ++ticks >= 40) {
                    if (SessionHistory.getLifetimeSummary() == null) return;
                    checkSessionIgt();
                    if (state.modMillis() <= baseline + 500) throw new AssertionError("Live playtime did not advance");
                    var build = com.mmm.hud.MiningHudRenderer.class.getDeclaredMethod("buildHudLines", boolean.class, boolean.class);
                    build.setAccessible(true);
                    String lines = build.invoke(null, false, false).toString();
                    for (String label : new String[] {"World Playtime:", "MMMod Playtime:", "All Worlds Playtime:"})
                        if (!lines.contains(label)) throw new AssertionError("HUD missing " + label);
                    shot(client, "playtime-hud.png");
                    client.setScreen(new MmmSettingsScreen(null));
                    var search = MmmSettingsScreen.class.getDeclaredField("searchField");
                    search.setAccessible(true);
                    ((TextFieldWidget) search.get(client.currentScreen)).setText("Playtime");
                    phase = 2; ticks = 0;
                } else if (phase == 2 && ++ticks >= 5) {
                    if (!client.isPaused()) throw new AssertionError("Singleplayer settings did not pause");
                    baseline = state.modMillis();
                    phase = 3; ticks = 0;
                } else if (phase == 3 && ++ticks >= 30) {
                    if (state.modMillis() != baseline) throw new AssertionError("Paused game added playtime");
                    shot(client, "playtime-settings.png");
                    client.options.getGuiScale().setValue(1);
                    client.onResolutionChanged();
                    phase = 5; ticks = 0;
                } else if (phase == 5 && ++ticks >= 12) {
                    shot(client, "playtime-settings-full.png");
                    client.setScreen(null);
                    phase = 4; ticks = 0;
                } else if (phase == 4 && ++ticks >= 30) {
                    if (state.modMillis() <= baseline + 500) throw new AssertionError("Playtime did not resume");
                    MiningStats.togglePauseSession();
                    pausedModClock = PlaytimeTracker.modClock();
                    baseline = state.worldMillis();
                    phase = 6; ticks = 0;
                } else if (phase == 6 && ++ticks >= 30) {
                    if (!PlaytimeTracker.modClock().equals(pausedModClock)) throw new AssertionError("Paused session changed MMMod playtime");
                    if (state.worldMillis() <= baseline + 500) throw new AssertionError("Pausing the session stopped world playtime");
                    checkSessionIgt();
                    PlaytimeTracker.save();
                    if (!AsyncPersistence.flush(Duration.ofSeconds(5))) throw new AssertionError("Save did not flush");
                    var file = PlaytimeTracker.class.getDeclaredField("file");
                    file.setAccessible(true);
                    var restored = PlaytimeState.restore(AtomicJsonStorage.readObjectWithBackup((Path) file.get(null)).value());
                    if (restored.modMillis() != state.modMillis() || restored.allWorldMillis() != state.allWorldMillis())
                        throw new AssertionError("Saved counters differ");
                    phase = 99;
                    Files.writeString(Path.of("mmm-playtime-smoke-result.txt"), "PASS: vanilla stats, session IGT with pauses excluded, hotbar HUD, settings render, pause/resume, persistence");
                    System.out.println("MMM_PLAYTIME_SMOKE_PASS");
                    client.scheduleStop();
                }
            } catch (Throwable failure) {
                phase = 99;
                failure.printStackTrace();
                try { Files.writeString(Path.of("mmm-playtime-smoke-result.txt"), "FAIL: " + failure); }
                catch (Exception ignored) { }
                client.scheduleStop();
            }
        });
    }

    private static void shot(MinecraftClient client, String name)
    {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), message -> { });
    }

    private static void checkSessionIgt()
    {
        long saved = SessionHistory.getLifetimeSummary().totalActiveMs();
        if (saved < 7_200_000L) throw new AssertionError("Previous session IGT not included");
        String expected = PlaytimeFormat.HOURS_MINUTES_SECONDS.format(saved + MiningStats.getSessionDurationMs());
        if (!PlaytimeTracker.modClock().equals(expected)) throw new AssertionError("MMMod playtime differs from session IGT");
    }
}
