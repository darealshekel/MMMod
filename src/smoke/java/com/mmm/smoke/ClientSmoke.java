package com.mmm.smoke;

import com.mmm.advancement.*;
import com.mmm.config.Configs;
import com.mmm.tracker.*;
import com.mmm.ui.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.*;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public final class ClientSmoke implements ClientModInitializer {
    private int phase, ticks;
    private long started;
    private boolean done;
    @Override public void onInitializeClient() {
        started = System.currentTimeMillis();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            try {
                if (System.currentTimeMillis() - started > 300000) throw new AssertionError("Timeout in phase " + phase);
                if (phase == 0 && client.gui.overlay() == null && ++ticks > 40) {
                    org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                    if (!(client.gui.screen() instanceof TitleScreen)) client.gui.setScreen(new TitleScreen());
                    CreateWorldScreen.openFresh(client, () -> client.gui.setScreen(new TitleScreen()));
                    phase = 1; ticks = 0;
                } else if (phase == 1 && client.gui.screen() instanceof CreateWorldScreen screen) {
                    for (var widget : Screens.getWidgets(screen)) {
                        if (widget instanceof Button button && button.getMessage().equals(Component.translatable("selectWorld.create"))) {
                            phase = 2;
                            button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                            break;
                        }
                    }
                } else if (phase == 2 && client.player != null && client.level != null && client.gui.screen() == null) {
                    client.options.pauseOnLostFocus = false;
                    MiningStats.startNewSession();
                    if (Boolean.getBoolean("mmm.reliabilitySmoke")) {
                        done = true;
                        new ReliabilitySmoke().install();
                        return;
                    }
                    checkMining(client);
                    client.gui.setScreen(new CollectionBookScreen(null));
                    phase = 3; ticks = 0;
                } else if (phase >= 3 && ++ticks > 20) {
                    ticks = 0;
                    switch (phase++) {
                        case 3 -> {
                            bounds(client.gui.screen());
                            shot(client, "collections.png");
                            var field = Screens.getWidgets(client.gui.screen()).stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w).findFirst().orElseThrow();
                            client.gui.screen().setFocused(field);
                            client.gui.screen().charTyped(new CharacterEvent('s'));
                            if (!field.getValue().equals("s")) throw new AssertionError("Collection search did not accept typing");
                            client.gui.setScreen(new InventoryScreen(client.player));
                        }
                        case 4 -> {
                            shot(client, "inventory.png");
                            var button = Screens.getWidgets(client.gui.screen()).stream().filter(w -> w instanceof InventoryCollectionButton).findFirst().orElseThrow();
                            ((InventoryCollectionButton) button).onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                            if (!(client.gui.screen() instanceof CollectionBookScreen)) throw new AssertionError("Inventory book did not open");
                            client.gui.setScreen(new AdvancementsScreen(client.getConnection().getAdvancements()));
                        }
                        case 5 -> {
                            var tabs = ((MmmAdvancementScreenAccess) client.gui.screen()).mmm$tabs();
                            if (!tabs.containsKey(MmmAdvancementTree.rootEntry())) throw new AssertionError("MMM advancements tab missing");
                            ((AdvancementsScreen) client.gui.screen()).onSelectedTabChanged(MmmAdvancementTree.rootEntry());
                            phase = 6;
                        }
                        case 6 -> {
                            shot(client, "advancements.png");
                            client.gui.setScreen(new ChatScreen("", false));
                        }
                        case 7 -> {
                            var chat = client.gui.screen();
                            var field = (EditBox) chat.getFocused();
                            if (field == null) throw new AssertionError("Chat lacks initial input focus");
                            chat.charTyped(new CharacterEvent('h'));
                            if (!field.getValue().equals("h")) throw new AssertionError("Cannot type immediately in chat");
                            int x = field.getX() + client.font.width("MINECRAFT") + 18;
                            chat.mouseClicked(new MouseButtonEvent(x, field.getY() - 10, new MouseButtonInfo(0, 0)), false);
                            chat.charTyped(new CharacterEvent('i'));
                            if (!field.getValue().equals("hi")) throw new AssertionError("Cannot type in MMM channel");
                            shot(client, "mmm-chat.png");
                            client.gui.setScreen(new LeaderboardScreen(null));
                        }
                        case 8 -> {
                            bounds(client.gui.screen());
                            shot(client, "leaderboards.png");
                            client.gui.setScreen(new MmmSettingsScreen(null));
                        }
                        case 9 -> {
                            shot(client, "settings.png");
                            client.gui.setScreen(new com.mmm.hud.SummaryScreen(MiningStats.simulateDevFinishedSession(), null));
                        }
                        case 10 -> {
                            shot(client, "session-summary.png");
                            client.gui.setScreen(null);
                            Configs.Generic.BREAKING_INDICATORS.setBooleanValue(true);
                        }
                        case 11 -> {
                            shot(client, "hud.png");
                            setupRankFixture(client);
                            var book = new CollectionBookScreen(null);
                            client.gui.setScreen(book);
                            var field = Screens.getWidgets(book).stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w).findFirst().orElseThrow();
                            field.setValue("stone");
                            press(book, "Stone");
                        }
                        case 12 -> {
                            bounds(client.gui.screen());
                            shot(client, "collection-rank.png");
                            press(client.gui.screen(), "Pin collection");
                            var pin = PinnedCollection.view();
                            if (pin == null || pin.rank().position() != 28 || !pin.rank().topPercent().equals("2.8%"))
                                throw new AssertionError("Pinned collection ranking missing");
                            client.gui.setScreen(null);
                        }
                        case 13 -> {
                            shot(client, "collection-pin.png");
                            var book = new CollectionBookScreen(null);
                            client.gui.setScreen(book);
                            book.resize(320, 240);
                        }
                        case 14 -> {
                            bounds(client.gui.screen());
                            shot(client, "collections-small.png");
                            press(client.gui.screen(), "Scroll tiers down");
                        }
                        case 15 -> {
                            bounds(client.gui.screen());
                            shot(client, "collections-small-scrolled.png");
                            finish(client, "PASS: mixin audit, mining, collections, inventory book, advancements, chat typing, leaderboards, settings, summary, rank, pinned HUD and responsive tier scrolling");
                        }
                        default -> throw new AssertionError("Unexpected phase " + phase);
                    }
                }
            } catch (Throwable failure) {
                failure.printStackTrace();
                finish(client, "FAIL phase " + phase + ": " + failure);
            }
        });
    }
    private static void checkMining(Minecraft client) {
        client.gameMode.setLocalMode(GameType.SURVIVAL);
        var pos = client.player.blockPosition().offset(3, 0, 0);
        var previous = client.level.getBlockState(pos);
        String world = com.mmm.storage.WorldSessionContext.getCurrentWorldId();
        long before = LiveCollections.localCounts(Map.of(), world).get("mmm:non_insta_deepslate");
        client.level.setBlock(pos, Blocks.DEEPSLATE.defaultBlockState(), 3);
        var attempt = com.mmm.timer.MmmBlockBreakDetector.beginBreak(pos);
            com.mmm.timer.MmmBlockBreakDetector.completeBreak(attempt, true);
        client.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        com.mmm.timer.MmmBlockBreakDetector.acknowledged(client.level, pos, Blocks.AIR.defaultBlockState());
        com.mmm.timer.MmmBlockBreakDetector.acknowledged(client.level, pos, Blocks.AIR.defaultBlockState());
        if (LiveCollections.localCounts(Map.of(), world).get("mmm:non_insta_deepslate") != before + 1)
            throw new AssertionError("Non-instant Deepslate not counted exactly once");
        client.level.setBlock(pos, previous, 3);
        if (CollectionProgress.cap("minecraft:stone") != 25000000L) throw new AssertionError("Old collection caps");
    }
    private static void bounds(Screen screen) {
        for (var w : Screens.getWidgets(screen)) if (w.visible && (w.getX() < 0 || w.getY() < 0 || w.getRight() > screen.width || w.getBottom() > screen.height))
            throw new AssertionError("Widget outside screen: " + w.getMessage().getString());
    }
    private static void press(Screen screen, String name) {
        var button = Screens.getWidgets(screen).stream().filter(w -> w.getMessage().getString().equals(name)).findFirst().orElseThrow();
        ((AbstractButton) button).onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }
    private static void setupRankFixture(Minecraft client) throws Exception {
        var cacheField = CollectionRankClient.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        var cachedType = Class.forName("com.mmm.tracker.CollectionRankClient$Cached");
        var constructor = cachedType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var rank = new CollectionRankClient.Rank(CollectionRankClient.Status.RANKED, 28, 1000);
        var value = constructor.newInstance(java.util.concurrent.CompletableFuture.completedFuture(rank), Long.MAX_VALUE);
        @SuppressWarnings("unchecked") var cache = (Map<String, Object>) cacheField.get(null);
        synchronized (CollectionRankClient.class) {
            cache.put("minecraft:stone:" + client.player.getGameProfile().name().toLowerCase(java.util.Locale.ROOT), value);
        }
    }
    private static void shot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, "smoke-" + name, client.gameRenderer.mainRenderTarget(), 1, message -> {});
    }
    private void finish(Minecraft client, String result) {
        done = true;
        try { Files.writeString(Path.of("mmm-smoke-result.txt"), result); }
        catch (Exception error) { error.printStackTrace(); }
        System.out.println("MMM_SMOKE_26_2 " + result);
        client.stop();
    }
}
