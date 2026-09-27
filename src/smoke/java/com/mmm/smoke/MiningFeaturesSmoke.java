package com.mmm.smoke;

import com.mmm.config.Configs;
import com.mmm.storage.*;
import com.mmm.tracker.*;
import com.mmm.ui.*;
import com.mmm.hud.SummaryScreen;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.ScreenshotRecorder;

final class MiningFeaturesSmoke
{
    private int phase, ticks;
    private long started = System.currentTimeMillis();
    private CollectionBookScreen book;
    private SessionData demo;
    private SummaryScreen efficiency;
    private net.minecraft.world.GameMode originalMode;

    void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (phase == 99) return;
            try {
                if (System.currentTimeMillis() - started > 90_000) throw new AssertionError("Features smoke timeout: " + phase);
                if (phase == 0) {
                    client.options.pauseOnLostFocus = false;
                    MiningStats.startNewSession();
                    client.setScreen(null);
                    checkClassification(client);
                    checkNonInstantDeepslate(client);
                    SidebarChecks.run(client);
                    CollectionChatSmoke.verify(client);
                    ticks = 0; phase = 1;
                } else if (phase == 1 && ++ticks > 12) {
                    shot(client, "leaderboard-sidebar.png");
                    Configs.Generic.MMM_LEADERBOARD_SIDEBAR.setBooleanValue(false);
                    com.mmm.leaderboard.LeaderboardSidebar.tick(client);
                    var session = MiningStats.getCurrentSession();
                    if (session.efficiency.tracked() == 0) throw new AssertionError("Live activity sampler inactive; igt=" + session.getActiveDurationMs()
                            + " active=" + MiningStats.isSessionActive() + " paused=" + MiningStats.isSessionPaused() + " screen=" + client.currentScreen);
                    client.setScreen(new InventoryScreen(client.player)); ticks = 0; phase = 2;
                } else if (phase == 2 && ++ticks > 12) {
                    if (MiningStats.getCurrentSession().efficiency.time(SessionEfficiency.Activity.INVENTORY) == 0)
                        throw new AssertionError("Inventory time not observed");
                    shot(client, "inventory-collections-book.png");
                    checkInventoryBook(client);
                    ticks = 0; phase = 20;
                } else if (phase == 20 && ++ticks > 12) {
                    shot(client, "inventory-collections-recipe-open.png");
                    var button = inventoryButton(client.currentScreen);
                    var access = (com.mmm.mixin.HandledScreenAccessor)client.currentScreen;
                    if (button.getX() != access.mmm$getInventoryX() + access.mmm$getInventoryWidth() - 20)
                        throw new AssertionError("Book did not follow recipe layout");
                    originalMode = client.interactionManager.getCurrentGameMode();
                    client.interactionManager.setGameMode(net.minecraft.world.GameMode.CREATIVE);
                    client.setScreen(new InventoryScreen(client.player));
                    if (!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen))
                        throw new AssertionError("Creative inventory did not open");
                    ticks = 0; phase = 21;
                } else if (phase == 21 && ++ticks > 12) {
                    shot(client, "inventory-collections-creative.png");
                    var creative = client.currentScreen;
                    var button = inventoryButton(creative);
                    if (button.getX() < 0 || button.getX() + button.getWidth() > creative.width)
                        throw new AssertionError("Creative book outside screen");
                    button.onPress();
                    if (!(client.currentScreen instanceof CollectionBookScreen)) throw new AssertionError("Creative book did not open collections");
                    client.currentScreen.close();
                    if (client.currentScreen != creative) throw new AssertionError("Creative inventory return failed");
                    client.interactionManager.setGameMode(originalMode);
                    client.setScreen(null);
                    MiningStats.togglePauseSession();
                    ticks = 0; phase = 3;
                } else if (phase == 3 && ++ticks > 12) {
                    var session = MiningStats.getCurrentSession();
                    if (!MiningStats.isSessionPaused()) throw new AssertionError("Session did not pause");
                    long before = session.efficiency.tracked();
                    for (int i = 0; i < 10; i++) EfficiencyTracker.tick(client);
                    if (before != session.efficiency.tracked()) throw new AssertionError("Paused samples gained time");
                    MiningStats.togglePauseSession();
                    client.options.getGuiScale().setValue(2); client.onResolutionChanged();
                    var world = Configs.getOrCreateWorldStats(WorldSessionContext.getCurrentWorldId(), "Smoke world", "singleplayer", "");
                    world.blockBreakdown = new java.util.LinkedHashMap<>(Map.of("minecraft:stone", 99_800L,
                            "minecraft:dirt", 10_000_000L, "minecraft:netherrack", 200_000L));
                    book = new CollectionBookScreen(null); client.setScreen(book);
                    checkBounds(book); ticks = 0; phase = 4;
                } else if (phase == 4 && ++ticks > 20) {
                    shot(client, "collections-categories.png");
                    var otherWorld = Configs.getOrCreateWorldStats("collection-smoke-other", "Other world", "singleplayer", "");
                    otherWorld.blockBreakdown = new java.util.LinkedHashMap<>(Map.of("minecraft:stone", 1200L));
                    for (int i = 0; i < 20; i++) book.tick();
                    if (!Long.valueOf(101000).equals(((Map<?, ?>)field(book, "counts")).get("minecraft:stone")))
                        throw new AssertionError("All local worlds not included");
                    clickSlot(book, "World scope");
                    if (!Long.valueOf(99800).equals(((Map<?, ?>)field(book, "counts")).get("minecraft:stone")))
                        throw new AssertionError("Current world filter did not switch");
                    var websiteField = book.getClass().getDeclaredField("website"); websiteField.setAccessible(true);
                    websiteField.set(book, new CollectionWebsiteClient.Snapshot(Map.of("minecraft:stone", 800000L, "minecraft:diamond_ore", 1234L), 10));
                    clickSlot(book, "World scope");
                    if (!Long.valueOf(800000).equals(((Map<?, ?>)field(book, "counts")).get("minecraft:stone"))
                            || !Long.valueOf(1234).equals(((Map<?, ?>)field(book, "counts")).get("minecraft:diamond_ore")))
                        throw new AssertionError("Global website worlds missing or double counted");
                    LiveCollections.mined("minecraft:stone");
                    for (int i = 0; i < 20; i++) book.tick();
                    if (!Long.valueOf(800001).equals(((Map<?, ?>)field(book, "counts")).get("minecraft:stone")))
                        throw new AssertionError("Global collections did not include unsynced mining");
                    clickSlot(book, "World scope");
                    websiteField.set(book, null);
                    clickSlot(book, "All collections");
                    var field = book.children().stream().filter(c -> c instanceof TextFieldWidget).map(c -> (TextFieldWidget)c).findFirst().orElseThrow();
                    book.setFocused(field);
                    for (char c : "stone".toCharArray()) book.charTyped(new net.minecraft.client.input.CharInput(c, 0));
                    book.keyPressed(new net.minecraft.client.input.KeyInput(259, 0, 0));
                    if (!field.getText().equals("ston")) throw new AssertionError("Collection search/backspace failed");
                    field.setText("stone");
                    var counts = (Map<?, ?>) field(book, "counts");
                    if (!Long.valueOf(99800).equals(counts.get("minecraft:stone"))) throw new AssertionError("Wrong collection total");
                    worldStone(100_000L);
                    for (int i = 0; i < 20; i++) book.tick();
                    if (!Long.valueOf(100000).equals(((Map<?, ?>) field(book, "counts")).get("minecraft:stone"))) throw new AssertionError("Collection stale");
                    var completedTiers = (long[])field(book, "tierCompletions");
                    for (int i = 0; i < completedTiers.length; i++) {
                        long goal = CollectionProgress.OVERVIEW_MILESTONES.get(i);
                        long expected = goal == 10000 || goal == 100000 ? 3 : goal == 1000000 || goal == 2500000 ? 1 : 0;
                        if (completedTiers[i] != expected) throw new AssertionError("Tier count wrong at " + goal);
                    }
                    if (((long[])field(book, "tierTotals"))[CollectionProgress.OVERVIEW_MILESTONES.indexOf(25000000L)] != 2)
                        throw new AssertionError("25M tier must include only two blocks");
                    book.init(320, 240); checkBounds(book);
                    if ((int)field(book, "x") < 0 || (int)field(book, "y") < 0) throw new AssertionError("Collection panel overflows");
                    client.options.getGuiScale().setValue(1); client.onResolutionChanged();
                    field = book.children().stream().filter(c -> c instanceof TextFieldWidget).map(c -> (TextFieldWidget)c).findFirst().orElseThrow();
                    field.setText("");
                    clickSlot(book, "Next page");
                    if ((int)field(book, "page") != 1) throw new AssertionError("Collection pagination failed");
                    clickSlot(book, "Previous page");
                    clickSlot(book, "Sort collections");
                    ticks = 0; phase = 5;
                } else if (phase == 5 && ++ticks > 20) {
                    shot(client, "collections-grid.png");
                    client.options.getGuiScale().setValue(2); client.onResolutionChanged();
                    ticks = 0; phase = 24;
                } else if (phase == 24 && ++ticks > 20) {
                    shot(client, "collections-grid-small.png");
                    checkBounds(book);
                    ((TextFieldWidget)field(book, "searchField")).setText("gilded");
                    clickSlot(book, "Gilded Blackstone");
                    checkTierPanes(book, 5, 0);
                    book.init(320, 240); checkBounds(book);
                    client.onResolutionChanged();
                    clickSlot(book, "Back");
                    ((TextFieldWidget)field(book, "searchField")).setText("oak log");
                    clickSlot(book, "Oak Log");
                    checkTierPanes(book, 2, 0);
                    clickSlot(book, "Back");
                    ((TextFieldWidget)field(book, "searchField")).setText("stone");
                    clickSlot(book, "Stone");
                    if (field(book, "selected") == null) throw new AssertionError("Collection details did not open");
                    checkTierPanes(book, 5, 2);
                    book.init(320, 240); checkBounds(book);
                    client.onResolutionChanged();
                    ticks = 0; phase = 14;
                } else if (phase == 14 && ++ticks > 20) {
                    shot(client, "collections-tiers-small.png");
                    checkBounds(book);
                    client.options.getGuiScale().setValue(1); client.onResolutionChanged();
                    ticks = 0; phase = 15;
                } else if (phase == 15 && ++ticks > 20) {
                    shot(client, "collections-tiers.png");
                    clickSlot(book, "Pin collection");
                    if (!PinnedCollection.matches("minecraft:stone", false) || PinnedCollection.view().target() != 1000000)
                        throw new AssertionError("Collection pin did not activate with next tier");
                    AsyncPersistence.flush(Duration.ofSeconds(3));
                    var saved = new com.google.gson.JsonObject();
                    var write = Configs.class.getDeclaredMethod("writeCustomState", com.google.gson.JsonObject.class);
                    write.setAccessible(true); write.invoke(null, saved);
                    Configs.pinnedCollectionBlock = "";
                    Configs.pinnedCollectionWorld = "";
                    Configs.pinnedCollectionPlayer = "";
                    var read = Configs.class.getDeclaredMethod("readCustomState", com.google.gson.JsonObject.class);
                    read.setAccessible(true); read.invoke(null, saved);
                    if (!PinnedCollection.matches("minecraft:stone", false)) throw new AssertionError("Pin did not survive config reload");
                    worldStone(10000000);
                    var sample = PinnedCollection.class.getDeclaredField("nextSample"); sample.setAccessible(true); sample.setLong(null, 0);
                    PinnedCollection.tick(client);
                    if (PinnedCollection.view().completed() || PinnedCollection.view().target() != 25000000)
                        throw new AssertionError("Stone pin failed to advance to 25M");
                    worldStone(25000000);
                    for (int i = 0; i < 20; i++) book.tick();
                    checkTierPanes(book, 5, 5);
                    sample.setLong(null, 0); PinnedCollection.tick(client);
                    if (!PinnedCollection.view().completed() || PinnedCollection.view().fraction() != 1)
                        throw new AssertionError("Completed pin failed");
                    worldStone(125000); sample.setLong(null, 0); PinnedCollection.tick(client);
                    if (PinnedCollection.view().count() != 125000 || PinnedCollection.view().target() != 1000000)
                        throw new AssertionError("Pin did not update with local mining count");
                    client.setScreen(null);
                    ticks = 0; phase = 18;
                } else if (phase == 18 && ++ticks > 20) {
                    shot(client, "collections-pinned-hud.png");
                    client.setScreen(book);
                    clickSlot(book, "Unpin collection");
                    if (PinnedCollection.hasPin() || PinnedCollection.view() != null) throw new AssertionError("Unpin did not clear HUD");
                    clickSlot(book, "Back");
                    if (field(book, "selected") != null) throw new AssertionError("Back to block grid failed");
                    clickSlot(book, "Collection progress");
                    if (field(book, "category") != null) throw new AssertionError("Red book did not return to tier overview");
                    var progressBook = book.children().stream().filter(c -> c instanceof CollectionSlotButton)
                            .map(c -> (CollectionSlotButton)c).filter(c -> c.getMessage().getString().equals("Collection progress")).findFirst().orElseThrow();
                    if (progressBook.details.get().size() != CollectionProgress.OVERVIEW_MILESTONES.size() + 1)
                        throw new AssertionError("Book tooltip missing tier counts");
                    book.init(320, 240); checkBounds(book);
                    for (int i = 0; i < 40; i++) book.mouseScrolled((int)field(book, "x") + 20, (int)field(book, "gridY") + 20, 0, -1);
                    var maxScroll = book.getClass().getDeclaredMethod("maxOverviewScroll"); maxScroll.setAccessible(true);
                    if ((int)field(book, "overviewScroll") != (int)maxScroll.invoke(book) || (int)field(book, "overviewScroll") <= 0)
                        throw new AssertionError("Final overview tiers unreachable on small screen");
                    client.onResolutionChanged();
                    demo = new SessionData(1700000000000L); demo.endTimeMs = demo.startTimeMs + 3600000;
                    int[] seconds = {2100, 300, 450, 200, 100, 150};
                    for (int i = 0; i < seconds.length; i++) for (int s = 0; s < seconds[i] * 4; s++)
                        demo.efficiency.add(SessionEfficiency.Activity.values()[i], 250);
                    EfficiencyStore.save(demo); AsyncPersistence.flush(Duration.ofSeconds(3));
                    var restored = EfficiencyStore.load(demo).get();
                    if (restored.tracked() != demo.efficiency.tracked()) throw new AssertionError("Saved efficiency lost");
                    demo.totalBlocks = 55000;
                    demo.blockBreakdown.put("minecraft:stone", 55000L);
                    for (int i = 0; i < 60; i++) demo.miningRateBuckets.add(900);
                    demo.efficiency = new SessionEfficiency();
                    client.getWindow().setWindowedSize(1280, 900);
                    efficiency = new SummaryScreen(demo, null); client.setScreen(efficiency);
                    checkBounds(efficiency); ticks = 0; phase = 6;
                } else if (phase == 6 && ++ticks > 20) {
                    shot(client, "efficiency-large.png");
                    if (demo.efficiency.tracked() != 3300000) throw new AssertionError("Summary did not load saved activity");
                    client.options.getGuiScale().setValue(2); client.onResolutionChanged();
                    efficiency.mouseScrolled(efficiency.width - 30, 130, 0, -5);
                    ticks = 0; phase = 7;
                } else if (phase == 7 && ++ticks > 20) {
                    shot(client, "efficiency-small.png");
                    efficiency.init(320, 240); checkBounds(efficiency);
                    int before = (int)field(efficiency, "contentScroll");
                    efficiency.mouseScrolled(270, 140, 0, -1);
                    if ((int)field(efficiency, "contentScroll") <= before) throw new AssertionError("Summary scrolling failed");
                    efficiency.mouseScrolled(270, 140, 0, -100);
                    if ((int)field(efficiency, "contentScroll") != (int)field(efficiency, "maxContentScroll")) throw new AssertionError("Summary bottom unreachable");
                    var search = (TextFieldWidget)field(efficiency, "searchField");
                    if (!search.visible) throw new AssertionError("Summary search inaccessible");
                    search.setText("stone");
                    var exit = SummaryScreen.worldExit(demo, null, "Smoke world");
                    client.setScreen(exit); exit.init(320, 240); checkBounds(exit);
                    exit.mouseScrolled(270, 140, 0, -100);
                    if (!((TextFieldWidget)field(exit, "searchField")).visible) throw new AssertionError("World exit search inaccessible");
                    efficiency.close();
                    if (client.currentScreen != null) throw new AssertionError("Close failed");
                    phase = 99;
                    Files.writeString(Path.of("mmm-features-smoke-result.txt"), "PASS: inventory book, cursor safety, recipe layout, categories, grid, tiers, pagination, live updates, search, efficiency, responsive bounds, screenshots");
                    System.out.println("MMM_FEATURES_SMOKE_PASS"); client.scheduleStop();
                }
            } catch (Throwable error) {
                phase = 99; error.printStackTrace();
                try { Files.writeString(Path.of("mmm-features-smoke-result.txt"), "FAIL: " + error); } catch (Exception ignored) {}
                client.scheduleStop();
            }
        });
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static InventoryCollectionButton inventoryButton(Screen screen) {
        return screen.children().stream().filter(c -> c instanceof InventoryCollectionButton)
                .map(c -> (InventoryCollectionButton)c).findFirst().orElseThrow();
    }
    private static void checkInventoryBook(MinecraftClient client) {
        Screen inventory = client.currentScreen;
        var button = inventoryButton(inventory);
        checkBounds(inventory);
        client.player.currentScreenHandler.setCursorStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE));
        button.onPress();
        if (client.currentScreen != inventory || client.player.currentScreenHandler.getCursorStack().getCount() != 1)
            throw new AssertionError("Book interfered with a held stack");
        client.player.currentScreenHandler.setCursorStack(net.minecraft.item.ItemStack.EMPTY);
        button.onPress();
        if (!(client.currentScreen instanceof CollectionBookScreen)) throw new AssertionError("Inventory book did not open Collections");
        client.currentScreen.close();
        if (client.currentScreen != inventory) throw new AssertionError("Collections did not return to inventory");
        long buttons = inventory.children().stream().filter(c -> c instanceof InventoryCollectionButton).count();
        if (buttons != 1) throw new AssertionError("Duplicate inventory book after reopening");
        inventory.children().stream().filter(c -> c instanceof TexturedButtonWidget)
                .map(c -> (TexturedButtonWidget)c).findFirst().orElseThrow().onPress(new net.minecraft.client.input.KeyInput(32, 0, 0));
    }
    private static void checkTierPanes(Screen screen, int total, int green) throws Exception {
        var panes = screen.children().stream().filter(c -> c instanceof CollectionSlotButton)
                .map(c -> (CollectionSlotButton)c).filter(c -> c.getMessage().getString().startsWith("Tier ")).toList();
        if (panes.size() != total) throw new AssertionError("Wrong number of glass-pane tiers");
        int completed = 0;
        for (var pane : panes) {
            var icon = (net.minecraft.item.ItemStack)field(pane, "icon");
            if (icon.isOf(net.minecraft.item.Items.LIME_STAINED_GLASS_PANE)) completed++;
            else if (!icon.isOf(net.minecraft.item.Items.RED_STAINED_GLASS_PANE)) throw new AssertionError("Tier is not a glass pane");
        }
        if (completed != green) throw new AssertionError("Tier pane colors did not update");
    }
    private static void clickSlot(Screen screen, String label) {
        screen.children().stream().filter(c -> c instanceof CollectionSlotButton)
                .map(c -> (CollectionSlotButton)c).filter(c -> c.getMessage().getString().equals(label)).findFirst().orElseThrow().onPress();
    }
    private static void checkClassification(MinecraftClient client) throws Exception {
        var method = EfficiencyTracker.class.getDeclaredMethod("classify", MinecraftClient.class, boolean.class, boolean.class);
        method.setAccessible(true);
        if (method.invoke(null, client, true, false) != SessionEfficiency.Activity.MOVING) throw new AssertionError("Moving misclassified");
        if (method.invoke(null, client, false, true) != SessionEfficiency.Activity.MINING) throw new AssertionError("Harvest misclassified");
        var pos = client.player.getBlockPos().add(2, 0, 0);
        var original = client.world.getBlockState(pos);
        var target = client.crosshairTarget;
        var breaking = client.interactionManager.getClass().getDeclaredField("breakingBlock"); breaking.setAccessible(true);
        boolean wasBreaking = breaking.getBoolean(client.interactionManager);
        try {
            client.world.setBlockState(pos, net.minecraft.block.Blocks.STONE.getDefaultState());
            client.crosshairTarget = new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false);
            client.options.attackKey.setPressed(true); breaking.setBoolean(client.interactionManager, true);
            if (method.invoke(null, client, false, false) != SessionEfficiency.Activity.NON_INSTANT) throw new AssertionError("Slow breaking misclassified");
        } finally {
            client.world.setBlockState(pos, original); client.crosshairTarget = target;
            client.options.attackKey.setPressed(false); breaking.setBoolean(client.interactionManager, wasBreaking);
        }
    }
    private static void checkBounds(Screen screen) {
        for (var child : screen.children()) if (child instanceof ClickableWidget widget && widget.visible)
            if (widget.getX() < 0 || widget.getX() + widget.getWidth() > screen.width || widget.getY() < 0 || widget.getY() + widget.getHeight() > screen.height)
                throw new AssertionError("Widget overflows: " + widget.getMessage().getString());
    }
    private static void checkNonInstantDeepslate(MinecraftClient client) {
        var pos = client.player.getBlockPos().add(4, 0, 0);
        var original = client.world.getBlockState(pos);
        var mode = client.interactionManager.getCurrentGameMode();
        var hand = client.player.getMainHandStack();
        String world = WorldSessionContext.getCurrentWorldId();
        long before = LiveCollections.localCounts(Map.of(), world).get("mmm:non_insta_deepslate");
        try {
            client.interactionManager.setGameMode(net.minecraft.world.GameMode.SURVIVAL);
            client.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
            client.world.setBlockState(pos, net.minecraft.block.Blocks.DEEPSLATE.getDefaultState());
            com.mmm.timer.MmmBlockBreakDetector.trackAttack(pos);
            client.world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
            com.mmm.timer.MmmBlockBreakDetector.onClientTick(client);
            com.mmm.timer.MmmBlockBreakDetector.onClientTick(client);
            if (LiveCollections.localCounts(Map.of(), world).get("mmm:non_insta_deepslate") != before + 1)
                throw new AssertionError("Non-insta Deepslate was missed or counted twice");
        } finally {
            client.world.setBlockState(pos, original);
            client.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, hand);
            client.interactionManager.setGameMode(mode);
            com.mmm.timer.MmmBlockBreakDetector.clear();
        }
    }
    private static void worldStone(long count) {
        Configs.WORLD_STATS.stream().filter(world -> world.worldId.equals(WorldSessionContext.getCurrentWorldId()))
                .forEach(world -> world.blockBreakdown.put("minecraft:stone", count));
    }
    private static void shot(MinecraftClient client, String file) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, file, client.getFramebuffer(), 1, message -> {});
    }
}
