package com.mmm.liteloader.smoke;

import com.mumfrey.liteloader.Tickable;
import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import com.mmm.legacy.*;
import com.mmm.legacy.collection.*;
import com.mmm.legacy.settings.*;
import com.mmm.scoreboard.ScoreboardScreen;
import com.mmm.tracker.MiningStats;
import com.mmm.legacy.social.ChatChannelAccess;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.gui.screen.menu.advancements.AdvancementsScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;

/** Included only with -PruntimeProbe. Never included in the normal development artifact. */
public final class LiteModMMMRuntimeProbe implements Tickable {
    private final List<Supplier<Screen>> screens = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private int ticks, screenIndex = -1;
    private boolean started, ready, failed;
    private long pausedAt;
    @Override public String getName() { return "MMM Runtime Probe"; }
    @Override public String getVersion() { return "test-only"; }
    @Override public void init(File configPath) {}
    @Override public void upgradeSettings(String version, File configPath, File oldConfigPath) {}

    @Override public void onTick(Minecraft client, float partialTick, boolean inGame, boolean clock) {
        if (!Boolean.getBoolean("mmm.runtimeProbe") || !clock || failed) return;
        try {
            if (!started) {
                if (++ticks < 30) return;
                started = true; ticks = 0;
                Configs.Generic.WEBSITE_SYNC_ENABLED.setBooleanValue(false);
                client.options.pauseOnUnfocus = false;
                client.options.viewDistance = 2;
                client.options.guiScale = 2;
                client.startGame("mmm-runtime-probe", "MMM Isolated Runtime Probe",
                        new WorldSettings(1, GameMode.SURVIVAL, false, false, WorldGeneratorType.FLAT).enableCommands());
                System.out.println("MMM_RUNTIME_WORLD_REQUESTED");
                return;
            }
            if (client.world == null || client.player == null) return;
            if (!ready) {
                if (++ticks < 80) return;
                ready = true; ticks = 0;
                client.openScreen(null);
                FeatureToggle.MMM_MINING_TRACKER.setBooleanValue(true);
                FeatureToggle.MMM_HUD.setBooleanValue(true);
                Configs.Generic.BREAKING_INDICATORS.setBooleanValue(true);
                FeatureToggle.MMM_BLOCK_ESP.setBooleanValue(true);
                MiningStats.startNewSession();
                BlockPos pos = new BlockPos(client.player.x + 2, client.player.y + 1, client.player.z);
                client.world.setBlockState(pos, Blocks.STONE.getStateFromMetadata(3));
                long before = MiningStats.getSessionTotal();
                if (!client.interactionManager.finishMiningBlock(pos)) throw new AssertionError("Mining failed");
                if (MiningStats.getSessionTotal() != before + 1) throw new AssertionError("Mining hook did not count exactly once");
                if (!Long.valueOf(1).equals(MiningStats.getCurrentSession().blockBreakdown.get("minecraft:diorite")))
                    throw new AssertionError("Diorite metadata was lost");
                System.out.println("MMM_RUNTIME_MINING_OK");
                verifyWorldTotal(client);
                add("hud", () -> null);
                add("hud-pinned", () -> {
                    com.mmm.tracker.PinnedCollection.pin("minecraft:diorite", true, null);
                    if (com.mmm.tracker.PinnedCollection.view() == null) throw new AssertionError("Pinned collection not available");
                    return null;
                });
                add("inventory", () -> new SurvivalInventoryScreen(client.player));
                add("chat", ProbeChatScreen::new);
                add("command-collections", ProbeChatScreen::new);
                for (Object page : LegacyMenuScreen.class.getDeclaredField("page").getType().getEnumConstants()) {
                    add("settings-" + ((Enum<?>) page).name().toLowerCase(), () -> settings(page));
                }
                add("collections", () -> new CollectionBookScreen(null));
                add("leaderboards", () -> new LeaderboardScreen(null));
                add("scoreboard", () -> new ScoreboardScreen(null));
                add("advancements", () -> new AdvancementsScreen(client.getNetworkHandler().getAdvancements()));
                add("session-summary", () -> new LegacySessionScreen(null, null));
                add("history", () -> new LegacyHistoryScreen(null));
                add("hud-editor", () -> new HudEditorScreen(null));
                add("timer", () -> new TimerScreen(null));
                add("timer-clicks", () -> new LegacyMenuScreen(null));
                add("website-link", () -> new LegacyMenuScreen(null));
                add("projects", () -> new ProjectManagerScreen(null));
                add("settings-small", () -> { client.options.guiScale = 4; return new LegacyMenuScreen(null); });
                add("website-link-small", () -> new LegacyMenuScreen(null));
                add("collections-small", () -> new CollectionBookScreen(null));
                add("summary-small", () -> new LegacySessionScreen(null, null));
                client.openScreen(new GameMenuScreen());
                return;
            }
            ticks++;
            if (screenIndex >= 0 && names.get(screenIndex).equals("timer-clicks") && ticks == 5) {
                verifyTimerClicks(client);
            }
            if (screenIndex >= 0 && names.get(screenIndex).startsWith("website-link") && ticks == 5) {
                verifyWebsiteLink(client);
            }
            if (screenIndex < 0) {
                if (ticks == 5) {
                    if (!MiningStats.isSessionPaused()) throw new AssertionError("Singleplayer ESC did not pause session");
                    pausedAt = MiningStats.getSessionDurationMs();
                }
                if (ticks == 15) {
                    if (MiningStats.getSessionDurationMs() != pausedAt) throw new AssertionError("Paused IGT advanced");
                    client.openScreen(null);
                }
                if (ticks == 20) {
                    if (MiningStats.isSessionPaused()) throw new AssertionError("Session did not resume");
                    System.out.println("MMM_RUNTIME_PAUSE_OK");
                    screenIndex = 0; ticks = 0;
                    client.openScreen(screens.get(0).get());
                }
                return;
            }
            if (ticks == 5 && client.screen instanceof ProbeChatScreen chat) {
                if (((ChatChannelAccess) (Object) chat).mmm$isPublicChannel()) throw new AssertionError("Chat did not reset to vanilla");
                if (names.get(screenIndex).equals("chat")) {
                    chat.typeWithoutClick();
                    chat.selectMmm();
                    if (!((ChatChannelAccess) (Object) chat).mmm$isPublicChannel()) throw new AssertionError("MMM tab selection failed");
                    System.out.println("MMM_RUNTIME_CHAT_INPUT_OK");
                } else chat.openCollectionsCommand();
            }
            if (ticks < 15) return;
            if (names.get(screenIndex).equals("command-collections") && !(client.screen instanceof CollectionBookScreen))
                throw new AssertionError("Client command screen was closed by vanilla chat");
            Screenshot.take(client.gameDir, "probe-" + names.get(screenIndex) + ".png", client.width, client.height, client.getRenderTarget());
            System.out.println("MMM_RUNTIME_SCREEN_OK " + names.get(screenIndex));
            ticks = 0;
            screenIndex++;
            if (screenIndex >= screens.size()) {
                System.out.println("MMM_RUNTIME_PROBE_OK");
                client.shutdown();
                failed = true;
            } else client.openScreen(screens.get(screenIndex).get());
        } catch (Throwable error) {
            failed = true;
            error.printStackTrace();
            System.out.println("MMM_RUNTIME_PROBE_FAILED " + error);
            client.shutdown();
        }
    }

    private void add(String name, Supplier<Screen> screen) { names.add(name); screens.add(screen); }

    private static void verifyWebsiteLink(Minecraft client) throws Exception {
        var menu = client.screen;
        var left = LegacyScreen.class.getDeclaredField("left");
        left.setAccessible(true);
        java.lang.reflect.Method click;
        try { click = Screen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class); }
        catch (NoSuchMethodException ignored) { click = Screen.class.getDeclaredMethod("a", int.class, int.class, int.class); }
        click.setAccessible(true);
        click.invoke(menu, left.getInt(menu) + 120, 15, 0);
        Screen link = client.screen;
        if (!link.getClass().getSimpleName().equals("LegacyLinkScreen")) throw new AssertionError("Header did not open website link");
        var codeField = link.getClass().getDeclaredField("code");
        codeField.setAccessible(true);
        var code = (net.minecraft.client.gui.widget.TextFieldWidget) codeField.get(link);
        if (!code.isFocused()) throw new AssertionError("Link input requires a click");
        code.setText("TEST12");
        link.init();
        code = (net.minecraft.client.gui.widget.TextFieldWidget) codeField.get(link);
        if (!code.getText().equals("TEST12")) throw new AssertionError("Resizing erased the link code");
        code.setText("");
        System.out.println("MMM_RUNTIME_WEBSITE_LINK_OK");
    }

    private static void verifyTimerClicks(Minecraft client) throws Exception {
        clickButton(client.screen, 10);
        clickButton(client.screen, 100);
        if (!(client.screen instanceof TimerScreen)) throw new AssertionError("Timer menu did not open");
        clickButton(client.screen, 1);
        clickButton(client.screen, 2);
        if (!com.mmm.timer.MmmTimerState.isRunning()) throw new AssertionError("Timer did not start");
        clickButton(client.screen, 3);
        if (!com.mmm.timer.MmmTimerState.isPaused()) throw new AssertionError("Timer did not pause");
        clickButton(client.screen, 2);
        clickButton(client.screen, 4);
        clickButton(client.screen, 5);
        clickButton(client.screen, 5);
        clickButton(client.screen, 6);
        if (!(client.screen instanceof TimerResultsScreen)) throw new AssertionError("Timer results did not open");
        clickButton(client.screen, 900);
        if (!(client.screen instanceof TimerScreen)) throw new AssertionError("Timer back navigation failed");
        System.out.println("MMM_RUNTIME_TIMER_CLICKS_OK");
    }

    private static void clickButton(Screen screen, int id) throws Exception {
        net.minecraft.client.gui.widget.ButtonWidget target = null;
        for (var field : Screen.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            for (Object value : (List<?>) field.get(screen)) {
                if (value instanceof net.minecraft.client.gui.widget.ButtonWidget button && button.id == id) target = button;
            }
        }
        if (target == null || !target.active) throw new AssertionError("Missing active button " + id + " in " + screen.getClass());
        java.lang.reflect.Method click;
        try { click = Screen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class); }
        catch (NoSuchMethodException ignored) { click = Screen.class.getDeclaredMethod("a", int.class, int.class, int.class); }
        click.setAccessible(true);
        click.invoke(screen, target.x + target.getWidth() / 2, target.y + 10, 0);
    }

    private static void verifyWorldTotal(Minecraft client) {
        var board = client.world.getScoreboard();
        var objective = board.createObjective("Dihs", net.minecraft.scoreboard.criterion.ScoreboardCriterion.DUMMY);
        board.setDisplayObjective(1, objective);
        var score = board.getScore(client.player.getGameProfile().getName(), objective);
        score.set(183977);
        long now = System.currentTimeMillis();
        MiningStats.applyScoreboardTotalMined(22931L, "Total Digs", true, now);
        com.mmm.sync.SyncScoreboardSelector.selectObjective("");
        com.mmm.sync.DigsSyncManager.onClientTick(now);
        if (MiningStats.getCurrentSourceTotalMined() != 183977L) throw new AssertionError("Analog total remained at local fallback");
        score.set(183978);
        com.mmm.sync.DigsSyncManager.onClientTick(now + 50);
        if (MiningStats.getCurrentSourceTotalMined() != 183978L) throw new AssertionError("World total did not follow live scoreboard");
        score.set(183900);
        com.mmm.sync.DigsSyncManager.onClientTick(now + 100);
        if (MiningStats.getCurrentSourceTotalMined() != 183900L) throw new AssertionError("World total ignored a scoreboard correction");
        if (MiningStats.getSessionTotal() != 1) throw new AssertionError("Scoreboard total inflated the mining session");
        System.out.println("MMM_RUNTIME_WORLD_TOTAL_OK");
    }

    private static final class ProbeChatScreen extends ChatScreen {
        void typeWithoutClick() throws Exception {
            if (!chatField.isFocused()) throw new AssertionError("Chat field has no initial focus");
            keyPressed('a', org.lwjgl.input.Keyboard.KEY_A);
            keyPressed('b', org.lwjgl.input.Keyboard.KEY_B);
            if (!chatField.getText().equals("ab")) throw new AssertionError("Typing required an input click");
            chatField.setText("MMM input test - not sent");
        }
        void selectMmm() throws Exception {
            mouseClicked(chatField.x + minecraft.textRenderer.getWidth("MINECRAFT") + 18, chatField.y - 8, 0);
        }
        void openCollectionsCommand() throws Exception {
            chatField.setText("/mmm collections");
            keyPressed('\r', org.lwjgl.input.Keyboard.KEY_RETURN);
        }
    }
    private static Screen settings(Object page) {
        try {
            LegacyMenuScreen screen = new LegacyMenuScreen(null);
            var field = LegacyMenuScreen.class.getDeclaredField("page");
            field.setAccessible(true); field.set(screen, page);
            return screen;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
}
